package np.ict.mad.madassg2025

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import np.ict.mad.madassg2025.settings.SettingsStore
import np.ict.mad.madassg2025.ui.home.SavedLocation
import org.json.JSONArray

class SettingsActivity : ComponentActivity() {

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createWeatherChannel(this)

        setContent {
            MaterialTheme {
                SettingsScreen(
                    context = this,
                    onBack = { finish() },
                    onRequestNotificationPermission = { requestPostNotificationsIfNeeded() }
                )
            }
        }
    }

    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            if (!granted) requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    context: Context,
    onBack: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val store = remember { SettingsStore(context) }

    val units by store.unitsFlow.collectAsState(initial = "C")
    val alertsEnabled by store.rainAlertsFlow.collectAsState(initial = false)

    val defaultName by store.defaultNameFlow.collectAsState(initial = "")
    val defaultLat by store.defaultLatFlow.collectAsState(initial = Double.NaN)
    val defaultLon by store.defaultLonFlow.collectAsState(initial = Double.NaN)

    val savedLocations = remember { loadSavedLocationsForUser(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                Row(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable { onBack() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        Column(Modifier.padding(16.dp)) {

            SettingCard(title = "Units") {
                DropdownSetting(
                    current = if (units == "F") "Fahrenheit (°F)" else "Celsius (°C)",
                    options = listOf("Celsius (°C)", "Fahrenheit (°F)"),
                    onSelect = { label ->
                        scope.launch { store.setUnits(if (label.startsWith("F")) "F" else "C") }
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            SettingCard(title = "Default location for alerts") {
                val currentDefault = if (!defaultLat.isNaN() && !defaultLon.isNaN()) {
                    "${defaultName.ifBlank { "Default location" }}  (%.4f, %.4f)".format(defaultLat, defaultLon)
                } else "Not set"

                Text(currentDefault)
                Spacer(Modifier.height(8.dp))

                if (savedLocations.isEmpty()) {
                    Text("No saved locations found. Add a favourite on Home first.")
                } else {
                    DropdownSetting(
                        current = "Choose from saved locations",
                        options = savedLocations.map { it.name },
                        onSelect = { name ->
                            val chosen = savedLocations.firstOrNull { it.name == name }
                            if (chosen != null) {
                                scope.launch { store.setDefaultLocation(chosen.name, chosen.lat, chosen.lon) }
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            SettingCard(title = "Rain alerts") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable rain reminders")
                    Switch(
                        checked = alertsEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                store.setRainAlertsEnabled(enabled)
                                if (enabled) {
                                    onRequestNotificationPermission()
                                    WeatherAlertScheduler.schedule(context)
                                } else {
                                    WeatherAlertScheduler.cancel(context)
                                }
                            }
                        }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("Checks the forecast periodically and notifies you if rain is likely soon.")
            }

            Spacer(Modifier.height(18.dp))

            Button(onClick = { onBack() }, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun DropdownSetting(
    current: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true },
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(current)
        }
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        options.forEach { opt ->
            DropdownMenuItem(
                text = { Text(opt) },
                onClick = {
                    expanded = false
                    onSelect(opt)
                }
            )
        }
    }
}

private fun loadSavedLocationsForUser(context: Context): List<SavedLocation> {
    val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)

    // Match HomePage's user key behaviour
    val helper = FirebaseHelper()
    val uid = helper.getCurrentUser()?.uid
    val email = helper.getCurrentUserEmail()
    val userKey = uid ?: email ?: "guest"

    val raw = prefs.getString("saved_locations_$userKey", null) ?: return emptyList()

    return runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    SavedLocation(
                        name = o.optString("name", "Saved place"),
                        lat = o.optDouble("lat", Double.NaN),
                        lon = o.optDouble("lon", Double.NaN)
                    )
                )
            }
        }.filter { !it.lat.isNaN() && !it.lon.isNaN() }
    }.getOrDefault(emptyList())
}
