package np.ict.mad.madassg2025

import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MapSearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                MapSearchScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapSearchScreen(onBack: () -> Unit) {

    val context = LocalContext.current
    val geocoder = remember { Geocoder(context, Locale("en", "SG")) }
    val scope = rememberCoroutineScope()

    // Default to Singapore
    val defaultSG = LatLng(1.3521, 103.8198)

    var picked by remember { mutableStateOf(defaultSG) }
    var hasPicked by remember { mutableStateOf(false) }

    var pickedName by remember { mutableStateOf("Singapore") }

    var searchQuery by remember { mutableStateOf("") }

    var loading by remember { mutableStateOf(false) }
    var weatherLine by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultSG, 11f)
    }

    fun updatePickedName(latLng: LatLng) {
        scope.launch {
            val name = withContext(Dispatchers.IO) {
                try {
                    val list = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                    val addr = list?.firstOrNull()
                    if (addr != null) bestPlaceLabel(addr) else null
                } catch (e: Exception) {
                    null
                }
            }
            pickedName = name ?: "Selected Location"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Map Weather Search") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            // --- Search bar ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    label = { Text("Search a place (e.g. Clementi, 120450)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        if (searchQuery.isBlank()) return@Button

                        scope.launch {
                            loading = true
                            weatherLine = null
                            error = null

                            val foundLatLng = withContext(Dispatchers.IO) {
                                try {
                                    // ✅ Make vague searches work better by biasing to Singapore
                                    val q = searchQuery.trim()
                                    val sgQuery =
                                        if (q.contains("singapore", ignoreCase = true)) q else "$q, Singapore"

                                    val results = geocoder.getFromLocationName(sgQuery, 1)
                                    val addr = results?.firstOrNull()
                                    if (addr != null) LatLng(addr.latitude, addr.longitude) else null
                                } catch (e: Exception) {
                                    null
                                }
                            }

                            if (foundLatLng == null) {
                                error = "Place not found. Try a different keyword/postal code."
                                loading = false
                            } else {
                                picked = foundLatLng
                                hasPicked = true
                                updatePickedName(foundLatLng)

                                cameraPositionState.position =
                                    CameraPosition.fromLatLngZoom(foundLatLng, 15f)

                                loading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Search & Move Map")
                }
            }

            // --- Map area ---
            GoogleMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                cameraPositionState = cameraPositionState,
                onMapClick = { latLng ->
                    picked = latLng
                    hasPicked = true
                    weatherLine = null
                    error = null
                    updatePickedName(latLng)
                }
            ) {
                Marker(
                    state = MarkerState(position = picked),
                    title = if (hasPicked) pickedName else "Singapore"
                )
            }

            // --- Bottom info + action ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Picked: ${"%.5f".format(picked.latitude)}, ${"%.5f".format(picked.longitude)}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Place: $pickedName",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (weatherLine != null) {
                    Text(weatherLine!!, style = MaterialTheme.typography.titleMedium)
                }

                if (error != null) {
                    Text("Error: $error", color = MaterialTheme.colorScheme.error)
                }

                Button(
                    onClick = {
                        loading = true
                        weatherLine = null
                        error = null

                        scope.launch {
                            try {
                                val w = withContext(Dispatchers.IO) {
                                    WeatherRepository.getCurrentWeather(picked.latitude, picked.longitude)
                                }

                                if (w == null) {
                                    error = "Weather unavailable (API returned null)"
                                } else {
                                    val temp = w.main.temp.toInt()
                                    val desc = w.weather.firstOrNull()?.description ?: "—"
                                    weatherLine =
                                        "$pickedName: ${temp}°C • ${desc.replaceFirstChar { it.uppercase() }}"
                                }
                            } catch (e: Exception) {
                                error = e.message ?: "Unknown error"
                            } finally {
                                loading = false
                            }
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (loading) "Loading..." else "Get Weather Here")
                }
            }
        }
    }
}

/**
 * Fixes the "Place: 8" (e.g.) issue by choosing the best human-readable label
 * and ignoring labels that are only numbers.
 */
private fun bestPlaceLabel(a: Address): String {
    val candidates = listOfNotNull(
        a.subLocality,        // Bugis / Little India (sometimes)
        a.thoroughfare,       // Street name
        a.locality,           // Singapore (often)
        a.subAdminArea,
        a.adminArea,
        a.featureName         // Last resort (can be house number)
    )

    val cleaned = candidates
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .firstOrNull { s -> s.any { ch -> ch.isLetter() } } // must contain a letter

    return cleaned ?: (a.getAddressLine(0) ?: "Unknown place")
}
