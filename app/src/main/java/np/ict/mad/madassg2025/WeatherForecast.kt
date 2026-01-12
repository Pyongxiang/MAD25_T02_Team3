package np.ict.mad.madassg2025

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

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
        val label: String,     // "Now", "2PM", etc.
        val tempC: Int,
        val weatherId: Int,
        val description: String
    )

    data class DayItem(
        val dayLabel: String,  // "Today", "Tue", etc.
        val lowC: Int,
        val highC: Int,
        val weatherId: Int,
        val description: String
    )

    data class ForecastResult(
        val cityName: String,
        val hourlyNext24: List<HourItem>,
        val dailyNext5: List<DayItem>
    )

    /**
     * Uses OpenWeather 5 day / 3 hour forecast endpoint.
     * Docs: /data/2.5/forecast provides forecast in 3-hour steps. :contentReference[oaicite:2]{index=2}
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

            // Build hourly next 24h: next 8 x 3-hour points
            val nowUtcSec = System.currentTimeMillis() / 1000L
            val hourlyItems = mutableListOf<HourItem>()

            val hourFmt = SimpleDateFormat("ha", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            var collected = 0
            for (i in 0 until listArr.length()) {
                val item = listArr.optJSONObject(i) ?: continue
                val dt = item.optLong("dt", 0L)
                if (dt <= 0L) continue

                // Take points from "now" onwards
                if (dt < nowUtcSec) continue

                val main = item.optJSONObject("main") ?: continue
                val temp = main.optDouble("temp", Double.NaN)
                val w0 = item.optJSONArray("weather")?.optJSONObject(0)
                val wid = w0?.optInt("id", 0) ?: 0
                val wdesc = w0?.optString("description", "") ?: ""

                val localSec = dt + tzOffsetSec
                val label = if (collected == 0) {
                    "Now"
                } else {
                    hourFmt.format(Date(localSec * 1000L))
                }

                hourlyItems.add(
                    HourItem(
                        dtUtcSec = dt,
                        label = label,
                        tempC = if (temp.isNaN()) 0 else temp.toInt(),
                        weatherId = wid,
                        description = wdesc
                    )
                )

                collected++
                if (collected >= 8) break // 8 * 3h ≈ 24h
            }

            // Build daily next 5 days by grouping forecast points by local date
            data class Acc(
                var low: Double = Double.POSITIVE_INFINITY,
                var high: Double = Double.NEGATIVE_INFINITY,
                val weatherCounts: MutableMap<Int, Int> = mutableMapOf(),
                val descById: MutableMap<Int, String> = mutableMapOf(),
                var firstDtLocalSec: Long = Long.MAX_VALUE
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
            }

            // Convert grouped days to next 5 days, label "Today" for first
            val dailyItems = mutableListOf<DayItem>()
            val keys = accByDay.keys.toList()
            val maxDays = minOf(5, keys.size)

            for (idx in 0 until maxDays) {
                val key = keys[idx]
                val acc = accByDay[key] ?: continue

                val dominantId = acc.weatherCounts.maxByOrNull { it.value }?.key ?: 0
                val desc = acc.descById[dominantId] ?: ""

                val dayLabel = if (idx == 0) {
                    "Today"
                } else {
                    dayLabelFmt.format(Date(acc.firstDtLocalSec * 1000L))
                }

                dailyItems.add(
                    DayItem(
                        dayLabel = dayLabel,
                        lowC = if (acc.low.isInfinite()) 0 else acc.low.toInt(),
                        highC = if (acc.high.isInfinite()) 0 else acc.high.toInt(),
                        weatherId = dominantId,
                        description = desc
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
