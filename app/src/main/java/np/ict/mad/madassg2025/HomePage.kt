package np.ict.mad.madassg2025

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
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

    // Callback to update the Compose UI text from fetchLocation()
    private var onLocationTextChanged: ((String) -> Unit)? = null

    // Launcher for the system location permission dialog
    private val locationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                fetchLocation()
            } else {
                onLocationTextChanged?.invoke("Permission denied.\nPlease allow location to use this feature.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationHelper = LocationHelper(applicationContext)
        val activity = this@HomePage

        setContent {
            var locationText by remember { mutableStateOf("Location not fetched yet") }
            var hasLocation by remember { mutableStateOf(false) }

            onLocationTextChanged = { newText ->
                locationText = newText
                // treat anything starting with "Location:" as a successful fetch
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

                        // Card-style box to show current location text
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

                        // Show button only until we get a valid location
                        if (!hasLocation) {
                            Button(
                                onClick = {
                                    val status = ContextCompat.checkSelfPermission(
                                        activity,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )

                                    if (status == PackageManager.PERMISSION_GRANTED) {
                                        // Already granted – no popup, just fetch
                                        activity.fetchLocation()
                                    } else {
                                        // Ask for permission – this shows the Android dialog
                                        activity.locationPermissionLauncher.launch(
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
        lifecycleScope.launch {
            onLocationTextChanged?.invoke("Getting location...")

            val location: Location? = locationHelper.getLastKnownLocation()

            if (location != null) {
                val lat = location.latitude
                val lon = location.longitude

                // 1️⃣ Try OneMap first for a Singapore-specific place name
                val oneMapName = try {
                    OneMapClient.reverseGeocode(lat, lon)
                } catch (e: Exception) {
                    null
                }

                // 2️⃣ Fallback to Android Geocoder if OneMap fails or returns nothing
                val geocoderName = if (oneMapName == null) {
                    val geocoder = Geocoder(this@HomePage, Locale("en", "SG"))
                    val addresses = try {
                        geocoder.getFromLocation(lat, lon, 1)
                    } catch (e: IOException) {
                        null
                    }

                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        listOfNotNull(
                            addr.subLocality,   // neighbourhood (e.g. Clementi, Holland)
                            addr.locality,      // usually "Singapore"
                            addr.subAdminArea,
                            addr.adminArea,
                            addr.countryName
                        ).firstOrNull()
                    } else {
                        null
                    }
                } else null

                val displayName = oneMapName ?: geocoderName ?: "Unknown location"

                // Show both the resolved name and raw coordinates for debugging
                val text = "Location: $displayName\n($lat, $lon)"
                onLocationTextChanged?.invoke(text)

                // 👉 Later: you can call a weather API here using lat & lon.

            } else {
                onLocationTextChanged?.invoke(
                    "Unable to get location.\nCheck that Location is ON and try again."
                )
            }
        }
    }
}
