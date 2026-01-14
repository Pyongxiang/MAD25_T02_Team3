package np.ict.mad.madassg2025

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun TopBar(place: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            modifier = Modifier
                .width(42.dp)
                .height(42.dp)
                .clickable { onBack() },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f))
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = place,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun HeaderBlock(place: String, tempC: Int, condition: String, hi: Int, lo: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = place,
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${tempC}°",
            color = Color.White,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = condition.replaceFirstChar { it.uppercase() },
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "H:$hi°  L:$lo°",
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun FrostCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content
        )
    }
}

@Composable
fun HourlyRowCompact(
    items: List<WeatherForecast.HourItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items.forEachIndexed { idx, h ->
            val selected = idx == selectedIndex

            Column(
                modifier = Modifier
                    .width(56.dp)
                    .clickable { onSelect(idx) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = h.label,
                    color = Color.White.copy(alpha = if (selected) 0.95f else 0.78f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )

                Card(
                    modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = pickEmojiIcon(h.weatherId),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "${h.tempC}°",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (selected) {
                    Spacer(
                        modifier = Modifier
                            .width(18.dp)
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.65f), RoundedCornerShape(99.dp))
                    )
                } else {
                    Spacer(modifier = Modifier.height(3.dp))
                }
            }
        }
    }
}

@Composable
fun TempSparkline(temps: List<Int>) {
    val minT = temps.minOrNull() ?: return
    val maxT = temps.maxOrNull() ?: return
    val range = max(1, maxT - minT)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        val w = size.width
        val h = size.height

        fun x(i: Int): Float = if (temps.size == 1) 0f else (i.toFloat() / (temps.size - 1)) * w
        fun y(t: Int): Float {
            val norm = (t - minT).toFloat() / range.toFloat()
            return (1f - norm) * h
        }

        for (i in 0 until temps.size - 1) {
            drawLine(
                color = Color.White.copy(alpha = 0.55f),
                start = Offset(x(i), y(temps[i])),
                end = Offset(x(i + 1), y(temps[i + 1])),
                strokeWidth = 3f
            )
        }

        for (i in temps.indices) {
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = 4f,
                center = Offset(x(i), y(temps[i]))
            )
        }
    }
}

@Composable
fun DetailsCardBelow(h: WeatherForecast.HourItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Details • ${h.label}",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DetailStat(icon = "🌡️", label = "Feels like", value = "${h.feelsLikeC}°")
                DetailStat(icon = "💧", label = "Humidity", value = "${h.humidityPct}%")
                DetailStat(
                    icon = "🌬️",
                    label = "Wind",
                    value = "${(maxOf(h.windSpeedMs, h.windGustMs)).roundToInt()} m/s"
                )
            }

            Text(
                text = h.description.replaceFirstChar { it.uppercase() },
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DetailStat(icon: String, label: String, value: String) {
    Column(modifier = Modifier.width(92.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon)
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.70f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun DailyList(items: List<WeatherForecast.DayItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { d ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = d.dayLabel,
                    color = Color.White,
                    modifier = Modifier.width(64.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = pickEmojiIcon(d.weatherId),
                    modifier = Modifier.width(34.dp)
                )

                Text(
                    text = d.description.replaceFirstChar { it.uppercase() }.ifBlank { "—" },
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val windMax = maxOf(d.maxWindMs, d.maxGustMs).roundToInt()
                Text(
                    text = "💨 ${windMax}m/s",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(84.dp),
                    maxLines = 1
                )

                Text(
                    text = "${d.lowC}° / ${d.highC}°",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}
