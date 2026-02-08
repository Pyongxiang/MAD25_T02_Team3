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
    private const val NOMINATIM_SEARCH = "https://nominatim.openstreetmap.org/search"

    // Singapore bounding box (roughly covers SG)
    private const val SG_LEFT = 103.60
    private const val SG_TOP = 1.48
    private const val SG_RIGHT = 104.10
    private const val SG_BOTTOM = 1.16

    private const val SG_CENTER_LAT = 1.3521
    private const val SG_CENTER_LON = 103.8198
    private const val SG_RADIUS_M = 25000 // 25km covers Singapore island

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

    // OpenWeather Geocoding API (direct + reverse)
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


    suspend fun searchPlaces(query: String, limit: Int = 5): List<DirectGeoResult> =
        withContext(Dispatchers.IO) {

            val q = query.trim()
            if (q.isBlank()) return@withContext emptyList()

            fun distinctByCoords(items: List<DirectGeoResult>): List<DirectGeoResult> =
                items.distinctBy { "${it.lat ?: 0.0},${it.lon ?: 0.0},${it.name ?: ""}" }

            // 1) OpenWeather direct geocoding (bias to Singapore)
            val owResults: List<DirectGeoResult> = try {
                val biasedQ =
                    if (q.contains("singapore", ignoreCase = true)) q else "$q, Singapore"
                geocodingApi.directGeocode(
                    query = biasedQ,
                    limit = limit,
                    apiKey = OPEN_WEATHER_API_KEY
                )
            } catch (_: Exception) {
                emptyList()
            }

            val owSG = owResults.filter { it.country?.equals("SG", ignoreCase = true) == true }
            val owNonSG = owResults.filterNot { it.country?.equals("SG", ignoreCase = true) == true }
            val owOrdered = distinctByCoords(owSG + owNonSG).take(limit)

            if (owOrdered.size >= limit) return@withContext owOrdered

            // 2) Nominatim bounded search (Singapore)
            val nominatim = try {
                nominatimSearchSingapore(q, limit)
            } catch (_: Exception) {
                emptyList()
            }

            // 3) Overpass POI search (Singapore)
            val overpass = try {
                overpassSearchSingaporePOI(q, limit)
            } catch (_: Exception) {
                emptyList()
            }

            distinctByCoords(owOrdered + nominatim + overpass).take(limit)
        }

    private fun nominatimSearchSingapore(query: String, limit: Int): List<DirectGeoResult> {
        val url = buildString {
            append(NOMINATIM_SEARCH)
            append("?format=jsonv2")
            append("&q=").append(URLEncoder.encode(query, "UTF-8"))
            append("&addressdetails=1")
            append("&limit=").append(limit)
            // Bias to Singapore only
            append("&countrycodes=sg")
            // Keep results inside SG bounding box
            append("&bounded=1")
            append("&viewbox=")
            append(SG_LEFT).append(",").append(SG_TOP).append(",")
            append(SG_RIGHT).append(",").append(SG_BOTTOM)
            append("&accept-language=en")
        }

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "MADAssg2025/1.0 (student project)")
            .build()

        okHttp.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()

            val bodyStr = resp.body()?.string().orEmpty()
            val arr = org.json.JSONArray(bodyStr)

            val out = mutableListOf<DirectGeoResult>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val displayName = obj.optString("display_name").trim()
                val lat = obj.optString("lat").toDoubleOrNull()
                val lon = obj.optString("lon").toDoubleOrNull()

                val address = obj.optJSONObject("address")
                val state = address?.optString("state")?.takeIf { it.isNotBlank() }
                val countryCode = address?.optString("country_code")?.uppercase(Locale.ROOT)

                if (displayName.isNotBlank() && lat != null && lon != null) {
                    out += DirectGeoResult(
                        name = displayName,
                        local_names = null,
                        lat = lat,
                        lon = lon,
                        country = countryCode ?: "SG",
                        state = state
                    )
                }
            }
            return out
        }
    }

    private fun overpassSearchSingaporePOI(query: String, limit: Int): List<DirectGeoResult> {
        val safe = query.replace("\"", "\\\"")
        val overpassQuery = """
            [out:json][timeout:25];
            (
              node(around:$SG_RADIUS_M,$SG_CENTER_LAT,$SG_CENTER_LON)["name"~"$safe",i]["amenity"="university"];
              node(around:$SG_RADIUS_M,$SG_CENTER_LAT,$SG_CENTER_LON)["name"~"$safe",i]["amenity"="college"];
              node(around:$SG_RADIUS_M,$SG_CENTER_LAT,$SG_CENTER_LON)["name"~"$safe",i]["amenity"="school"];
              way(around:$SG_RADIUS_M,$SG_CENTER_LAT,$SG_CENTER_LON)["name"~"$safe",i]["amenity"="university"];
              way(around:$SG_RADIUS_M,$SG_CENTER_LAT,$SG_CENTER_LON)["name"~"$safe",i]["amenity"="college"];
              way(around:$SG_RADIUS_M,$SG_CENTER_LAT,$SG_CENTER_LON)["name"~"$safe",i]["amenity"="school"];
            );
            out center;
        """.trimIndent()

        val body = RequestBody.create(
            MediaType.parse("application/x-www-form-urlencoded"),
            "data=" + URLEncoder.encode(overpassQuery, "UTF-8")
        )

        val req = Request.Builder()
            .url(OVERPASS_URL)
            .post(body)
            .header("User-Agent", "MADAssg2025/1.0 (student project)")
            .build()

        okHttp.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()

            val json = JSONObject(resp.body()?.string().orEmpty())
            val elements = json.optJSONArray("elements") ?: return emptyList()

            val out = mutableListOf<DirectGeoResult>()
            for (i in 0 until elements.length()) {
                val el = elements.getJSONObject(i)
                val tags = el.optJSONObject("tags")
                val name = tags?.optString("name")?.trim().orEmpty()
                if (name.isBlank()) continue

                val lat = if (el.has("lat")) el.optDouble("lat", Double.NaN) else Double.NaN
                val lon = if (el.has("lon")) el.optDouble("lon", Double.NaN) else Double.NaN
                val center = el.optJSONObject("center")

                val finalLat = if (!lat.isNaN()) lat else center?.optDouble("lat", Double.NaN) ?: Double.NaN
                val finalLon = if (!lon.isNaN()) lon else center?.optDouble("lon", Double.NaN) ?: Double.NaN
                if (finalLat.isNaN() || finalLon.isNaN()) continue

                out += DirectGeoResult(
                    name = name,
                    local_names = null,
                    lat = finalLat,
                    lon = finalLon,
                    country = "SG",
                    state = null
                )
            }
            return out.take(limit)
        }
    }


     //Reverse geocode using Nominatim + Overpass heuristic

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

                val obj = JSONObject(resp.body()?.string().orEmpty())
                val address = obj.optJSONObject("address")
                val displayName = obj.optString("display_name").orEmpty()

                val poiName = listOf(
                    address?.optString("attraction"),
                    address?.optString("tourism"),
                    address?.optString("leisure"),
                    address?.optString("amenity"),
                    address?.optString("shop"),
                    address?.optString("building"),
                    address?.optString("name")
                ).firstOrNull { !it.isNullOrBlank() }?.trim()

                val venueName = getNearestBigVenueName(lat, lon)?.trim()

                val label = when {
                    !venueName.isNullOrBlank() && !isLikelySmallTenant(venueName, address) -> venueName
                    !poiName.isNullOrBlank() && !isLikelySmallTenant(poiName, address) -> poiName
                    else -> buildAreaLabel(address, displayName)
                }?.trim()

                if (label.isNullOrBlank()) return@withContext null
                if (isBadLabel(label)) return@withContext buildAreaLabel(address, displayName)

                return@withContext label
            }
        }

    private fun getNearestBigVenueName(lat: Double, lon: Double): String? {
        val q = """
            [out:json][timeout:25];
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

        val body = RequestBody.create(
            MediaType.parse("application/x-www-form-urlencoded"),
            "data=" + URLEncoder.encode(q, "UTF-8")
        )

        val req = Request.Builder()
            .url(OVERPASS_URL)
            .post(body)
            .header("User-Agent", "MADAssg2025/1.0 (student project)")
            .build()

        okHttp.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null

            val json = JSONObject(resp.body()?.string().orEmpty())
            val elements = json.optJSONArray("elements") ?: return null

            var bestName: String? = null
            var bestDist = Double.MAX_VALUE

            for (i in 0 until elements.length()) {
                val el = elements.getJSONObject(i)
                val tags = el.optJSONObject("tags") ?: continue
                val name = tags.optString("name").orEmpty().trim()
                if (name.isBlank()) continue

                if (isLikelySmallVenue(tags, name)) continue

                val elLat: Double? = when {
                    el.has("lat") -> el.optDouble("lat", Double.NaN).takeIf { !it.isNaN() }
                    el.optJSONObject("center") != null ->
                        el.optJSONObject("center")!!.optDouble("lat", Double.NaN).takeIf { !it.isNaN() }
                    else -> null
                }

                val elLon: Double? = when {
                    el.has("lon") -> el.optDouble("lon", Double.NaN).takeIf { !it.isNaN() }
                    el.optJSONObject("center") != null ->
                        el.optJSONObject("center")!!.optDouble("lon", Double.NaN).takeIf { !it.isNaN() }
                    else -> null
                }

                if (elLat == null || elLon == null) continue

                val d = haversineMeters(lat, lon, elLat, elLon)
                if (d < bestDist && d <= MAX_VENUE_DISTANCE_M) {
                    bestDist = d
                    bestName = name
                }
            }

            return bestName
        }
    }

    private fun isLikelySmallVenue(tags: JSONObject, name: String): Boolean {
        val amenity = tags.optString("amenity").lowercase(Locale.ROOT)
        val shop = tags.optString("shop").lowercase(Locale.ROOT)
        val tourism = tags.optString("tourism").lowercase(Locale.ROOT)

        if (shop.isNotBlank()) return true

        val smallAmenities = setOf(
            "restaurant", "cafe", "fast_food", "bar", "pub", "bakery",
            "clinic", "doctors", "dentist", "pharmacy",
            "convenience", "supermarket", "hairdresser", "beauty"
        )
        if (amenity in smallAmenities) return true

        val smallTourism = setOf("hotel", "hostel", "guest_house")
        if (tourism in smallTourism) return true

        return isLikelySmallTenant(name, tags.optJSONObject("address"))
    }

    private fun buildAreaLabel(address: JSONObject?, displayName: String): String? {
        val candidates = listOf(
            address?.optString("neighbourhood"),
            address?.optString("suburb"),
            address?.optString("quarter"),
            address?.optString("city_district"),
            address?.optString("city"),
            address?.optString("town"),
            address?.optString("village"),
            address?.optString("state")
        ).filterNotNull().map { it.trim() }.filter { it.isNotBlank() }

        return candidates.firstOrNull()
            ?: displayName.split(",").firstOrNull()?.trim()
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun looksLikeBlock(s: String): Boolean {
        val t = s.lowercase(Locale.ROOT)
        return t.startsWith("blk ") || t.startsWith("block ") || Regex("""\bblk\b""").containsMatchIn(t)
    }

    private fun isBadLabel(s: String): Boolean =
        s.isBlank() || s.all { it.isDigit() } || looksLikeBlock(s)

    private fun isLikelySmallTenant(poi: String, address: JSONObject?): Boolean {
        val p = poi.lowercase(Locale.ROOT)
        val badWords = listOf(
            "coffee", "cafe", "restaurant", "sushi", "noodle", "bakery", "bar", "pub",
            "clinic", "pharmacy", "dentist", "salon", "hair", "beauty",
            "mart", "mini mart", "convenience", "supermarket", "shop"
        )
        if (badWords.any { p.contains(it) }) return true

        val houseNum = address?.optString("house_number")?.trim().orEmpty()
        val road = address?.optString("road")?.trim().orEmpty()
        if (houseNum.isNotBlank() && road.isNotBlank()) return true

        return false
    }
}
