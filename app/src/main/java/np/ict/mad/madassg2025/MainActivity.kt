package np.ict.mad.madassg2025

import android.Manifest
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

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

            onLocationTextChanged = { newText ->
                locationText = newText
            }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF101820))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = locationText, color = Color.White)
                        Spacer(modifier = Modifier.height(24.dp))
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

    private fun fetchLocation() {
        lifecycleScope.launch {
            onLocationTextChanged?.invoke("Getting location...")
            val location = locationHelper.getLastKnownLocation()
            if (location != null) {
                val text = "Lat: ${location.latitude}\nLon: ${location.longitude}"
                onLocationTextChanged?.invoke(text)
            } else {
                onLocationTextChanged?.invoke("Unable to get location")
            }
        }
    }
}
