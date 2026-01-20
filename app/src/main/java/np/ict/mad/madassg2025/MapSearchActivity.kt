package np.ict.mad.madassg2025

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.google.android.gms.maps.model.CameraPosition


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

    // Default to Singapore if user hasn't picked anything yet
    val defaultSG = LatLng(1.3521, 103.8198)

    var picked by remember { mutableStateOf(defaultSG) }
    var hasPicked by remember { mutableStateOf(false) }

    var loading by remember { mutableStateOf(false) }
    var weatherLine by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultSG, 11f)
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
                }
            ) {
                // Marker where user tapped
                Marker(
                    state = MarkerState(position = picked),
                    title = if (hasPicked) "Selected Location" else "Singapore"
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
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (loading) "Loading..." else "Get Weather Here")
                }

                // Fetch weather when loading becomes true
                LaunchedEffect(loading) {
                    if (!loading) return@LaunchedEffect
                    try {
                        val w = WeatherRepository.getCurrentWeather(picked.latitude, picked.longitude)
                        if (w == null) {
                            error = "Weather unavailable (API returned null)"
                        } else {
                            val city = w.name
                            val temp = w.main.temp.toInt()
                            val desc = w.weather.firstOrNull()?.description ?: "—"
                            weatherLine = "$city: ${temp}°C • ${desc.replaceFirstChar { it.uppercase() }}"
                        }
                    } catch (e: Exception) {
                        error = e.message ?: "Unknown error"
                    } finally {
                        loading = false
                    }
                }
            }
        }
    }
}
