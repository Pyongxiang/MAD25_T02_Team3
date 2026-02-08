package np.ict.mad.madassg2025

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import np.ict.mad.madassg2025.alerts.WeatherNotifier
import np.ict.mad.madassg2025.settings.AlertFrequency
import np.ict.mad.madassg2025.settings.SettingsStore
import np.ict.mad.madassg2025.ui.home.SavedLocation
import org.json.JSONArray
import kotlin.math.abs

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

    val freqName by store.alertFrequencyFlow.collectAsState(initial = AlertFrequency.DAILY.name)
    val alertFrequency = AlertFrequency.fromStored(freqName)

    // ✅ NEW: Saved locations now comes from Firestore when logged in, else SharedPreferences.
    var savedLocations by remember { mutableStateOf<List<SavedLocation>>(emptyList()) }
    var favListener by remember { mutableStateOf<ListenerRegistration?>(null) }

    val helper = remember { FirebaseHelper() }
    val userId = helper.getCurrentUser()?.uid

    // Start loading favourites depending on login state
    LaunchedEffect(userId) {
        if (userId == null) {
            // Guest -> local
            savedLocations = loadSavedLocationsFromPrefs(context)
        } else {
            // Logged in -> Firestore realtime
            favListener?.remove()
            favListener = helper.listenToFavourites(
                userId = userId,
                onUpdate = { docs ->
                    val list = docs.mapNotNull { d ->
                        val name = d["name"] as? String ?: return@mapNotNull null
                        val lat = (d["lat"] as? Number)?.toDouble() ?: return@mapNotNull null
                        val lon = (d["lon"] as? Number)?.toDouble() ?: return@mapNotNull null
                        SavedLocation(name = name, lat = lat, lon = lon)
                    }
                    savedLocations = list
                },
                onFailure = { err ->
                    // If Firestore fails, fall back to local so app still works
                    savedLocations = loadSavedLocationsFromPrefs(context)
                    Toast.makeText(context, "Failed to load favourites: $err", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    // Clean up Firestore listener
    DisposableEffect(Unit) {
        onDispose {
            favListener?.remove()
            favListener = null
        }
    }

    val bg = MaterialTheme.colorScheme.background
    val cardShape = RoundedCornerShape(16.dp)

    fun rescheduleIfEnabled() {
        scope.launch {
            if (store.isRainAlertsEnabled()) {
                onRequestNotificationPermission()
                val f = store.getAlertFrequency()
                WeatherAlertScheduler.schedule(context, f)
            }
        }
    }

    fun canPostNotificationsNow(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
    }

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

                        val currentDefault = if (!defaultLat.isNaN() && !defaultLon.isNaN()) {
                            defaultName.ifBlank { "Default location" }
                        } else "Not set"

                        SettingRow(
                            label = "Default location",
                            value = currentDefault,
                            helper = "Used for rain checks."
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
                                        }
                                        rescheduleIfEnabled()
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

                        SettingRow(
                            label = "Alert frequency",
                            value = alertFrequency.label,
                            helper = "How often the app checks for rain and notifies you."
                        )

                        Spacer(Modifier.height(10.dp))

                        FrequencyChips(
                            selected = alertFrequency,
                            onPick = { picked ->
                                scope.launch { store.setAlertFrequency(picked) }
                                rescheduleIfEnabled()
                            }
                        )

                        Spacer(Modifier.height(14.dp))
                        Divider()
                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Rain alerts", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Shows a notification when rain is likely (based on your default location).",
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
                                            val f = store.getAlertFrequency()
                                            WeatherAlertScheduler.schedule(context, f)
                                        } else {
                                            WeatherAlertScheduler.cancel(context)
                                        }
                                    }
                                }
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Button(
                            onClick = {
                                onRequestNotificationPermission()

                                if (!canPostNotificationsNow()) {
                                    Toast.makeText(
                                        context,
                                        "Notifications are blocked. Enable them in App Settings to see the test alert.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@Button
                                }

                                if (defaultLat.isNaN() || defaultLon.isNaN()) {
                                    Toast.makeText(
                                        context,
                                        "Set a default location first.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@Button
                                }

                                val name = if (defaultName.isBlank()) "Default location" else defaultName

                                scope.launch {
                                    val result =
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            WeatherForecast().getHourly24AndDaily5(defaultLat, defaultLon)
                                        }

                                    if (result == null || result.hourlyNext24.isEmpty()) {
                                        Toast.makeText(
                                            context,
                                            "Could not fetch weather right now.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@launch
                                    }

                                    val now = result.hourlyNext24.first()
                                    val msg =
                                        "$name: ${now.tempC}°C, ${now.description} • POP ${now.popPct}% • Rain ${"%.1f".format(now.rainMm)}mm"

                                    WeatherNotifier.showTestAlert(
                                        context = context,
                                        title = "Weather now",
                                        message = msg
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Send Test Alert")
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
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(
                helper,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = value,
            modifier = Modifier.widthIn(max = 170.dp),
            textAlign = TextAlign.End,
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
        Text(text, style = MaterialTheme.typography.bodySmall)
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
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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

@Composable
private fun FrequencyChips(
    selected: AlertFrequency,
    onPick: (AlertFrequency) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            FreqChip(AlertFrequency.DAILY, selected, onPick, Modifier.weight(1f))
            FreqChip(AlertFrequency.HOURLY, selected, onPick, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            FreqChip(AlertFrequency.SIX_HOURLY, selected, onPick, Modifier.weight(1f))
            FreqChip(AlertFrequency.FIFTEEN_MIN, selected, onPick, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FreqChip(
    freq: AlertFrequency,
    selected: AlertFrequency,
    onPick: (AlertFrequency) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = freq == selected
    OutlinedButton(
        onClick = { onPick(freq) },
        modifier = modifier,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Text(freq.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun approxEqual(a: Double, b: Double): Boolean = abs(a - b) < 0.00001

// ✅ Guest fallback (SharedPreferences). This matches your existing storage format.
private fun loadSavedLocationsFromPrefs(context: Context): List<SavedLocation> {
    val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)

    val helper = FirebaseHelper()
    val uid = helper.getCurrentUser()?.uid
    val email = helper.getCurrentUserEmail()

    val possibleKeys = buildList {
        if (!uid.isNullOrBlank()) add("saved_locations_$uid")
        if (!email.isNullOrBlank()) add("saved_locations_$email")
        add("saved_locations_guest")
        add("saved_locations")
        add("savedLocations")
        add("locations")
    }

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
