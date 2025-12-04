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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    // this is only used INSIDE setContent
    private var weatherText by mutableStateOf("Weather not available")

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
            var locationText by remember { mutableStateOf("Location not fetched yet") }
            var hasLocation by remember { mutableStateOf(false) }

            // expose state holder to fetchLocation()
            onLocationTextChanged = { newText ->
                locationText = newText
                hasLocation = newText.startsWith("Location:")
            }

            // 🔹 Use YOUR Retrofit WeatherRepository here
            LaunchedEffect(Unit) {
                weatherText = "Loading weather..."
                try {
                    // Stage 1 requirement: use city name
                    val response = WeatherRepository.getCurrentWeather("Singapore")
                    val temp = response.main.temp
                    val desc = response.weather.firstOrNull()?.description ?: "No description"
                    weatherText = "Singapore: ${temp}°C, $desc"
                } catch (e: Exception) {
                    Log.e("HomePage", "Error loading weather", e)
                    weatherText = "Error loading weather: ${e.message ?: "Unknown error"}"
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {

                        // --- Location card ---
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x331CFFFFFF))
                                .padding(vertical = 24.dp, horizontal = 20.dp)
                        ) {
                            Text(
                                text = locationText,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // --- Current Weather (from WeatherRepository) ---
                        Text(
                            text = weatherText,
                            color = Color.White,
                            modifier = Modifier.padding(16.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

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

                    // 🔸 NOTE: we no longer call WeatherClient here.
                    // Your weather comes from WeatherRepository via city name.

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
