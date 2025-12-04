package np.ict.mad.madassg2025

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object WeatherClient {

    private const val API_KEY = "609ac56ec358de4c72c375183bc62c70" // Replace with your OpenWeather API Key
    private const val BASE_URL = "https://api.openweathermap.org/data/2.5/weather"

    private val client = OkHttpClient()

    suspend fun getWeather(lat: Double, lon: Double): String? {
        val url = "$BASE_URL?lat=$lat&lon=$lon&appid=$API_KEY&units=metric"

        // Log the coordinates being sent to the weather API
        Log.d("WeatherClient", "Fetching weather for coordinates: ($lat, $lon)")

        val request = Request.Builder()
            .url(url)
            .build()

        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()

                // Log the response for debugging purposes
                Log.d("WeatherClient", "Weather API response: ${response.code} ${response.body?.string()}")

                if (!response.isSuccessful) {
                    return@withContext null
                }

                val body = response.body?.string() ?: return@withContext null
                val jsonObject = JSONObject(body)

                val weather = jsonObject.getJSONArray("weather").getJSONObject(0)
                val main = jsonObject.getJSONObject("main")

                val description = weather.getString("description")
                val temperature = main.getDouble("temp")
                val humidity = main.getInt("humidity")

                return@withContext "Weather: $description\nTemp: ${temperature}°C\nHumidity: $humidity%"
            } catch (e: Exception) {
                Log.e("WeatherClient", "Error fetching weather data: ${e.message}")
                e.printStackTrace()
                return@withContext null
            }
        }
    }
}
