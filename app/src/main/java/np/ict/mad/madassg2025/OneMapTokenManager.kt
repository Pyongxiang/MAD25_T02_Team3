package np.ict.mad.madassg2025

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class OneMapTokenManager(private val context: Context) {

    companion object {
        private const val TAG = "OneMapTokenManager"
        private const val BASE_URL = "https://www.onemap.gov.sg"
        private const val TOKEN_PATH = "/api/auth/post/getToken"

        private const val PREFS = "onemap_prefs"
        private const val KEY_TOKEN = "access_token"
        private const val KEY_EXP = "token_exp_unix"
    }

    // From BuildConfig (your build.gradle.kts buildConfigField)
    private val email = BuildConfig.ONEMAP_EMAIL
    private val password = BuildConfig.ONEMAP_PASSWORD

    suspend fun getValidToken(): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cachedToken = prefs.getString(KEY_TOKEN, null)
        val cachedExp = prefs.getLong(KEY_EXP, 0L)

        val now = System.currentTimeMillis() / 1000L
        val refreshBufferSeconds = 6 * 60 * 60 // 6 hours

        if (!cachedToken.isNullOrBlank() && cachedExp > (now + refreshBufferSeconds)) {
            return cachedToken
        }

        val newToken = fetchNewToken() ?: return null
        val newExp = decodeJwtExp(newToken) ?: (now + 3600L) // fallback 1 hour

        prefs.edit()
            .putString(KEY_TOKEN, newToken)
            .putLong(KEY_EXP, newExp)
            .apply()

        return newToken
    }

    private suspend fun fetchNewToken(): String? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null

        try {
            val url = URL("$BASE_URL$TOKEN_PATH")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val body = JSONObject()
                .put("email", email)
                .put("password", password)
                .toString()

            // ✅ Correct writer: OutputStreamWriter
            conn.outputStream.use { os ->
                OutputStreamWriter(os).use { writer ->
                    writer.write(body)
                    writer.flush()
                }
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText)

            if (code !in 200..299 || text.isNullOrBlank()) {
                Log.e(TAG, "Token fetch failed: code=$code body=$text")
                return@withContext null
            }

            val json = JSONObject(text)
            json.optString("access_token").takeIf { it.isNotBlank() }

        } catch (e: Exception) {
            Log.e(TAG, "fetchNewToken exception: ${e.message}", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun decodeJwtExp(jwt: String): Long? {
        val parts = jwt.split(".")
        if (parts.size < 2) return null

        return try {
            val payload = parts[1]
            val decoded = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP))
            val obj = JSONObject(decoded)
            obj.optLong("exp", 0L).takeIf { it > 0L }
        } catch (_: Exception) {
            null
        }
    }
}
