package np.ict.mad.madassg2025

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object OneMapClient {

    private const val ONE_MAP_TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoxMDMzNiwiZm9yZXZlciI6ZmFsc2UsImlzcyI6Ik9uZU1hcCIsImlhdCI6MTc2NDgxNTAxNCwibmJmIjoxNzY0ODE1MDE0LCJleHAiOjE3NjUwNzQyMTQsImp0aSI6ImNkMmMxYmYyLTI1Y2QtNGQ1YS04ZjEzLTY2NDIwNjMyNjI2NyJ9.QxppM6FCZtj1GEjh-duKm0vKBnDf1GjIb3H8lHqcU7tf8GDBgvHiLlGLZ6eI-gcJSkd1_LIivJ8KyR6FgBeKKQM_BWtQ5uU0bRr6l1jkOnc0pFrBrZ-Y_mYhZ2HPOkQ_eeWxoY7Qd9KJ4y9_GF0EaQE5V9KKKJJ0YP57gbxmhjXliuPwBJFiVxEHSRH5z3unMPWQud2QdxlKFzjMtXgvo1DWoh0DNSV6bHgvVGP8Wz4g5hHsnQPsU_PrU_gAb5Xp6FMUvs43QC4q6_vpFDU4dvJ5HAEkm4XNXlyFxMZrGf-SKyHg-1J5YbRMpipjInIduIswtr7jbnimJciAzy9Ybw"

    private const val BASE_URL = "https://www.onemap.gov.sg"
    private const val REVERSE_GEOCODE_PATH = "/api/public/revgeocode"

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
                    setRequestProperty("Authorization", "Bearer $ONE_MAP_TOKEN")
                }

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
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

                val root = JSONObject(responseText)
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
                e.printStackTrace()
                null
            } finally {
                connection?.disconnect()
            }
        }
}
