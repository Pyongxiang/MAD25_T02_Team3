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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object WeatherRepository {

    private const val BASE_URL = "https://api.openweathermap.org/"
    private const val NOMINATIM_BASE = "https://nominatim.openstreetmap.org/reverse"
    private const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"

    // 🔑 Replace with your real key or BuildConfig value
    private const val OPEN_WEATHER_API_KEY = "e25a0c31ecc92cc51c1c7548568af374"

    private const val VENUE_RADIUS_M = 350.0
    private const val MAX_VENUE_DISTANCE_M = 600.0

    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val weatherApi: WeatherApiService by lazy {
        retrofit.create(WeatherApiService::class.java)
    }

    // ✅ OpenWeather Geocoding API (direct + reverse)
    private val geocodingApi: OpenWeatherGeocodingService by lazy {
        retrofit.create(OpenWeatherGeocodingService::class.java)
    }

    // Current weather (coords)
    suspend fun getCurrentWeather(lat: Double, lon: Double): WeatherResponse? =
        withContext(Dispatchers.IO) {
            try {
                weatherApi.getCurrentWeatherByCoords(
                    lat = lat,
                    lon = lon,
                    apiKey = OPEN_WEATHER_API_KEY
                )
            } catch (_: Exception) {
                null
            }
        }

    // ✅ Search places (forward geocode)
    suspend fun searchPlaces(query: String, limit: Int = 5): List<DirectGeoResult> =
        withContext(Dispatchers.IO) {
            try {
                geocodingApi.directGeocode(
                    query = query,
                    limit = limit,
                    apiKey = OPEN_WEATHER_API_KEY
                )
            } catch (_: Exception) {
                emptyList()
            }
        }

    /**
     * Reverse geocode using Nominatim + Overpass heuristic
     */
    suspend fun getPlaceName(lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {

            val url = buildString {
                append(NOMINATIM_BASE)
                append("?format=jsonv2")
                append("&lat=").append(lat)
                append("&lon=").append(lon)
                append("&zoom=18")
                append("&addressdetails=1")
            }

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "MADAssg2025/1.0 (student project)")
                .build()

            okHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null

                // ✅ OkHttp 3.x: use resp.body() not resp.body
                val bodyStr = resp.body()?.string() ?: return@withContext null

                val json = JSONObject(bodyStr)
                val address = json.optJSONObject("address")
                val displayName = json.optString("display_name", "")

                val name = address?.optString("attraction") ?: ""
                val amenity = address?.optString("amenity") ?: ""
                val tourism = address?.optString("tourism") ?: ""
                val shop = address?.optString("shop") ?: ""
                val building = address?.optString("building") ?: ""

                val poiName = when {
                    name.isNotBlank() -> name
                    amenity.isNotBlank() -> amenity
                    tourism.isNotBlank() -> tourism
                    shop.isNotBlank() -> shop
                    building.isNotBlank() -> building
                    else -> null
                }

                val looksSmallTenant = poiName?.let { isLikelySmallTenant(it, address) } ?: false
                val venueName = getNearestBigVenueName(lat, lon)

                val label = when {
                    !venueName.isNullOrBlank() -> venueName
                    !poiName.isNullOrBlank() && !looksSmallTenant -> poiName
                    else -> buildAreaLabel(address, displayName)
                }?.takeIf { !isBadLabel(it) }

                return@withContext label
            }
        }

    // ----------------- helpers -----------------

    private fun getNearestBigVenueName(lat: Double, lon: Double): String? {
        val q = """
            [out:json][timeout:10];
            (
              node(around:$VENUE_RADIUS_M,$lat,$lon)["name"]["amenity"];
              node(around:$VENUE_RADIUS_M,$lat,$lon)["name"]["tourism"];
              node(around:$VENUE_RADIUS_M,$lat,$lon)["name"]["leisure"];
              node(around:$VENUE_RADIUS_M,$lat,$lon)["name"]["building"];
              way(around:$VENUE_RADIUS_M,$lat,$lon)["name"]["amenity"];
              way(around:$VENUE_RADIUS_M,$lat,$lon)["name"]["tourism"];
              way(around:$VENUE_RADIUS_M,$lat,$lon)["name"]["leisure"];
              way(around:$VENUE_RADIUS_M,$lat,$lon)["name"]["building"];
              relation(around:$VENUE_RADIUS_M,$lat,$lon)["name"]["amenity"];
              relation(around:$VENUE_RADIUS_M,$lat,$lon)["name"]["tourism"];
              relation(around:$VENUE_RADIUS_M,$lat,$lon)["name"]["leisure"];
              relation(around:$VENUE_RADIUS_M,$lat,$lon)["name"]["building"];
            );
            out center;
        """.trimIndent()

        val mediaType = MediaType.parse("application/x-www-form-urlencoded")
        val body = RequestBody.create(mediaType, "data=" + URLEncoder.encode(q, "UTF-8"))

        val req = Request.Builder()
            .url(OVERPASS_URL)
            .post(body)
            .header("User-Agent", "MADAssg2025/1.0 (student project)")
            .build()

        return try {
            okHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null

                // ✅ OkHttp 3.x: use resp.body()
                val bodyStr = resp.body()?.string() ?: return null
                val json = JSONObject(bodyStr)
                val elements = json.optJSONArray("elements") ?: return null

                var bestName: String? = null
                var bestDist = Double.MAX_VALUE

                for (i in 0 until elements.length()) {
                    val el = elements.getJSONObject(i)
                    val tags = el.optJSONObject("tags") ?: continue
                    val n = tags.optString("name", "")
                    if (n.isBlank()) continue

                    val eLat = when {
                        el.has("lat") -> el.optDouble("lat")
                        el.optJSONObject("center") != null -> el.optJSONObject("center")!!.optDouble("lat")
                        else -> Double.NaN
                    }
                    val eLon = when {
                        el.has("lon") -> el.optDouble("lon")
                        el.optJSONObject("center") != null -> el.optJSONObject("center")!!.optDouble("lon")
                        else -> Double.NaN
                    }
                    if (eLat.isNaN() || eLon.isNaN()) continue

                    val d = haversineMeters(lat, lon, eLat, eLon)
                    if (d < bestDist && d <= MAX_VENUE_DISTANCE_M) {
                        bestDist = d
                        bestName = n
                    }
                }

                bestName
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildAreaLabel(address: JSONObject?, displayName: String): String? {
        if (address == null) {
            return displayName.takeIf { it.isNotBlank() }
                ?.split(",")
                ?.firstOrNull()
                ?.trim()
        }

        val neighbourhood = address.optString("neighbourhood", "")
        val suburb = address.optString("suburb", "")
        val cityDistrict = address.optString("city_district", "")
        val town = address.optString("town", "")
        val city = address.optString("city", "")
        val county = address.optString("county", "")

        val candidates = listOf(neighbourhood, suburb, cityDistrict, town, city, county)
            .map { it.trim() }
            .filter { it.isNotBlank() && !isBadLabel(it) }

        return candidates.firstOrNull()
            ?: displayName.takeIf { it.isNotBlank() }?.split(",")?.firstOrNull()?.trim()
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun looksLikeBlock(s: String): Boolean {
        val t = s.lowercase(Locale.getDefault())
        return t.startsWith("block") || t.startsWith("blk")
    }

    private fun isBadLabel(s: String): Boolean =
        s.isBlank() || s.all { it.isDigit() } || looksLikeBlock(s)

    private fun isLikelySmallTenant(poi: String, address: JSONObject?): Boolean {
        val n = poi.lowercase(Locale.getDefault())
        val badWords = listOf(
            "sushi", "tea", "coffee", "bistro", "restaurant", "cafe", "bubble", "koi",
            "starbucks", "mcdonald", "subway", "bakery", "clinic", "pharmacy", "salon",
            "barber", "7-eleven", "minimart", "mart", "store", "shop", "tuition", "centre",
            "center", "laundry"
        )
        if (badWords.any { n.contains(it) }) return true

        val road = address?.optString("road", "") ?: ""
        val house = address?.optString("house_number", "") ?: ""
        if (house.isNotBlank() && road.isNotBlank()) return true

        return false
    }
}
