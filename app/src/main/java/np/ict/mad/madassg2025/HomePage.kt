package np.ict.mad.madassg2025

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale

class HomePage : ComponentActivity() {

    private lateinit var locationHelper: LocationHelper

    // callback to update the location text from fetchLocation()
    private var onLocationTextChanged: ((String) -> Unit)? = null

    // Register for location permission request
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                fetchLocation() // Permission granted, fetch location
            } else {
                onLocationTextChanged?.invoke(
                    "Permission denied.\nPlease allow location to use this feature."
                )
                Log.d("LocationPermission", "Permission NOT granted")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationHelper = LocationHelper(applicationContext)

        setContent {
            // --------- Location state (friend's feature) ----------
            var locationText by remember { mutableStateOf("Location not fetched yet") }
            var hasLocation by remember { mutableStateOf(false) }

            onLocationTextChanged = { newText ->
                locationText = newText
                hasLocation = newText.startsWith("Location:")
            }

            // --------- Current Weather state (your feature) ----------
            var weatherCity by remember { mutableStateOf("Singapore") }
            var weatherTemp by remember { mutableStateOf<Double?>(null) }
            var weatherCondition by remember { mutableStateOf<String?>(null) }
            var weatherError by remember { mutableStateOf<String?>(null) }

            // Call your Retrofit WeatherRepository once when screen loads
            LaunchedEffect(Unit) {
                weatherError = null
                weatherTemp = null
                weatherCondition = null

                try {
                    val response = WeatherRepository.getCurrentWeather("Singapore")
                    weatherCity = response.name
                    weatherTemp = response.main.temp
                    weatherCondition = response.weather.firstOrNull()?.description
                } catch (e: Exception) {
                    Log.e("HomePage", "Error loading weather", e)
                    weatherError = e.message ?: "Unknown error"
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF101820)),
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {

                        // -------- Current Weather section (now at the top) --------
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Current Weather",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelLarge
                            )

                            when {
                                weatherError != null -> {
                                    Text(
                                        text = "Error loading weather: $weatherError",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                weatherTemp == null || weatherCondition == null -> {
                                    Text(
                                        text = "Loading weather...",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                else -> {
                                    // City name
                                    Text(
                                        text = weatherCity,
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    // Big temperature
                                    Text(
                                        text = "${weatherTemp!!.toInt()}°C",
                                        color = Color.White,
                                        style = MaterialTheme.typography.displaySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    // Condition (e.g. "light rain" → "Light rain")
                                    Text(
                                        text = weatherCondition!!
                                            .replaceFirstChar { it.uppercase() },
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        // -------- Location card (now below weather) --------
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x331CFFFFFF))
                                .padding(vertical = 24.dp, horizontal = 20.dp)
                        ) {
                            Text(
                                text = locationText,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // -------- Use My Location button --------
                        if (!hasLocation) {
                            Button(
                                onClick = {
                                    val status = ContextCompat.checkSelfPermission(
                                        this@HomePage,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )

                                    if (status == PackageManager.PERMISSION_GRANTED) {
                                        fetchLocation()
                                    } else {
                                        locationPermissionLauncher.launch(
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        )
                                    }
                                }
                            ) {
                                Text("Use My Location")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun fetchLocation() {
        val status = ContextCompat.checkSelfPermission(
            this@HomePage,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (status == PackageManager.PERMISSION_GRANTED) {
            Log.d("LocationPermission", "Permission granted, fetching location")
            lifecycleScope.launch {
                onLocationTextChanged?.invoke("Getting location...")

                val location: Location? = locationHelper.getLastKnownLocation()

                if (location != null) {
                    val lat = location.latitude
                    val lon = location.longitude

                    // Try OneMap for a SG-specific place name
                    val oneMapName = try {
                        OneMapClient.reverseGeocode(lat, lon)
                    } catch (e: Exception) {
                        Log.e("HomePage", "Error fetching location name from OneMap: ${e.message}")
                        null
                    }

                    // Fallback to Geocoder if OneMap fails
                    val geocoderName = if (oneMapName == null) {
                        val geocoder = Geocoder(this@HomePage, Locale("en", "SG"))
                        val addresses = try {
                            geocoder.getFromLocation(lat, lon, 1)
                        } catch (e: IOException) {
                            Log.e("HomePage", "Error fetching location from Geocoder: ${e.message}")
                            null
                        }

                        if (!addresses.isNullOrEmpty()) {
                            val addr = addresses[0]
                            listOfNotNull(
                                addr.subLocality,
                                addr.locality,
                                addr.subAdminArea,
                                addr.adminArea,
                                addr.countryName
                            ).firstOrNull()
                        } else {
                            "Unable to resolve location"
                        }
                    } else oneMapName

                    val displayName = geocoderName ?: "Unknown location"
                    val locationText = "Location: $displayName\n($lat, $lon)"
                    onLocationTextChanged?.invoke(locationText)

                    // Weather still comes from WeatherRepository via city name (above)

                } else {
                    onLocationTextChanged?.invoke(
                        "Unable to get location.\nCheck that Location is ON and try again."
                    )
                }
            }
        } else {
            Log.d("LocationPermission", "Permission NOT granted")
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}
