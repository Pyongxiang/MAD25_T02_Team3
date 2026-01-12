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
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val weatherApi: WeatherApiService by lazy {
        retrofit.create(WeatherApiService::class.java)
    }

    // ✅ Matches WeatherApiService.kt exactly
    suspend fun getCurrentWeather(lat: Double, lon: Double): WeatherResponse? =
        withContext(Dispatchers.IO) {
            try {
                weatherApi.getCurrentWeatherByCoords(
                    lat = lat,
                    lon = lon,
                    apiKey = OPEN_WEATHER_API_KEY
                )
            } catch (e: Exception) {
                null
            }
        }

    // ✅ FIXED: explicit return to avoid "Missing return statement"
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

            val resp = try {
                okHttp.newCall(req).execute()
            } catch (e: Exception) {
                null
            } ?: return@withContext null

            resp.use { r ->
                if (!r.isSuccessful) return@withContext null

                val bodyStr = r.body()?.string()?.takeIf { it.isNotBlank() }
                    ?: return@withContext null

                val json = JSONObject(bodyStr)

                val displayName = json.optString("display_name", "")
                val address = json.optJSONObject("address")

                val name = json.optString("name", "").trim()
                val amenity = address?.optString("amenity")?.trim().orEmpty()
                val tourism = address?.optString("tourism")?.trim().orEmpty()
                val shop = address?.optString("shop")?.trim().orEmpty()
                val building = address?.optString("building")?.trim().orEmpty()

                val poiName = when {
                    name.isNotBlank() -> name
                    amenity.isNotBlank() -> amenity
                    tourism.isNotBlank() -> tourism
                    shop.isNotBlank() -> shop
                    building.isNotBlank() -> building
                    else -> null
                }

                val looksSmallTenant = poiName?.let {
                    isLikelySmallTenant(it, address)
                } ?: false

                val venueName = getNearestBigVenueName(lat, lon)

                val label = when {
                    !venueName.isNullOrBlank() -> venueName
                    !poiName.isNullOrBlank() && !looksSmallTenant -> poiName
                    else -> buildAreaLabel(address, displayName)
                }?.takeIf { !isBadLabel(it) }

                // ✅ REQUIRED explicit return
                return@withContext label
            }
        }

    private fun isLikelySmallTenant(poi: String, address: JSONObject?): Boolean {
        val n = poi.lowercase(Locale.getDefault())

        val badWords = listOf(
            "restaurant", "cafe", "bakery", "clinic", "salon",
            "barber", "nail", "laundry", "7-eleven", "minimart"
        )

        if (badWords.any { n.contains(it) }) return true

        val amenity = address?.optString("amenity").orEmpty()
        val shop = address?.optString("shop").orEmpty()

        return amenity.isNotBlank() || shop.isNotBlank()
    }

    private fun buildAreaLabel(address: JSONObject?, displayName: String?): String? {
        val fields = listOf(
            address?.optString("neighbourhood"),
            address?.optString("suburb"),
            address?.optString("city_district"),
            address?.optString("town"),
            address?.optString("city")
        )

        for (f in fields) {
            if (!f.isNullOrBlank() && !isBadLabel(f)) return f
        }

        displayName?.split(",")?.forEach {
            val s = it.trim()
            if (!isBadLabel(s)) return s
        }

        return null
    }

    private fun getNearestBigVenueName(lat: Double, lon: Double): String? {
        val query = """
            [out:json][timeout:25];
            (
              node(around:${VENUE_RADIUS_M.toInt()},$lat,$lon)["name"]["shop"="mall"];
              way(around:${VENUE_RADIUS_M.toInt()},$lat,$lon)["name"]["shop"="mall"];
              relation(around:${VENUE_RADIUS_M.toInt()},$lat,$lon)["name"]["shop"="mall"];
            );
            out center;
        """.trimIndent()

        val body = "data=" + URLEncoder.encode(query, "UTF-8")
        val mediaType = MediaType.parse("application/x-www-form-urlencoded")
        val reqBody = RequestBody.create(mediaType, body)

        val req = Request.Builder()
            .url(OVERPASS_URL)
            .post(reqBody)
            .header("User-Agent", "MADAssg2025/1.0")
            .build()

        val resp = try {
            okHttp.newCall(req).execute()
        } catch (e: Exception) {
            null
        } ?: return null

        resp.use { r ->
            if (!r.isSuccessful) return null

            val json = JSONObject(r.body()?.string() ?: return null)
            val els = json.optJSONArray("elements") ?: return null

            var best: String? = null
            var bestDist = Double.MAX_VALUE

            for (i in 0 until els.length()) {
                val el = els.getJSONObject(i)
                val tags = el.optJSONObject("tags") ?: continue
                val name = tags.optString("name", "")
                val center = el.optJSONObject("center") ?: continue

                val d = haversine(
                    lat, lon,
                    center.getDouble("lat"),
                    center.getDouble("lon")
                )

                if (d < bestDist) {
                    bestDist = d
                    best = name
                }
            }

            return if (bestDist <= MAX_VENUE_DISTANCE_M) best else null
        }
    }

    private fun haversine(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a =
            sin(dLat / 2).pow(2.0) +
                    cos(Math.toRadians(lat1)) *
                    cos(Math.toRadians(lat2)) *
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
}
