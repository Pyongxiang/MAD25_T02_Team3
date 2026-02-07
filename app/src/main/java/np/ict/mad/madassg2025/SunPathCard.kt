package np.ict.mad.madassg2025

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import np.ict.mad.madassg2025.forecast.formatLocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SunPathCard(
    sunriseUtc: Long,
    sunsetUtc: Long,
    tzOffsetSec: Int,
    nowUtcSec: Long
) {
    // Convert to "local seconds" using tzOffset (OpenWeather timezone offset)
    val sunriseLocal = sunriseUtc + tzOffsetSec.toLong()
    val sunsetLocal = sunsetUtc + tzOffsetSec.toLong()
    val nowLocal = nowUtcSec + tzOffsetSec.toLong()

    val daylightSec = (sunsetLocal - sunriseLocal).coerceAtLeast(0L)

    // progress through daylight
    val progress = when {
        daylightSec <= 0L -> 0f
        nowLocal <= sunriseLocal -> 0f
        nowLocal >= sunsetLocal -> 1f
        else -> ((nowLocal - sunriseLocal).toDouble() / daylightSec.toDouble()).toFloat()
    }.coerceIn(0f, 1f)

    val sunriseStr = formatLocalTime(sunriseUtc, tzOffsetSec)
    val sunsetStr = formatLocalTime(sunsetUtc, tzOffsetSec)

    val remainingSec = when {
        nowLocal < sunriseLocal -> (sunriseLocal - nowLocal).coerceAtLeast(0L)
        nowLocal in sunriseLocal..sunsetLocal -> (sunsetLocal - nowLocal).coerceAtLeast(0L)
        else -> 0L
    }

    val statusLine = when {
        daylightSec <= 0L -> "Sun times unavailable"
        nowLocal < sunriseLocal -> "Sunrise in ${formatDuration(remainingSec)}"
        nowLocal in sunriseLocal..sunsetLocal -> "Daylight remaining ${formatDuration(remainingSec)}"
        else -> "Sun has set"
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Sun Path",
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = statusLine,
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.bodyMedium
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
        ) {
            val w = size.width
            val h = size.height

            // Semi-circle arc baseline at bottom
            val radius = (w * 0.42f).coerceAtMost(h * 0.95f)
            val center = Offset(w / 2f, h * 0.95f)

            // Background arc
            drawArc(
                color = Color.White.copy(alpha = 0.20f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = 10f, cap = StrokeCap.Round)
            )

            // Progress arc (daylight portion)
            if (progress > 0f) {
                drawArc(
                    color = Color.White.copy(alpha = 0.55f),
                    startAngle = 180f,
                    sweepAngle = 180f * progress,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = Stroke(width = 10f, cap = StrokeCap.Round)
                )
            }

            // Sun dot on the arc
            val theta = PI * (1.0 - progress.toDouble()) // PI..0
            val sunX = center.x + (radius * cos(theta)).toFloat()
            val sunY = center.y - (radius * sin(theta)).toFloat()

            drawCircle(
                color = Color.White.copy(alpha = 0.95f),
                radius = 10f,
                center = Offset(sunX, sunY)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.25f),
                radius = 18f,
                center = Offset(sunX, sunY)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "🌅 Sunrise",
                    color = Color.White.copy(alpha = 0.70f),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = sunriseStr,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "🌇 Sunset",
                    color = Color.White.copy(alpha = 0.70f),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = sunsetStr,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
