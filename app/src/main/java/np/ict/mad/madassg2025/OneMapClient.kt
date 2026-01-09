package np.ict.mad.madassg2025

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object OneMapClient {

    private const val TAG = "OneMapClient"

    // ✅ Reverse geocode endpoint is on developers.onemap.sg (examples show this) :contentReference[oaicite:2]{index=2}
    private const val BASE_URL = "https://developers.onemap.sg"
    private const val REVERSE_GEOCODE_PATH = "/privateapi/commonsvc/revgeocode"

    private var tokenManager: OneMapTokenManager? = null

    fun init(context: Context) {
        if (tokenManager == null) tokenManager = OneMapTokenManager(context.applicationContext)
    }

    suspend fun reverseGeocode(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        val tm = tokenManager ?: run {
            Log.e(TAG, "Not initialized. Call OneMapClient.init(context) first.")
            return@withContext null
        }

        val token = tm.getValidToken()
        if (token.isNullOrBlank()) {
            Log.e(TAG, "No valid OneMap token (auto-renew failed).")
            return@withContext null
        }

        // ✅ token is query param (matches OneMap examples) :contentReference[oaicite:3]{index=3}
        val urlString =
            "$BASE_URL$REVERSE_GEOCODE_PATH?location=$lat,$lon&token=$token&buffer=20&addressType=All"

        var conn: HttpURLConnection? = null
        try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                // IMPORTANT: no Authorization header
            }

            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            if (code !in 200..299) {
                Log.e(TAG, "revgeocode failed: code=$code, error=$body")
                return@withContext null
            }

            val root = JSONObject(body)
            val arr = root.optJSONArray("GeocodeInfo") ?: return@withContext null
            if (arr.length() == 0) return@withContext null

            val obj = arr.getJSONObject(0)
            val buildingName = obj.optString("BUILDINGNAME").takeIf { it.isNotBlank() }
            val block = obj.optString("BLOCK").takeIf { it.isNotBlank() }
            val road = obj.optString("ROAD").takeIf { it.isNotBlank() }

            when {
                buildingName != null -> buildingName
                block != null && road != null -> "$block $road"
                road != null -> road
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "reverseGeocode exception: ${e.message}", e)
            null
        } finally {
            conn?.disconnect()
        }
    }
}
