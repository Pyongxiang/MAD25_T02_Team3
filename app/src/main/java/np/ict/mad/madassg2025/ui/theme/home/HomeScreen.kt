package np.ict.mad.madassg2025.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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

    val onSearchQueryChange: (String) -> Unit,
    val onPickSearchResult: (PlaceSuggestion) -> Unit,

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
            DynamicSkyBackground(modifier = Modifier.fillMaxSize(), mode = state.skyMode)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    // Header + unit toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

                        UnitsToggle(unit = state.unit, onChange = actions.onToggleUnit)
                    }
                }

                item {
                    // Top details + open forecast
                    TopDetailsAndForecast(
                        state = state,
                        onOpenForecast = actions.onOpenForecast
                    )
                }

                item {
                    // My location + save
                    MyLocationRow(
                        currentLabel = state.placeLabel.takeIf { it.isNotBlank() && it != "—" && it != "Loading…" },
                        canSave = !state.isLoading && state.lastLat != null && state.lastLon != null,
                        onAddCurrent = actions.onAddCurrent
                    )
                }

                item {
                    // Search + results
                    SearchSection(
                        state = state,
                        onQueryChange = actions.onSearchQueryChange,
                        onPick = actions.onPickSearchResult
                    )
                }

                if (state.savedLocations.isNotEmpty()) {
                    item {
                        Text("Favourites", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                    }

                    items(state.savedLocations, key = { favKey(it) }) { loc ->
                        val key = favKey(loc)
                        val mini = state.favouritesMini[key]

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { actions.onSelectSaved(loc) },
                                    onLongClick = { actions.onRemoveSaved(loc) }
                                ),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f))
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = loc.name,
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = when {
                                            mini == null -> "Loading…"
                                            mini.isLoading -> "Loading…"
                                            mini.desc.isNullOrBlank() -> "—"
                                            else -> mini.desc.replaceFirstChar { it.uppercase() }
                                        },
                                        color = Color.White.copy(alpha = 0.70f),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(pickWeatherEmojiById(mini?.weatherId), style = MaterialTheme.typography.titleLarge)
                                    val tempText = when {
                                        mini == null || mini.isLoading || mini.tempC == null -> "—"
                                        state.unit == UnitPref.C -> "${mini.tempC.roundToInt()}°"
                                        else -> "${(mini.tempC * 9.0 / 5.0 + 32.0).roundToInt()}°"
                                    }
                                    Text(
                                        tempText,
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            "Tip: long-press a card to remove",
                            color = Color.White.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                item {
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
}

@Composable
private fun TopDetailsAndForecast(state: HomeUiState, onOpenForecast: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                Text(
                    text = "Updated $timeStr",
                    color = Color.White.copy(alpha = 0.70f),
                    style = MaterialTheme.typography.bodySmall
                )

                if (!state.isLoading && state.sunriseUtc != null && state.sunsetUtc != null && state.tzOffsetSec != null) {
                    val sunrise = formatLocalTime(state.sunriseUtc, state.tzOffsetSec)
                    val sunset = formatLocalTime(state.sunsetUtc, state.tzOffsetSec)
                    Text("🌅 Sunrise $sunrise", color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodyMedium)
                    Text("🌇 Sunset $sunset", color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("Sunrise/Sunset: —", color = Color.White.copy(alpha = 0.70f), style = MaterialTheme.typography.bodySmall)
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

            Card(
                modifier = Modifier.clickable(enabled = state.canOpenForecast) { onOpenForecast() },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f))
            ) {
                Text(
                    text = "View 7-day",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    color = Color.White.copy(alpha = if (state.canOpenForecast) 0.90f else 0.45f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SearchSection(
    state: HomeUiState,
    onQueryChange: (String) -> Unit,
    onPick: (PlaceSuggestion) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search a place (e.g. Holland Village)", color = Color.White.copy(alpha = 0.55f)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White.copy(alpha = 0.25f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
                focusedContainerColor = Color.White.copy(alpha = 0.08f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.08f)
            )
        )

        if (state.searchLoading) {
            Text("Searching…", color = Color.White.copy(alpha = 0.70f), style = MaterialTheme.typography.bodySmall)
        } else if (state.searchError != null) {
            Text("Search error: ${state.searchError}", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
        }

        state.searchResults.forEach { r ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(r) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("＋", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = r.displayLabel,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "(${r.lat}, ${r.lon})",
                            color = Color.White.copy(alpha = 0.60f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MyLocationRow(currentLabel: String?, canSave: Boolean, onAddCurrent: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
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
                modifier = Modifier.clickable(enabled = canSave) { onAddCurrent() },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f))
            ) {
                Text(
                    text = "＋ Save",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = Color.White.copy(alpha = if (canSave) 0.85f else 0.45f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
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
        else -> "☁️"
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
private fun DynamicSkyBackground(modifier: Modifier, mode: SkyMode) {
    val base = when (mode) {
        SkyMode.NIGHT -> Brush.verticalGradient(listOf(Color(0xFF07101E), Color(0xFF0B1730), Color(0xFF0F2240)))
        SkyMode.DAWN -> Brush.verticalGradient(listOf(Color(0xFF0B1530), Color(0xFF2E3A70), Color(0xFF6B5A78)))
        SkyMode.DAY -> Brush.verticalGradient(listOf(Color(0xFF0B2447), Color(0xFF0E2E5A), Color(0xFF123667)))
        SkyMode.DUSK -> Brush.verticalGradient(listOf(Color(0xFF0B1530), Color(0xFF2D2A60), Color(0xFF5A3A66)))
    }

    Box(modifier = modifier.background(base)) {
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

@Composable
private fun UnitsToggle(unit: UnitPref, onChange: (UnitPref) -> Unit) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f))) {
        Row(
            modifier = Modifier.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            UnitChip("°C", unit == UnitPref.C) { onChange(UnitPref.C) }
            UnitChip("°F", unit == UnitPref.F) { onChange(UnitPref.F) }
        }
    }
}

@Composable
private fun UnitChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent)
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
