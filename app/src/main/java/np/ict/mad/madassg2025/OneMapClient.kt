package np.ict.mad.madassg2025

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object OneMapClient {

    private const val TAG = "OneMapClient"

    // 🔑 Your OneMap access_token (JWT). This expires after a few days.
    // When it expires, you must get a new one and paste it here.
    private const val ONE_MAP_TOKEN =
        "API_KEY"

    private const val BASE_URL = "https://www.onemap.gov.sg"
    private const val REVERSE_GEOCODE_PATH = "/privateapi/commonsvc/revgeocode"

    /**
     * Reverse geocode lat/lon using OneMap private API.
     *
     * Returns:
     *  - BUILDINGNAME (e.g. "ION ORCHARD"), or
     *  - "BLOCK ROAD" (e.g. "238 THOMSON ROAD"), or
     *  - ROAD, or
     *  - null if request fails / no useful result.
     */
    suspend fun reverseGeocode(lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            val urlString =
                "$BASE_URL$REVERSE_GEOCODE_PATH?location=$lat,$lon&buffer=20&addressType=All"

            var connection: HttpURLConnection? = null

            try {
                val url = URL(urlString)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    // ✅ NEW: pass token in header
                    setRequestProperty("Authorization", "Bearer $ONE_MAP_TOKEN")
                }

                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    val errorBody = connection.errorStream
                        ?.bufferedReader()
                        ?.use { it.readText() }
                    Log.e(TAG, "revgeocode failed: code=$code, error=$errorBody")
                    return@withContext null
                }

                val responseText = connection.inputStream.use { input ->
                    BufferedReader(InputStreamReader(input)).use { reader ->
                        val sb = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            sb.append(line)
                        }
                        sb.toString()
                    }
                }

                val trimmed = responseText.trim()

                // Guard against HTML so we don't crash if they send a page instead of JSON
                if (trimmed.startsWith("<")) {
                    Log.e(
                        TAG,
                        "revgeocode returned HTML, not JSON. Body (first 200 chars): " +
                                trimmed.take(200)
                    )
                    return@withContext null
                }

                Log.d(TAG, "revgeocode body: $trimmed")

                val root = JSONObject(trimmed)
                val arr = root.optJSONArray("GeocodeInfo") ?: return@withContext null
                if (arr.length() == 0) return@withContext null

                val obj = arr.getJSONObject(0)

                val buildingName = obj.optString("BUILDINGNAME").takeIf { it.isNotBlank() }
                val road = obj.optString("ROAD").takeIf { it.isNotBlank() }
                val block = obj.optString("BLOCK").takeIf { it.isNotBlank() }

                return@withContext when {
                    buildingName != null -> buildingName
                    block != null && road != null -> "$block $road"
                    road != null -> road
                    else -> null
                }
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Exception in reverseGeocode: ${e::class.java.simpleName}: ${e.message}"
                )
                null
            } finally {
                connection?.disconnect()
            }
        }
}
