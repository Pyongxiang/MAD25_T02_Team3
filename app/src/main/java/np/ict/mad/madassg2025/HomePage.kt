package np.ict.mad.madassg2025

import android.Manifest
import android.location.Geocoder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale

class HomePage : ComponentActivity() {

    private lateinit var locationHelper: LocationHelper

    private val locationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (granted) {
                fetchLocation()
            } else {
                onLocationTextChanged?.invoke("Permission denied")
            }
        }

    private var onLocationTextChanged: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationHelper = LocationHelper(applicationContext)

        setContent {
            var locationText by remember { mutableStateOf("Location not fetched yet") }
            var hasLocation by remember { mutableStateOf(false) }

            onLocationTextChanged = { newText ->
                locationText = newText
                // When fetchLocation succeeds it sets "Location: <area>"
                hasLocation = newText.startsWith("Location:")
            }

            // Dark background (so text is NOT white on white)
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

                        // Card showing current location / status (Apple-ish style)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Color(0x331CFFFFFF), // translucent white
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .padding(vertical = 24.dp, horizontal = 20.dp)
                        ) {
                            Text(
                                text = locationText,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Only show the button while we don't have a resolved location yet
                        if (!hasLocation) {
                            Button(
                                onClick = {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
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

            val location = locationHelper.getLastKnownLocation()

            if (location != null) {
                val geocoder = Geocoder(this@HomePage, Locale.getDefault())
                val addresses = try {
                    geocoder.getFromLocation(location.latitude, location.longitude, 1)
                } catch (e: IOException) {
                    null
                }

                val areaName = if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    // Try neighbourhood, then town/city, then region, then country
                    listOfNotNull(
                        addr.subLocality,
                        addr.locality,
                        addr.adminArea,
                        addr.countryName
                    ).firstOrNull() ?: "Unknown location"
                } else {
                    "Unknown location"
                }

                val text = "Location: $areaName"
                onLocationTextChanged?.invoke(text)
            } else {
                onLocationTextChanged?.invoke(
                    "Unable to get location.\nCheck that Location is ON and try again."
                )
            }
        }
    }
}
