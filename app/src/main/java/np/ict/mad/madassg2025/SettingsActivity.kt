package np.ict.mad.madassg2025

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import np.ict.mad.madassg2025.settings.SettingsStore
import np.ict.mad.madassg2025.ui.home.SavedLocation
import org.json.JSONArray
import kotlin.math.abs
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState


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

    val alertHour by store.alertHourFlow.collectAsState(initial = 8)
    val alertMin by store.alertMinuteFlow.collectAsState(initial = 0)

    val savedLocations = remember { loadSavedLocationsForUser(context) }

    val bg = MaterialTheme.colorScheme.background
    val cardShape = RoundedCornerShape(16.dp)

    Surface(modifier = Modifier.fillMaxSize(), color = bg) {
        Column {
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bg)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {

                SettingsSectionTitle("Preferences")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(14.dp)) {

                        SettingRow(
                            label = "Units",
                            value = if (units == "F") "Fahrenheit (°F)" else "Celsius (°C)",
                            helper = "Changes temperature display across the app."
                        )

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = { scope.launch { store.setUnits("C") } },
                                border = if (units == "C")
                                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                else null
                            ) { Text("°C") }

                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = { scope.launch { store.setUnits("F") } },
                                border = if (units == "F")
                                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                else null
                            ) { Text("°F") }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                SettingsSectionTitle("Alerts")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(14.dp)) {

                        // Default location
                        val currentDefault = if (!defaultLat.isNaN() && !defaultLon.isNaN()) {
                            defaultName.ifBlank { "Default location" }
                        } else "Not set"

                        SettingRow(
                            label = "Default location",
                            value = currentDefault,
                            helper = "Used for daily rain checks."
                        )

                        Spacer(Modifier.height(10.dp))

                        if (savedLocations.isEmpty()) {
                            HintBox("No saved locations found. Add a favourite on Home first.")
                        } else {
                            Text(
                                text = "Choose from saved locations",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(8.dp))

                            // Keep UI clean: show first 6 only
                            savedLocations.take(6).forEach { loc ->
                                LocationPickRow(
                                    name = loc.name,
                                    isSelected =
                                        (!defaultLat.isNaN() && !defaultLon.isNaN()
                                                && approxEqual(defaultLat, loc.lat)
                                                && approxEqual(defaultLon, loc.lon)),
                                    onPick = {
                                        scope.launch {
                                            store.setDefaultLocation(loc.name, loc.lat, loc.lon)
                                            if (alertsEnabled) {
                                                onRequestNotificationPermission()
                                                WeatherAlertScheduler.scheduleDailyAt(context, alertHour, alertMin)
                                            }
                                        }
                                    }
                                )
                                Spacer(Modifier.height(8.dp))
                            }

                            if (savedLocations.size > 6) {
                                HintBox("Tip: you have more saved locations — the first few are shown here for a cleaner UI.")
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Divider()
                        Spacer(Modifier.height(14.dp))

                        // Alert time
                        SettingRow(
                            label = "Alert time",
                            value = "%02d:%02d".format(alertHour, alertMin),
                            helper = "Runs once a day near this time."
                        )

                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = "Hour",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))

                        ChipRow(
                            items = listOf(6, 7, 8, 9, 10, 11, 12, 18, 20, 22),
                            selected = alertHour,
                            label = { "%02d".format(it) },
                            onSelect = { h ->
                                scope.launch {
                                    store.setAlertTime(h, alertMin)
                                    if (alertsEnabled) {
                                        onRequestNotificationPermission()
                                        WeatherAlertScheduler.scheduleDailyAt(context, h, alertMin)
                                    }
                                }
                            }
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = "Minute",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))

                        ChipRow(
                            items = listOf(0, 15, 30, 45),
                            selected = alertMin,
                            label = { "%02d".format(it) },
                            onSelect = { m ->
                                scope.launch {
                                    store.setAlertTime(alertHour, m)
                                    if (alertsEnabled) {
                                        onRequestNotificationPermission()
                                        WeatherAlertScheduler.scheduleDailyAt(context, alertHour, m)
                                    }
                                }
                            }
                        )

                        Spacer(Modifier.height(14.dp))
                        Divider()
                        Spacer(Modifier.height(14.dp))

                        // Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Rain alerts", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Notifies you when rain is likely soon (based on your default location).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = alertsEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        store.setRainAlertsEnabled(enabled)
                                        if (enabled) {
                                            onRequestNotificationPermission()
                                            WeatherAlertScheduler.scheduleDailyAt(context, alertHour, alertMin)
                                        } else {
                                            WeatherAlertScheduler.cancel(context)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = { onBack() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done")
                }

                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun SettingRow(label: String, value: String, helper: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(
                helper,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HintBox(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChipRow(
    items: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEach { v ->
            val isSelected = v == selected
            Box(
                modifier = Modifier
                    .clickable { onSelect(v) }
                    .background(
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = label(v),
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LocationPickRow(
    name: String,
    isSelected: Boolean,
    onPick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
            if (isSelected) {
                Text(
                    "Selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun approxEqual(a: Double, b: Double): Boolean = abs(a - b) < 0.00001

private fun loadSavedLocationsForUser(context: Context): List<SavedLocation> {
    val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)

    val helper = FirebaseHelper()
    val uid = helper.getCurrentUser()?.uid
    val email = helper.getCurrentUserEmail()

    // Try multiple keys because different parts of the app might save under different user keys
    val possibleKeys = buildList {
        if (!uid.isNullOrBlank()) add("saved_locations_$uid")
        if (!email.isNullOrBlank()) add("saved_locations_$email")
        add("saved_locations_guest")

        // Common fallback/legacy keys (in case your HomePage used these)
        add("saved_locations")
        add("savedLocations")
        add("locations")
    }

    // Try each key until we find a valid list
    for (key in possibleKeys) {
        val raw = prefs.getString(key, null) ?: continue
        val parsed = parseSavedLocationsJson(raw)
        if (parsed.isNotEmpty()) return parsed
    }

    return emptyList()
}

private fun parseSavedLocationsJson(raw: String): List<SavedLocation> {
    return runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)

                val name = o.optString("name")
                    .ifBlank { o.optString("place") }
                    .ifBlank { o.optString("label") }
                    .ifBlank { "Saved place" }

                // Support multiple field naming conventions
                val lat = when {
                    o.has("lat") -> o.optDouble("lat", Double.NaN)
                    o.has("latitude") -> o.optDouble("latitude", Double.NaN)
                    else -> Double.NaN
                }

                val lon = when {
                    o.has("lon") -> o.optDouble("lon", Double.NaN)
                    o.has("lng") -> o.optDouble("lng", Double.NaN)
                    o.has("longitude") -> o.optDouble("longitude", Double.NaN)
                    else -> Double.NaN
                }

                if (!lat.isNaN() && !lon.isNaN()) {
                    add(SavedLocation(name = name, lat = lat, lon = lon))
                }
            }
        }
    }.getOrDefault(emptyList())
}

