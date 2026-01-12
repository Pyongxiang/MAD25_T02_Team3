package np.ict.mad.madassg2025

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ForecastActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val lat = intent.getDoubleExtra("lat", Double.NaN)
        val lon = intent.getDoubleExtra("lon", Double.NaN)
        val place = intent.getStringExtra("place") ?: "My Location"

        setContent {
            ForecastAppleLike(
                place = place,
                lat = lat,
                lon = lon,
                onBack = { finish() }
            )
        }
    }
}

@Composable
private fun ForecastAppleLike(
    place: String,
    lat: Double,
    lon: Double,
    onBack: () -> Unit
) {
    val bg = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0B1220),
            Color(0xFF0F1A2C),
            Color(0xFF162A47)
        )
    )

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<WeatherForecast.ForecastResult?>(null) }

    LaunchedEffect(lat, lon) {
        loading = true
        error = null
        result = null

        if (lat.isNaN() || lon.isNaN()) {
            loading = false
            error = "Missing coordinates"
            return@LaunchedEffect
        }

        if (ApiConfig.OPEN_WEATHER_API_KEY.startsWith("PUT_")) {
            loading = false
            error = "API key not set in ApiConfig.kt"
            return@LaunchedEffect
        }

        try {
            val res = withContext(Dispatchers.IO) {
                WeatherForecast().getHourly24AndDaily5(lat, lon)
            }
            loading = false
            if (res == null) error = "Unable to load 5-day forecast"
            else result = res
        } catch (e: Exception) {
            loading = false
            error = e.message ?: "Forecast failed"
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TopBar(place = place, onBack = onBack)

            when {
                loading -> Text("Loading forecast...", color = Color.White.copy(alpha = 0.85f))
                error != null -> Text("Error: $error", color = Color.White)
                result == null -> Text("No data", color = Color.White)
                else -> {
                    val r = result!!

                    val today = r.dailyNext5.firstOrNull()
                    val headerTemp = r.hourlyNext24.firstOrNull()?.tempC ?: today?.highC ?: 0
                    val headerDesc = r.hourlyNext24.firstOrNull()?.description ?: today?.description ?: "—"
                    val hi = today?.highC ?: headerTemp
                    val lo = today?.lowC ?: headerTemp

                    HeaderBlock(
                        place = place,
                        tempC = headerTemp,
                        condition = headerDesc,
                        hi = hi,
                        lo = lo
                    )

                    FrostCard {
                        Text(
                            text = "Next 24 hours",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(10.dp))
                        HourlyRow(r.hourlyNext24)
                    }

                    FrostCard {
                        Text(
                            text = "Next 5 days",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        DailyList(r.dailyNext5)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(place: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            modifier = Modifier
                .size(42.dp)
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
private fun HeaderBlock(place: String, tempC: Int, condition: String, hi: Int, lo: Int) {
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
private fun FrostCard(content: @Composable ColumnScope.() -> Unit) {
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
private fun HourlyRow(items: List<WeatherForecast.HourItem>) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items.forEach { h ->
            Column(
                modifier = Modifier.width(56.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = h.label,
                    color = Color.White.copy(alpha = 0.80f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
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
    }
}

@Composable
private fun DailyList(items: List<WeatherForecast.DayItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

                Text(text = pickEmojiIcon(d.weatherId), modifier = Modifier.width(34.dp))

                Text(
                    text = "${d.lowC}° / ${d.highC}°",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun pickEmojiIcon(weatherId: Int): String {
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
