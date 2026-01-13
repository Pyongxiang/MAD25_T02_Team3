package np.ict.mad.madassg2025

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import np.ict.mad.madassg2025.ui.home.SavedLocation
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

class LocationsActivity : ComponentActivity() {

    private val firebaseHelper = FirebaseHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userKey = buildUserKey(firebaseHelper)

        setContent {
            val bg = Brush.verticalGradient(
                listOf(Color(0xFF07101E), Color(0xFF0B1730), Color(0xFF0F2240))
            )

            var saved by remember { mutableStateOf(loadSavedLocations(userKey)) }

            // key -> mini weather (nullable while loading)
            val miniMap = remember { mutableStateMapOf<String, MiniWeather?>() }

            LaunchedEffect(saved) {
                // Load mini weather for each saved location
                saved.forEach { loc ->
                    val key = locKey(loc)
                    if (!miniMap.containsKey(key)) {
                        miniMap[key] = null // mark as loading
                        val mini = withContext(Dispatchers.IO) {
                            runCatching {
                                val w = WeatherRepository.getCurrentWeather(loc.lat, loc.lon)
                                if (w == null) null
                                else MiniWeather(
                                    tempC = w.main.temp,
                                    desc = w.weather.firstOrNull()?.description ?: "",
                                    weatherId = w.weather.firstOrNull()?.id
                                )
                            }.getOrNull()
                        }
                        miniMap[key] = mini
                    }
                }
            }

            Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                Box(modifier = Modifier.fillMaxSize().background(bg)) {
                    val scroll = rememberScrollState()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp)
                            .verticalScroll(scroll),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Locations",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tap a card to view • Long-press to remove",
                            color = Color.White.copy(alpha = 0.70f),
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (saved.isEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "No saved locations yet.\nGo back and tap “+ Save”.",
                                color = Color.White.copy(alpha = 0.80f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        saved.forEach { loc ->
                            val key = locKey(loc)
                            val mini = miniMap[key]

                            locationStackCard(
                                name = loc.name,
                                mini = mini,
                                onClick = {
                                    startActivity(
                                        Intent(this@LocationsActivity, ForecastActivity::class.java).apply {
                                            putExtra("lat", loc.lat)
                                            putExtra("lon", loc.lon)
                                            putExtra("place", loc.name)
                                        }
                                    )
                                },
                                onLongPress = {
                                    val updated = saved.filterNot {
                                        it.name == loc.name && it.lat == loc.lat && it.lon == loc.lon
                                    }
                                    saved = updated
                                    saveSavedLocations(userKey, updated)
                                    miniMap.remove(key)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ---------- storage ----------
    private fun prefs() = getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)

    private fun buildUserKey(firebaseHelper: FirebaseHelper): String {
        val uid = firebaseHelper.getCurrentUser()?.uid
        val email = firebaseHelper.getCurrentUserEmail()
        return uid ?: email ?: "guest"
    }

    private fun loadSavedLocations(userKey: String): List<SavedLocation> {
        val raw = prefs().getString("saved_locations_$userKey", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        SavedLocation(
                            name = o.getString("name"),
                            lat = o.getDouble("lat"),
                            lon = o.getDouble("lon")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveSavedLocations(userKey: String, list: List<SavedLocation>) {
        val arr = JSONArray()
        list.forEach { loc ->
            val o = JSONObject()
            o.put("name", loc.name)
            o.put("lat", loc.lat)
            o.put("lon", loc.lon)
            arr.put(o)
        }
        prefs().edit().putString("saved_locations_$userKey", arr.toString()).apply()
    }
}

// ---------- helpers & UI (top-level) ----------

private data class MiniWeather(
    val tempC: Double,
    val desc: String,
    val weatherId: Int?
)

private fun locKey(loc: SavedLocation): String = "${loc.name}|${loc.lat}|${loc.lon}"

@Composable
private fun locationStackCard(
    name: String,
    mini: MiniWeather?,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when {
                        mini == null -> "Loading…"
                        mini.desc.isBlank() -> "—"
                        else -> mini.desc.replaceFirstChar { it.uppercase() }
                    },
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = pickEmoji(mini?.weatherId),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = mini?.let { "${it.tempC.roundToInt()}°" } ?: "—",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun pickEmoji(weatherId: Int?): String {
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
