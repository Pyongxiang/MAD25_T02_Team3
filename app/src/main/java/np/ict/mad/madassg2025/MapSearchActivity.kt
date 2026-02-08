package np.ict.mad.madassg2025

import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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

    private val firebaseHelper = FirebaseHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // force-close if not logged in
        val userId = firebaseHelper.getCurrentUser()?.uid
        if (userId == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            MaterialTheme {
                MapSearchScreen(
                    onBack = { finish() },
                    onSaveFavourite = save@{ name, lat, lon ->
                        val uid = firebaseHelper.getCurrentUser()?.uid
                        if (uid == null) {
                            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show()
                            return@save
                        }

                        firebaseHelper.addFavourite(
                            userId = uid,
                            name = name,
                            lat = lat,
                            lon = lon,
                            onSuccess = {
                                Toast.makeText(this, "Saved to favourites!", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { err ->
                                Log.e("MapSearch", "Save favourite failed: $err")
                                Toast.makeText(this, "Save failed: $err", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapSearchScreen(
    onBack: () -> Unit,
    onSaveFavourite: (name: String, lat: Double, lon: Double) -> Unit
) {
    val context = LocalContext.current
    val geocoder = remember { Geocoder(context, Locale("en", "SG")) }
    val scope = rememberCoroutineScope()

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
                } catch (_: Exception) {
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
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            // Search bar
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
                                    val q = searchQuery.trim()
                                    val sgQuery =
                                        if (q.contains("singapore", ignoreCase = true)) q else "$q, Singapore"

                                    val results = geocoder.getFromLocationName(sgQuery, 1)
                                    val addr = results?.firstOrNull()
                                    if (addr != null) LatLng(addr.latitude, addr.longitude) else null
                                } catch (_: Exception) {
                                    null
                                }
                            }

                            if (foundLatLng == null) {
                                error = "Place not found. Try a different keyword/postal code."
                            } else {
                                picked = foundLatLng
                                hasPicked = true
                                updatePickedName(foundLatLng)
                                cameraPositionState.position =
                                    CameraPosition.fromLatLngZoom(foundLatLng, 15f)
                            }

                            loading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Search & Move Map") }
            }

            // Map
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

            // Bottom info + action
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

                Text("Place: $pickedName", style = MaterialTheme.typography.bodyMedium)

                if (weatherLine != null) Text(weatherLine!!, style = MaterialTheme.typography.titleMedium)
                if (error != null) Text("Error: $error", color = MaterialTheme.colorScheme.error)

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
                                    weatherLine = "$pickedName: ${temp}°C • ${desc.replaceFirstChar { it.uppercase() }}"
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
                ) { Text(if (loading) "Loading..." else "Get Weather Here") }

                Button(
                    onClick = {
                        val nameToSave =
                            if (pickedName.isBlank() || pickedName == "Selected Location")
                                "Pinned (${ "%.3f".format(picked.latitude) }, ${ "%.3f".format(picked.longitude) })"
                            else pickedName

                        onSaveFavourite(nameToSave, picked.latitude, picked.longitude)
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add to Favourite Locations")
                }
            }
        }
    }
}

private fun bestPlaceLabel(a: Address): String {
    val candidates = listOfNotNull(
        a.subLocality,
        a.thoroughfare,
        a.locality,
        a.subAdminArea,
        a.adminArea,
        a.featureName
    )

    val cleaned = candidates
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .firstOrNull { s -> s.any { ch -> ch.isLetter() } }

    return cleaned ?: (a.getAddressLine(0) ?: "Unknown place")
}
