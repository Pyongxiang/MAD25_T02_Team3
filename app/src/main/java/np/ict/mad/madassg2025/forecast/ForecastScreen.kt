package np.ict.mad.madassg2025.forecast

import android.content.Intent // Google Maps Imports
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import np.ict.mad.madassg2025.MapSearchActivity
import np.ict.mad.madassg2025.SunPathCard
import np.ict.mad.madassg2025.WeatherForecast
import np.ict.mad.madassg2025.WeatherRepository
import kotlin.math.max

@Composable
fun ForecastScreen(
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

    var selectedHourIndex by remember { mutableStateOf(0) }

    val context = LocalContext.current

    // sunrise/sunset info (for SunPathCard)
    var sunriseUtc by remember { mutableStateOf<Long?>(null) }
    var sunsetUtc by remember { mutableStateOf<Long?>(null) }
    var tzOffsetSec by remember { mutableStateOf<Int?>(null) }

    // sunrise/sunset line (existing)
    var sunLine by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(lat, lon) {
        loading = true
        error = null
        result = null
        sunLine = null
        selectedHourIndex = 0
        sunriseUtc = null
        sunsetUtc = null
        tzOffsetSec = null

        if (lat.isNaN() || lon.isNaN()) {
            loading = false
            error = "Missing coordinates"
            return@LaunchedEffect
        }

        try {
            val (forecastRes, currentRes) = withContext(Dispatchers.IO) {
                val f = WeatherForecast().getHourly24AndDaily5(lat, lon)
                val c = WeatherRepository.getCurrentWeather(lat, lon)
                Pair(f, c)
            }

            loading = false

            if (forecastRes == null) {
                error = "Unable to load 5-day forecast"
                return@LaunchedEffect
            }

            result = forecastRes

            if (currentRes != null) {
                val tz = currentRes.timezone
                tzOffsetSec = tz
                sunriseUtc = currentRes.sys.sunrise
                sunsetUtc = currentRes.sys.sunset

                val sunriseLocal = formatLocalTime(currentRes.sys.sunrise, tz)
                val sunsetLocal = formatLocalTime(currentRes.sys.sunset, tz)
                sunLine = "🌅 Sunrise $sunriseLocal • 🌇 Sunset $sunsetLocal"
            }
        } catch (e: Exception) {
            loading = false
            error = e.message ?: "Forecast failed"
        }
    }

    val verticalScroll = rememberScrollState()

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
        color = Color.Transparent
    ) {
        // Box wrapper to overlay the Map FAB
        Box(modifier = Modifier.fillMaxSize()) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScroll)
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

                        val hourly = r.hourlyNext24
                        val safeIndex = selectedHourIndex.coerceIn(0, max(0, hourly.size - 1))
                        val selected = hourly.getOrNull(safeIndex)

                        FrostCard {
                            Text(
                                text = "Next 24 hours",
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "3-hour steps • ${hourly.size} points",
                                color = Color.White.copy(alpha = 0.70f),
                                style = MaterialTheme.typography.bodySmall
                            )

                            val summary = buildNext24Summary(hourly)
                            if (summary.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = summary,
                                    color = Color.White.copy(alpha = 0.82f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (!sunLine.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = sunLine!!,
                                    color = Color.White.copy(alpha = 0.80f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            HourlyRowCompact(
                                items = hourly,
                                selectedIndex = safeIndex,
                                onSelect = { selectedHourIndex = it }
                            )

                            if (hourly.size >= 2) {
                                Spacer(Modifier.height(12.dp))
                                TempSparkline(hourly.map { it.tempC })
                            }
                        }

                        if (selected != null) {
                            DetailsCardBelow(selected)
                        }

                        // ✅ Sun Path card (new)
                        val sRise = sunriseUtc
                        val sSet = sunsetUtc
                        val tz = tzOffsetSec
                        if (sRise != null && sSet != null && tz != null) {
                            FrostCard {
                                SunPathCard(
                                    sunriseUtc = sRise,
                                    sunsetUtc = sSet,
                                    tzOffsetSec = tz,
                                    nowUtcSec = System.currentTimeMillis() / 1000L
                                )
                            }
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

            // Map button at bottom-right of screen
            FloatingActionButton(
                onClick = {
                    context.startActivity(Intent(context, MapSearchActivity::class.java))
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 70.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Filled.Map,
                    contentDescription = "Open Map Weather Search"
                )
            }
        }
    }
}
