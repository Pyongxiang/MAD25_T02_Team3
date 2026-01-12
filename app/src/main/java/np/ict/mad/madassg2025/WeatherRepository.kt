package np.ict.mad.madassg2025

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object WeatherRepository {

    private const val BASE_URL = "https://api.openweathermap.org/"
    private const val API_KEY = "e25a0c31ecc92cc51c1c7548568af374"

    // OpenStreetMap / Nominatim (no API key)
    private const val NOMINATIM_REVERSE_URL = "https://nominatim.openstreetmap.org/reverse"
    private const val USER_AGENT = "mad-weather-app/1.0 (student project)"

    // Overpass API (no API key) – used to find “bigger place” names
    private const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"

    private const val PLACE_CACHE_TTL_MS = 10 * 60 * 1000L
    private const val PLACE_CACHE_COORD_THRESHOLD = 0.0003

    private var lastPlaceLat: Double? = null
    private var lastPlaceLon: Double? = null
    private var lastPlaceName: String? = null
    private var lastPlaceTimeMs: Long = 0L

    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val weatherApi: WeatherApiService by lazy {
        retrofit.create(WeatherApiService::class.java)
    }

    suspend fun getCurrentWeather(lat: Double, lon: Double): WeatherResponse {
        return weatherApi.getCurrentWeatherByCoords(lat, lon, API_KEY)
    }
    suspend fun getPlaceName(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // Cache check
        val cachedLat = lastPlaceLat
        val cachedLon = lastPlaceLon
        val cachedName = lastPlaceName
        if (cachedLat != null && cachedLon != null && cachedName != null) {
            val withinTime = (now - lastPlaceTimeMs) <= PLACE_CACHE_TTL_MS
            val movedLittle =
                abs(lat - cachedLat) <= PLACE_CACHE_COORD_THRESHOLD &&
                        abs(lon - cachedLon) <= PLACE_CACHE_COORD_THRESHOLD
            if (withinTime && movedLittle) return@withContext cachedName
        }

        // ---- 1) Nominatim reverse ----
        val nominatimUrl =
            "$NOMINATIM_REVERSE_URL?format=jsonv2&lat=$lat&lon=$lon" +
                    "&addressdetails=1&namedetails=1&extratags=1&layer=poi&zoom=18"

        val nominatimRequest = Request.Builder()
            .url(nominatimUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", Locale.getDefault().language)
            .build()

        try {
            okHttp.newCall(nominatimRequest).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null

                val bodyStr = resp.body()?.string() ?: return@withContext null
                val json = JSONObject(bodyStr)

                val address = json.optJSONObject("address")
                val displayName = json.optString("display_name", null)

                // Candidate POI name from Nominatim
                val poiName = extractBestName(json)?.trim()
                    ?.takeIf { it.isNotBlank() && !isDigitsOnly(it) }

                // Try to detect if Nominatim POI is a “small tenant” (restaurant/shop etc.)
                val looksSmallTenant = looksLikeSmallTenant(json, address)

                // ---- 2) Overpass venue-first fallback ----
                // Even if Nominatim gave us a name, if it looks like a small tenant,
                // we prefer a “bigger venue” name if available.
                val venueName = getNearestBigVenueName(lat, lon)

                val finalLabel = when {
                    // If we found a big venue, use it
                    !venueName.isNullOrBlank() -> venueName

                    // Otherwise accept Nominatim POI name only if it doesn't look like a tenant
                    !poiName.isNullOrBlank() && !looksSmallTenant -> poiName

                    // Otherwise fall back to area label
                    else -> buildAreaLabel(address, displayName)
                }?.trim()?.takeIf { it.isNotBlank() && !isDigitsOnly(it) }
                    ?: return@withContext null

                cache(lat, lon, finalLabel)
                return@withContext finalLabel
            }
        } catch (_: Exception) {
            return@withContext null
        }
    }

    private fun cache(lat: Double, lon: Double, name: String) {
        lastPlaceLat = lat
        lastPlaceLon = lon
        lastPlaceName = name
        lastPlaceTimeMs = System.currentTimeMillis()
    }

    private fun extractBestName(json: JSONObject): String? {
        val direct = json.optString("name", "").trim()
        if (direct.isNotEmpty()) return direct

        val namedetails = json.optJSONObject("namedetails")
        val nd = namedetails?.optString("name", "")?.trim()
        if (!nd.isNullOrEmpty()) return nd

        val extratags = json.optJSONObject("extratags")
        val en = extratags?.optString("name:en", "")?.trim()
        if (!en.isNullOrEmpty()) return en

        val any = extratags?.optString("name", "")?.trim()
        return any?.takeIf { it.isNotEmpty() }
    }

    /**
     * Detect “small tenant” types so we avoid showing Sushi Tei / random shop names
     * when user likely wants the bigger venue (mall/campus/etc.).
     *
     * This is heuristic: depends on tags returned by Nominatim.
     */
    private fun looksLikeSmallTenant(json: JSONObject, address: JSONObject?): Boolean {
        val extratags = json.optJSONObject("extratags")

        // Some Nominatim responses include "category" and "type"
        val category = json.optString("category", "").lowercase(Locale.getDefault())
        val type = json.optString("type", "").lowercase(Locale.getDefault())

        // extratags may contain amenity/shop values
        val amenity = extratags?.optString("amenity", "")?.lowercase(Locale.getDefault()) ?: ""
        val shop = extratags?.optString("shop", "")?.lowercase(Locale.getDefault()) ?: ""
        val tourism = extratags?.optString("tourism", "")?.lowercase(Locale.getDefault()) ?: ""

        // address may include "shop" / "amenity" sometimes (rare)
        val addrAmenity = address?.optString("amenity", "")?.lowercase(Locale.getDefault()) ?: ""
        val addrShop = address?.optString("shop", "")?.lowercase(Locale.getDefault()) ?: ""

        val smallAmenity = setOf(
            "restaurant", "cafe", "fast_food", "bar", "pub", "food_court", "ice_cream",
            "bank", "atm", "pharmacy", "clinic"
        )
        val smallShop = setOf(
            "supermarket", "convenience", "bakery", "clothes", "shoes", "beauty", "hairdresser",
            "mobile_phone", "electronics", "jewelry", "gift", "department_store"
        )

        val isSmallAmenity =
            smallAmenity.contains(amenity) || smallAmenity.contains(addrAmenity) ||
                    (category == "amenity" && smallAmenity.contains(type))

        val isSmallShop =
            shop.isNotBlank() || addrShop.isNotBlank() ||
                    (category == "shop") || smallShop.contains(shop) || smallShop.contains(addrShop)

        // tourism=attraction is usually NOT small; keep it as “not small”
        val isTourismAttraction = tourism == "attraction"

        return (isSmallAmenity || isSmallShop) && !isTourismAttraction
    }

    private fun buildAreaLabel(address: JSONObject?, displayName: String?): String? {
        val fields = listOf(
            address?.optString("neighbourhood"),
            address?.optString("suburb"),
            address?.optString("city_district"),
            address?.optString("town"),
            address?.optString("city"),
            address?.optString("road")
        )
        for (f in fields) {
            if (!f.isNullOrBlank() && !isDigitsOnly(f)) return f.trim()
        }
        val dn = displayName?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return dn.split(",").firstOrNull()?.trim()
    }

    /**
     * Overpass: find a “big venue” name near the point.
     * We intentionally prefer malls/buildings/campuses/stations/parks over restaurants/shops.
     */
    private fun getNearestBigVenueName(lat: Double, lon: Double): String? {
        val radius = 900

        // Tier 1: venues we usually want to show
        val query = """
            [out:json][timeout:12];
            (
              // Malls
              node(around:$radius,$lat,$lon)["shop"="mall"]["name"];
              way(around:$radius,$lat,$lon)["shop"="mall"]["name"];
              relation(around:$radius,$lat,$lon)["shop"="mall"]["name"];

              // Named buildings (often malls/campuses)
              way(around:$radius,$lat,$lon)["building"]["name"];
              relation(around:$radius,$lat,$lon)["building"]["name"];

              // Campuses / education institutions
              node(around:$radius,$lat,$lon)["amenity"~"university|college|school"]["name"];
              way(around:$radius,$lat,$lon)["amenity"~"university|college|school"]["name"];
              relation(around:$radius,$lat,$lon)["amenity"~"university|college|school"]["name"];

              // Stations / public transport hubs
              node(around:$radius,$lat,$lon)["railway"="station"]["name"];
              way(around:$radius,$lat,$lon)["railway"="station"]["name"];
              node(around:$radius,$lat,$lon)["public_transport"="station"]["name"];
              way(around:$radius,$lat,$lon)["public_transport"="station"]["name"];

              // Parks / attractions
              node(around:$radius,$lat,$lon)["leisure"="park"]["name"];
              way(around:$radius,$lat,$lon)["leisure"="park"]["name"];
              node(around:$radius,$lat,$lon)["tourism"="attraction"]["name"];
              way(around:$radius,$lat,$lon)["tourism"="attraction"]["name"];
            );
            out center 60;
        """.trimIndent()

        val best = runOverpassNearestName(lat, lon, query) ?: return null

        // Extra guard: if Overpass somehow returns a small tenant anyway, ignore it.
        if (looksLikeTenantName(best)) return null

        return best
    }

    /**
     * Execute Overpass query and return nearest element name to (lat,lon).
     */
    private fun runOverpassNearestName(originLat: Double, originLon: Double, overpassQuery: String): String? {
        val encoded = URLEncoder.encode(overpassQuery, "UTF-8")
        val body = RequestBody.create(
            MediaType.parse("application/x-www-form-urlencoded"),
            "data=$encoded"
        )

        val request = Request.Builder()
            .url(OVERPASS_URL)
            .post(body)
            .header("User-Agent", USER_AGENT)
            .build()

        return try {
            okHttp.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bodyStr = resp.body()?.string() ?: return null
                val json = JSONObject(bodyStr)
                val elements = json.optJSONArray("elements") ?: return null

                var bestName: String? = null
                var bestDist = Double.MAX_VALUE

                for (i in 0 until elements.length()) {
                    val el = elements.optJSONObject(i) ?: continue
                    val tags = el.optJSONObject("tags") ?: continue
                    val name = tags.optString("name", "").trim()
                    if (name.isBlank() || isDigitsOnly(name)) continue

                    val (elLat, elLon) = when {
                        el.has("lat") && el.has("lon") -> el.getDouble("lat") to el.getDouble("lon")
                        el.has("center") -> {
                            val c = el.optJSONObject("center") ?: continue
                            c.optDouble("lat", Double.NaN) to c.optDouble("lon", Double.NaN)
                        }
                        else -> continue
                    }

                    if (elLat.isNaN() || elLon.isNaN()) continue

                    val d = haversineMeters(originLat, originLon, elLat, elLon)
                    if (d < bestDist) {
                        bestDist = d
                        bestName = name
                    }
                }

                bestName
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Small safeguard: some tenant names look like brands/restaurants.
     * This is heuristic; we only use it as a last guard.
     */
    private fun looksLikeTenantName(name: String): Boolean {
        val n = name.lowercase(Locale.getDefault())
        val tenantWords = listOf(
            "sushi", "cafe", "coffee", "tea", "bakery", "restaurant", "bistro", "bar", "grill",
            "clinic", "pharmacy", "hair", "salon"
        )
        return tenantWords.any { n.contains(it) }
    }

    private fun isDigitsOnly(s: String): Boolean = s.trim().isNotEmpty() && s.trim().all { it.isDigit() }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
            sin(dLat / 2).pow(2.0) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
