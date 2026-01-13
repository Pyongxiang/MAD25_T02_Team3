package np.ict.mad.madassg2025

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class WeatherForecast(
    private val apiKey: String = ApiConfig.OPEN_WEATHER_API_KEY
) {
    private val baseUrl = "https://api.openweathermap.org/data/2.5/forecast"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    data class HourItem(
        val dtUtcSec: Long,
        val label: String,     // "Now", "9PM", etc.
        val tempC: Int,
        val feelsLikeC: Int,
        val humidityPct: Int,
        val windSpeedMs: Double,
        val windGustMs: Double,
        val weatherId: Int,
        val description: String
    )

    data class DayItem(
        val dayLabel: String,  // "Today", "Tue", etc.
        val lowC: Int,
        val highC: Int,
        val weatherId: Int,
        val description: String,
        val maxWindMs: Double,
        val maxGustMs: Double
    )

    data class ForecastResult(
        val cityName: String,
        val hourlyNext24: List<HourItem>,
        val dailyNext5: List<DayItem>
    )

    /**
     * Uses OpenWeather 5 day / 3 hour forecast endpoint.
     * Endpoint returns forecast points in 3-hour steps.
     */
    fun getHourly24AndDaily5(lat: Double, lon: Double): ForecastResult? {
        if (apiKey.isBlank() || apiKey.startsWith("PUT_")) return null

        val url = "$baseUrl?lat=$lat&lon=$lon&appid=$apiKey&units=metric"

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "MADAssg2025/1.0 (student project)")
            .build()

        val resp = try { client.newCall(req).execute() } catch (_: Exception) { null } ?: return null
        resp.use { r ->
            if (!r.isSuccessful) return null

            val raw = r.body()?.string()?.takeIf { it.isNotBlank() } ?: return null
            val json = JSONObject(raw)

            val cityObj = json.optJSONObject("city") ?: return null
            val cityName = cityObj.optString("name", "My Location")

            // Forecast endpoint provides city timezone offset (seconds from UTC) in "city.timezone"
            val tzOffsetSec = cityObj.optInt("timezone", 0)

            val listArr = json.optJSONArray("list") ?: return null
            if (listArr.length() == 0) return null

            // ---- HOURLY: true "next 24h" window (not fixed 8 items) ----
            val nowUtcSec = System.currentTimeMillis() / 1000L
            val endUtcSec = nowUtcSec + 24L * 3600L

            val hourFmt = SimpleDateFormat("ha", Locale.getDefault()).apply {
                // We'll format a "local" timestamp by adding tzOffsetSec to dt, then format in UTC
                timeZone = TimeZone.getTimeZone("UTC")
            }

            val hourlyItems = mutableListOf<HourItem>()
            var firstAdded = false

            for (i in 0 until listArr.length()) {
                val item = listArr.optJSONObject(i) ?: continue
                val dt = item.optLong("dt", 0L)
                if (dt <= 0L) continue
                if (dt < nowUtcSec) continue
                if (dt > endUtcSec) break // list is chronological; once past 24h, we can stop

                val main = item.optJSONObject("main") ?: continue
                val temp = main.optDouble("temp", Double.NaN)
                val feels = main.optDouble("feels_like", Double.NaN)
                val humidity = main.optInt("humidity", -1)

                val wind = item.optJSONObject("wind")
                val windSpeed = wind?.optDouble("speed", Double.NaN) ?: Double.NaN
                val windGust = wind?.optDouble("gust", Double.NaN) ?: Double.NaN

                val w0 = item.optJSONArray("weather")?.optJSONObject(0)
                val wid = w0?.optInt("id", 0) ?: 0
                val wdesc = w0?.optString("description", "") ?: ""

                val localSec = dt + tzOffsetSec
                val label = if (!firstAdded) {
                    firstAdded = true
                    "Now"
                } else {
                    hourFmt.format(Date(localSec * 1000L))
                }

                hourlyItems.add(
                    HourItem(
                        dtUtcSec = dt,
                        label = label,
                        tempC = if (temp.isNaN()) 0 else temp.roundToInt(),
                        feelsLikeC = if (feels.isNaN()) 0 else feels.roundToInt(),
                        humidityPct = if (humidity < 0) 0 else humidity,
                        windSpeedMs = if (windSpeed.isNaN()) 0.0 else windSpeed,
                        windGustMs = if (windGust.isNaN()) 0.0 else windGust,
                        weatherId = wid,
                        description = wdesc
                    )
                )
            }

            // ---- DAILY: group by local date (next 5) + include max wind/gust ----
            data class Acc(
                var low: Double = Double.POSITIVE_INFINITY,
                var high: Double = Double.NEGATIVE_INFINITY,
                val weatherCounts: MutableMap<Int, Int> = mutableMapOf(),
                val descById: MutableMap<Int, String> = mutableMapOf(),
                var firstDtLocalSec: Long = Long.MAX_VALUE,
                var maxWind: Double = 0.0,
                var maxGust: Double = 0.0
            )

            val dayKeyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val dayLabelFmt = SimpleDateFormat("EEE", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            val accByDay = linkedMapOf<String, Acc>()

            for (i in 0 until listArr.length()) {
                val item = listArr.optJSONObject(i) ?: continue
                val dt = item.optLong("dt", 0L)
                if (dt <= 0L) continue

                val main = item.optJSONObject("main") ?: continue
                val temp = main.optDouble("temp", Double.NaN)
                if (temp.isNaN()) continue

                val wind = item.optJSONObject("wind")
                val windSpeed = wind?.optDouble("speed", Double.NaN) ?: Double.NaN
                val windGust = wind?.optDouble("gust", Double.NaN) ?: Double.NaN

                val w0 = item.optJSONArray("weather")?.optJSONObject(0)
                val wid = w0?.optInt("id", 0) ?: 0
                val wdesc = w0?.optString("description", "") ?: ""

                val localSec = dt + tzOffsetSec
                val key = dayKeyFmt.format(Date(localSec * 1000L))

                val acc = accByDay.getOrPut(key) { Acc() }
                acc.low = minOf(acc.low, temp)
                acc.high = maxOf(acc.high, temp)

                acc.weatherCounts[wid] = (acc.weatherCounts[wid] ?: 0) + 1
                if (!acc.descById.containsKey(wid) && wdesc.isNotBlank()) {
                    acc.descById[wid] = wdesc
                }
                acc.firstDtLocalSec = minOf(acc.firstDtLocalSec, localSec)

                if (!windSpeed.isNaN()) acc.maxWind = maxOf(acc.maxWind, windSpeed)
                if (!windGust.isNaN()) acc.maxGust = maxOf(acc.maxGust, windGust)
            }

            val dailyItems = mutableListOf<DayItem>()
            val keys = accByDay.keys.toList()
            val maxDays = minOf(5, keys.size)

            for (idx in 0 until maxDays) {
                val key = keys[idx]
                val acc = accByDay[key] ?: continue

                val dominantId = acc.weatherCounts.maxByOrNull { it.value }?.key ?: 0
                val desc = acc.descById[dominantId] ?: ""

                val dayLabel =
                    if (idx == 0) "Today"
                    else dayLabelFmt.format(Date(acc.firstDtLocalSec * 1000L))

                dailyItems.add(
                    DayItem(
                        dayLabel = dayLabel,
                        lowC = if (acc.low.isInfinite()) 0 else acc.low.roundToInt(),
                        highC = if (acc.high.isInfinite()) 0 else acc.high.roundToInt(),
                        weatherId = dominantId,
                        description = desc,
                        maxWindMs = acc.maxWind,
                        maxGustMs = acc.maxGust
                    )
                )
            }

            return ForecastResult(
                cityName = cityName,
                hourlyNext24 = hourlyItems,
                dailyNext5 = dailyItems
            )
        }
    }
}
