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

    private var onLocationTextChanged: ((String) -> Unit)? = null
    private var weatherText by mutableStateOf("Weather not available")  // Declare weatherText here

    // Register for location permission request
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                fetchLocation() // Permission granted, fetch location
            } else {
                onLocationTextChanged?.invoke("Permission denied.\nPlease allow location to use this feature.")
                Log.d("LocationPermission", "Permission NOT granted") // Log permission denial
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationHelper = LocationHelper(applicationContext)

        setContent {
            var locationText by remember { mutableStateOf("Location not fetched yet") }
            var hasLocation by remember { mutableStateOf(false) }

            onLocationTextChanged = { newText ->
                locationText = newText
                hasLocation = newText.startsWith("Location:")
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

                        // Display weather text here
                        Text(
                            text = weatherText,
                            color = Color.White,
                            modifier = Modifier.padding(16.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        if (!hasLocation) {
                            Button(
                                onClick = {
                                    // Check if permission is granted before attempting to fetch location
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
        // Log permission status to check if permission is granted
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
                                addr.subLocality,   // neighbourhood
                                addr.locality,      // usually "Singapore"
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

                    // Fetch weather and update weatherText
                    val weatherInfo = try {
                        WeatherClient.getWeather(lat, lon)
                    } catch (e: Exception) {
                        Log.e("HomePage", "Error fetching weather data: ${e.message}")
                        null
                    }

                    weatherText = weatherInfo ?: "Weather data not available"

                } else {
                    onLocationTextChanged?.invoke(
                        "Unable to get location.\nCheck that Location is ON and try again."
                    )
                }
            }
        } else {
            // Log if permission is not granted
            Log.d("LocationPermission", "Permission NOT granted")
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}
