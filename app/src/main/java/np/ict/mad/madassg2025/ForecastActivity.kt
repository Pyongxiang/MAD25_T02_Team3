package np.ict.mad.madassg2025

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
            val bg = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0B1220),
                    Color(0xFF101B2D),
                    Color(0xFF1A2A45)
                )
            )

            var error by remember { mutableStateOf<String?>(null) }
            var forecast by remember { mutableStateOf<List<WeatherForecast.ForecastDay>>(emptyList()) }

            LaunchedEffect(lat, lon) {
                if (lat.isNaN() || lon.isNaN()) {
                    error = "Missing coordinates"
                    return@LaunchedEffect
                }

                // IMPORTANT:
                // WeatherForecast needs an OpenWeather API key.
                // I cannot confirm where your project stores that key because WeatherRepository.kt was not uploaded.
                // Replace the placeholder below with the same key source you already use for current weather.
                val apiKey = "REPLACE_WITH_YOUR_OPENWEATHER_API_KEY"

                val result = withContext(Dispatchers.IO) {
                    WeatherForecast(apiKey).get7DayForecast(lat, lon)
                }

                if (result == null) {
                    error = "Unable to load forecast"
                } else {
                    error = null
                    forecast = result.take(7)
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
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = place,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "7-Day Forecast",
                        color = Color.White.copy(alpha = 0.80f),
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (error != null) {
                        Text(text = error!!, color = Color.White)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(forecast) { day ->
                                ForecastRow(day)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForecastRow(day: WeatherForecast.ForecastDay) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.10f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = day.formattedDate(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = day.weatherDescription.replaceFirstChar { it.uppercase() },
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = "${day.tempMin.toInt()}° / ${day.tempMax.toInt()}°",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}
