package np.ict.mad.madassg2025

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

fun formatLocalTime(utcEpochSec: Long, tzOffsetSec: Int): String {
    val localEpochSec = utcEpochSec + tzOffsetSec.toLong()
    val fmt = SimpleDateFormat("h:mm a", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return fmt.format(Date(localEpochSec * 1000L))
}

fun pickEmojiIcon(weatherId: Int): String {
    return when (weatherId) {
        in 200..232 -> "⛈️"
        in 300..321 -> "🌦️"
        in 500..531 -> "🌧️"
        in 600..622 -> "❄️"
        in 701..781 -> "🌫️"
        800 -> "☀️"
        in 801..804 -> "☁️"
        else -> "☁️"
    }
}

fun buildNext24Summary(hourly: List<WeatherForecast.HourItem>): String {
    if (hourly.isEmpty()) return ""

    val firstDesc = hourly.firstOrNull()?.description?.trim().orEmpty()
    val change = hourly.firstOrNull { it.description.isNotBlank() && it.description != firstDesc }
    val descItem = change ?: hourly.firstOrNull { it.description.isNotBlank() }

    val descPart = if (descItem != null) {
        val desc = descItem.description.replaceFirstChar { it.uppercase() }
        val time = descItem.label
        "$desc around $time"
    } else {
        ""
    }

    val maxWindItem = hourly.maxByOrNull { maxOf(it.windSpeedMs, it.windGustMs) }
    val windPart = if (maxWindItem != null) {
        val w = maxOf(maxWindItem.windSpeedMs, maxWindItem.windGustMs).roundToInt()
        "Wind up to ${w} m/s around ${maxWindItem.label}"
    } else {
        ""
    }

    return listOf(descPart, windPart).filter { it.isNotBlank() }.joinToString(" • ")
}
