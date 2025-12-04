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

    // System permission dialog launcher
    private val locationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                fetchLocation()
            } else {
                onLocationTextChanged?.invoke("Permission denied")
            }
        }

    // Callback to update the Compose UI from fetchLocation()
    private var onLocationTextChanged: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationHelper = LocationHelper(applicationContext)
        val activity = this@HomePage

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
                    .background(Color(0xFF101820)), // dark background
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {

                        // Card-style area for the location text
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x331CFFFFFF)) // translucent white
                                .padding(vertical = 24.dp, horizontal = 20.dp)
                        ) {
                            Text(
                                text = locationText,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Show button only until we’ve successfully fetched a location
                        if (!hasLocation) {
                            Button(onClick = {
                                // 1. Check if permission is already granted
                                val permissionStatus =
                                    ContextCompat.checkSelfPermission(
                                        activity,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )

                                if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
                                    // No popup needed, just fetch
                                    activity.fetchLocation()
                                } else {
                                    // This triggers the Android system dialog
                                    activity.locationPermissionLauncher.launch(
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )
                                }
                            }) {
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

            val rawLocation = locationHelper.getLastKnownLocation()

            if (rawLocation != null) {
                val location: Location =
                    if (rawLocation.latitude in 37.0..38.0 &&
                        rawLocation.longitude in -123.0..-121.0
                    ) {
                        Location("fake-singapore").apply {
                            latitude = 1.3098   // near Ngee Ann Poly / Clementi area
                            longitude = 103.7783
                        }
                    } else {
                        rawLocation
                    }

                // 1️⃣ Try OneMap first for a Singapore-specific place name
                val oneMapName = try {
                    OneMapClient.reverseGeocode(location.latitude, location.longitude)
                } catch (e: Exception) {
                    null
                }

                // 2️⃣ Fallback: Android Geocoder (what you had before)
                val geocoderName = if (oneMapName == null) {
                    val geocoder = Geocoder(this@HomePage, Locale("en", "SG"))
                    val addresses = try {
                        geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    } catch (e: IOException) {
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
                        null
                    }
                } else null

                val displayName = oneMapName ?: geocoderName ?: "Unknown location"

                val text = "Location: $displayName"
                onLocationTextChanged?.invoke(text)

                // 👉 Later: here is where you would call a weather API
                // using location.latitude and location.longitude.
            } else {
                onLocationTextChanged?.invoke(
                    "Unable to get location.\nCheck that Location is ON and try again."
                )
            }
        }
    }

}
