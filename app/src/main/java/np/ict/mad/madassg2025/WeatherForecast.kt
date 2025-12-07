package np.ict.mad.madassg2025

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*


class WeatherForecast(private val apiKey: String) {

    private val baseUrl =
        "https://api.openweathermap.org/data/2.5/onecall"

    data class ForecastDay(
        val dt: Long,
        val tempDay: Double,
        val tempMin: Double,
        val tempMax: Double,
        val weatherMain: String,
        val weatherDescription: String,
        val icon: String
    ) {
        fun formattedDate(): String {
            val sdf = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
            sdf.timeZone = TimeZone.getDefault()
            return sdf.format(Date(dt * 1000L))
        }
    }

    /**
     * Get 7-day forecast.
     */
    suspend fun get7DayForecast(
        lat: Double,
        lon: Double,
        units: String = "metric"
    ): List<ForecastDay>? {

        return withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null

            try {
                val urlStr =
                    "$baseUrl?lat=$lat&lon=$lon&exclude=current,minutely,hourly,alerts&units=$units&appid=$apiKey"

                val url = URL(urlStr)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }

                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) return@withContext null

                val text =
                    conn.inputStream.bufferedReader()
                        .use(BufferedReader::readText)

                val root = JSONObject(text)
                val arr: JSONArray = root.optJSONArray("daily") ?: return@withContext null

                val list = mutableListOf<ForecastDay>()

                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)

                    val dt = item.optLong("dt", 0L)
                    val tempObj = item.optJSONObject("temp")

                    val day = tempObj?.optDouble("day", Double.NaN) ?: Double.NaN
                    val min = tempObj?.optDouble("min", Double.NaN) ?: Double.NaN
                    val max = tempObj?.optDouble("max", Double.NaN) ?: Double.NaN

                    val weatherArr = item.optJSONArray("weather")
                    val w = if (weatherArr != null && weatherArr.length() > 0)
                        weatherArr.getJSONObject(0)
                    else null

                    val main = w?.optString("main", "") ?: ""
                    val desc = w?.optString("description", "") ?: ""
                    val icon = w?.optString("icon", "") ?: ""

                    list.add(ForecastDay(dt, day, min, max, main, desc, icon))
                }

                list

            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                conn?.disconnect()
            }
        }
    }

    /**
     * Get hourly forecast (up to 48 hours).
     */
    suspend fun getHourlyForecast(
        lat: Double,
        lon: Double,
        hours: Int = 24,
        units: String = "metric"
    ): List<ForecastDay>? {

        return withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null

            try {
                val urlStr =
                    "$baseUrl?lat=$lat&lon=$lon&exclude=current,minutely,daily,alerts&units=$units&appid=$apiKey"

                val url = URL(urlStr)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }

                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null

                val text = conn.inputStream.bufferedReader()
                    .use(BufferedReader::readText)

                val root = JSONObject(text)
                val arr: JSONArray = root.optJSONArray("hourly") ?: return@withContext null

                val list = mutableListOf<ForecastDay>()
                val takeCount = minOf(hours, arr.length())

                for (i in 0 until takeCount) {
                    val item = arr.getJSONObject(i)
                    val dt = item.optLong("dt", 0L)
                    val temp = item.optDouble("temp", Double.NaN)

                    val weatherArr = item.optJSONArray("weather")
                    val w = if (weatherArr != null && weatherArr.length() > 0)
                        weatherArr.getJSONObject(0)
                    else null

                    val main = w?.optString("main", "") ?: ""
                    val desc = w?.optString("description", "") ?: ""
                    val icon = w?.optString("icon", "") ?: ""

                    list.add(
                        ForecastDay(
                            dt,
                            temp,
                            temp,
                            temp,
                            main,
                            desc,
                            icon
                        )
                    )
                }

                list
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                conn?.disconnect()
            }
        }
    }
}
