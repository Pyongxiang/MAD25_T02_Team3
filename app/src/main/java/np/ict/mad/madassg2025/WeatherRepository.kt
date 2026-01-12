package np.ict.mad.madassg2025

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object WeatherRepository {

    private const val BASE_URL = "https://api.openweathermap.org/"
    private const val API_KEY = "e25a0c31ecc92cc51c1c7548568af374"

    // OpenStreetMap / Nominatim reverse-geocoding (no API key needed)
    private const val NOMINATIM_REVERSE_URL = "https://nominatim.openstreetmap.org/reverse"
    private const val NOMINATIM_USER_AGENT = "mad-weather-app/1.0 (student project)"

    // Cache to avoid repeated reverse geocode calls
    private const val PLACE_CACHE_TTL_MS = 10 * 60 * 1000L // 10 minutes
    private const val PLACE_CACHE_COORD_THRESHOLD = 0.0003 // ~tens of meters (rough)

    private var lastPlaceLat: Double? = null
    private var lastPlaceLon: Double? = null
    private var lastPlaceName: String? = null
    private var lastPlaceTimeMs: Long = 0L

    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
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
        return weatherApi.getCurrentWeatherByCoords(lat = lat, lon = lon, apiKey = API_KEY)
    }
    suspend fun getPlaceName(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        // 1) Cache check (time + coordinate change)
        val now = System.currentTimeMillis()
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

        val url =
            "$NOMINATIM_REVERSE_URL" +
                    "?format=jsonv2" +
                    "&lat=$lat" +
                    "&lon=$lon" +
                    "&addressdetails=1" +
                    "&namedetails=1" +
                    "&extratags=1" +
                    "&layer=poi" +
                    "&zoom=18"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", NOMINATIM_USER_AGENT)
            .header("Accept-Language", Locale.getDefault().language)
            .build()

        try {
            val resp = okHttp.newCall(request).execute()
            resp.use { r ->
                if (!r.isSuccessful) return@withContext null

                val bodyStr = r.body()?.string() ?: return@withContext null
                val json = JSONObject(bodyStr)

                val name = extractBestName(json)
                val address = json.optJSONObject("address")
                val displayName = json.optString("display_name", null)

                val label = when {
                    // Prefer explicit POI name if it exists and isn't just digits
                    !name.isNullOrBlank() && !isDigitsOnly(name) -> name

                    // Otherwise use address-based best label (avoid digits-only)
                    else -> buildAreaLabel(address, displayName)
                } ?: return@withContext null

                // Final guard: don't cache/return digit-only labels (e.g. "535")
                if (isDigitsOnly(label)) return@withContext null

                // 3) Update cache
                lastPlaceLat = lat
                lastPlaceLon = lon
                lastPlaceName = label
                lastPlaceTimeMs = now

                return@withContext label
            }
        } catch (_: Exception) {
            return@withContext null
        }
    }
    private fun extractBestName(json: JSONObject): String? {
        val direct = json.optString("name", null)?.trim()
        if (!direct.isNullOrBlank()) return direct

        val namedetails = json.optJSONObject("namedetails")
        val nd = namedetails?.optString("name", null)?.trim()
        if (!nd.isNullOrBlank()) return nd

        val extratags = json.optJSONObject("extratags")
        val en = extratags?.optString("name:en", null)?.trim()
        if (!en.isNullOrBlank()) return en

        val any = extratags?.optString("name", null)?.trim()
        if (!any.isNullOrBlank()) return any

        return null
    }

    private fun buildAreaLabel(address: JSONObject?, displayName: String?): String? {
        val candidates = listOf(
            address?.optString("neighbourhood"),
            address?.optString("suburb"),
            address?.optString("city_district"),
            address?.optString("village"),
            address?.optString("town"),
            address?.optString("city"),
            address?.optString("county"),
            address?.optString("road")
        ).mapNotNull { it?.trim()?.takeIf { s -> s.isNotBlank() && !isDigitsOnly(s) } }

        if (candidates.isNotEmpty()) return candidates.first()

        val dn = displayName?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val firstPart = dn.split(",").firstOrNull()?.trim()
        return firstPart?.takeIf { it.isNotBlank() && !isDigitsOnly(it) } ?: dn
    }

    private fun isDigitsOnly(s: String): Boolean {
        val t = s.trim()
        if (t.isEmpty()) return false
        return t.all { it.isDigit() }
    }
}
