package np.ict.mad.madassg2025.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt
import kotlin.random.Random

data class HomeActions(
    val onToggleUnit: (UnitPref) -> Unit,
    val onUseMyLocation: () -> Unit,
    val onOpenForecast: () -> Unit,
    val onSelectSaved: (SavedLocation) -> Unit,
    val onAddCurrent: () -> Unit,
    val onRemoveSaved: (SavedLocation) -> Unit
)

@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Box(modifier = Modifier.fillMaxSize()) {
            DynamicSkyBackground(
                modifier = Modifier.fillMaxSize(),
                mode = state.skyMode
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header + units toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "My Location",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = state.placeLabel.ifBlank { "—" },
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        when {
                            state.isLoading -> Text(
                                text = "Loading…",
                                color = Color.White.copy(alpha = 0.80f),
                                style = MaterialTheme.typography.titleMedium
                            )

                            state.error != null -> Text(
                                text = "Error: ${state.error}",
                                color = Color.White.copy(alpha = 0.90f),
                                style = MaterialTheme.typography.bodyMedium
                            )

                            state.tempC == null || state.condition.isNullOrBlank() -> Text(
                                text = "Tap Use My Location to load weather",
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.bodyMedium
                            )

                            else -> {
                                val tempShown = formatTemp(state.tempC, state.unit)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = pickWeatherEmojiById(state.weatherId),
                                        modifier = Modifier.size(42.dp),
                                        color = Color.White,
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                    Text(
                                        text = tempShown,
                                        color = Color.White,
                                        style = MaterialTheme.typography.displayLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Text(
                                    text = state.condition!!.replaceFirstChar { it.uppercase() },
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }

                    UnitsToggle(
                        unit = state.unit,
                        onChange = actions.onToggleUnit
                    )
                }

                // NEW: split saving UI into a "My Location" row, then Favourites under it
                SavedLocationsSection(
                    state = state,
                    onAddCurrent = actions.onAddCurrent,
                    onSelectSaved = actions.onSelectSaved,
                    onRemoveSaved = actions.onRemoveSaved
                )

                // Forecast card (balanced left/right + skeleton)
                ForecastCard(
                    state = state,
                    onOpenForecast = actions.onOpenForecast
                )

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = actions.onUseMyLocation
                ) {
                    Text("Use My Location")
                }
            }
        }
    }
}

@Composable
private fun SavedLocationsSection(
    state: HomeUiState,
    onAddCurrent: () -> Unit,
    onSelectSaved: (SavedLocation) -> Unit,
    onRemoveSaved: (SavedLocation) -> Unit
) {
    val currentLabel = state.placeLabel.takeIf { it.isNotBlank() && it != "—" && it != "Loading…" }
    val scroll1 = rememberScrollState()
    val scroll2 = rememberScrollState()

    // Row 1: "My Location" + Save
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll1),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!currentLabel.isNullOrBlank()) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📍", color = Color.White)
                    Text(
                        text = currentLabel,
                        color = Color.White.copy(alpha = 0.90f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Card(
                modifier = Modifier.clickable { onAddCurrent() },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f))
            ) {
                Text(
                    text = "＋ Save",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    // Row 2: Favourites
    if (state.savedLocations.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Favourites",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scroll2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.savedLocations.forEach { loc ->
                    Card(
                        modifier = Modifier.combinedClickable(
                            onClick = { onSelectSaved(loc) },
                            onLongClick = { onRemoveSaved(loc) } // long-press to remove
                        ),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("★", color = Color.White.copy(alpha = 0.85f))
                            Text(
                                text = loc.name,
                                color = Color.White.copy(alpha = 0.88f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Text(
                text = "Tip: long-press a favourite to remove",
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ForecastCard(state: HomeUiState, onOpenForecast: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = state.canOpenForecast) { onOpenForecast() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Forecast (7 days)",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    text = when {
                        state.isLoading -> "Fetching your location…"
                        state.lastLat == null -> "Load location to view forecast"
                        else -> "Tap to view the next 7 days"
                    },
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyMedium
                )

                if (!state.isLoading && state.tempC != null && state.weatherId != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = pickWeatherEmojiById(state.weatherId),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = formatTemp(state.tempC, state.unit) + " " + state.unit.label,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (state.isLoading) {
                    SkeletonLine(widthDp = 150, heightDp = 18)
                    SkeletonLine(widthDp = 120, heightDp = 18)
                }
            }

            // Right
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End
            ) {
                val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                Text(
                    text = "Updated $timeStr",
                    color = Color.White.copy(alpha = 0.70f),
                    style = MaterialTheme.typography.bodySmall
                )

                if (!state.isLoading && state.sunriseUtc != null && state.sunsetUtc != null && state.tzOffsetSec != null) {
                    val sunrise = formatLocalTime(state.sunriseUtc, state.tzOffsetSec)
                    val sunset = formatLocalTime(state.sunsetUtc, state.tzOffsetSec)
                    Text(
                        text = "🌅 $sunrise",
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "🌇 $sunset",
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (state.isLoading) {
                    SkeletonLine(widthDp = 110, heightDp = 18)
                    SkeletonLine(widthDp = 90, heightDp = 18)
                }

                if (!state.isLoading && state.locationText.isNotBlank()) {
                    Text(
                        text = state.locationText,
                        color = Color.White.copy(alpha = 0.70f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun formatTemp(tempC: Double?, unit: UnitPref): String {
    if (tempC == null) return "—"
    return when (unit) {
        UnitPref.C -> "${tempC.roundToInt()}°"
        UnitPref.F -> "${(tempC * 9.0 / 5.0 + 32.0).roundToInt()}°"
    }
}

private fun pickWeatherEmojiById(weatherId: Int?): String {
    return when (weatherId) {
        in 200..232 -> "⛈️"
        in 300..321 -> "🌦️"
        in 500..504 -> "🌧️"
        511 -> "🌨️"
        in 520..531 -> "🌧️"
        in 600..622 -> "❄️"
        in 701..781 -> "🌫️"
        800 -> "☀️"
        in 801..804 -> "☁️"
        else -> "🌡️"
    }
}

private fun formatLocalTime(utcEpochSec: Long, tzOffsetSec: Int): String {
    val localEpochSec = utcEpochSec + tzOffsetSec.toLong()
    val fmt = SimpleDateFormat("h:mm a", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return fmt.format(Date(localEpochSec * 1000L))
}

@Composable
private fun UnitsToggle(unit: UnitPref, onChange: (UnitPref) -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f))
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            UnitChip(
                label = "°C",
                selected = unit == UnitPref.C,
                onClick = { onChange(UnitPref.C) }
            )
            UnitChip(
                label = "°F",
                selected = unit == UnitPref.F,
                onClick = { onChange(UnitPref.F) }
            )
        }
    }
}

@Composable
private fun UnitChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = Color.White.copy(alpha = if (selected) 0.95f else 0.75f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun SkeletonLine(widthDp: Int, heightDp: Int) {
    Card(
        modifier = Modifier
            .width(widthDp.dp)
            .height(heightDp.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
    ) {}
}

/**
 * UPDATED: softer day/dawn/dusk glow that doesn't overpower cards.
 * - Glow is off-screen/top-right
 * - Lower alpha
 * - No big orange circles in the middle
 */
@Composable
private fun DynamicSkyBackground(modifier: Modifier, mode: SkyMode) {
    val base = when (mode) {
        SkyMode.NIGHT -> Brush.verticalGradient(
            listOf(Color(0xFF07101E), Color(0xFF0B1730), Color(0xFF0F2240))
        )
        SkyMode.DAWN -> Brush.verticalGradient(
            listOf(Color(0xFF0B1530), Color(0xFF2E3A70), Color(0xFF6B5A78))
        )
        SkyMode.DAY -> Brush.verticalGradient(
            listOf(Color(0xFF0B2447), Color(0xFF0E2E5A), Color(0xFF123667))
        )
        SkyMode.DUSK -> Brush.verticalGradient(
            listOf(Color(0xFF0B1530), Color(0xFF2D2A60), Color(0xFF5A3A66))
        )
    }

    Box(modifier = modifier.background(base)) {

        // Soft glow in dawn/day/dusk (top-right, subtle)
        if (mode != SkyMode.NIGHT) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width * 1.05f // off-screen to the right
                val cy = size.height * 0.08f
                val r = size.minDimension * 0.55f

                val glowA = when (mode) {
                    SkyMode.DAY -> 0.10f
                    SkyMode.DAWN -> 0.13f
                    SkyMode.DUSK -> 0.12f
                    else -> 0f
                }

                // Layered soft circles to simulate a gentle sun glow
                drawCircle(
                    color = Color(0xFFFFE2B8).copy(alpha = glowA),
                    radius = r,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color(0xFFFFC6A3).copy(alpha = glowA * 0.75f),
                    radius = r * 0.70f,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color(0xFFFFAFA0).copy(alpha = glowA * 0.45f),
                    radius = r * 0.48f,
                    center = Offset(cx, cy)
                )
            }
        }

        // Stars at night
        if (mode == SkyMode.NIGHT) {
            val seed = remember { (System.currentTimeMillis() / 1000L).toInt() }
            val stars = remember(seed) {
                val rng = Random(seed)
                List(90) {
                    Star(
                        x = rng.nextFloat(),
                        y = rng.nextFloat() * 0.55f,
                        r = 1.5f + rng.nextFloat() * 2.6f,
                        a = 0.25f + rng.nextFloat() * 0.55f
                    )
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                stars.forEach { s ->
                    drawCircle(
                        color = Color.White.copy(alpha = s.a),
                        radius = s.r,
                        center = Offset(size.width * s.x, size.height * s.y)
                    )
                }
            }
        }
    }
}

private data class Star(val x: Float, val y: Float, val r: Float, val a: Float)
