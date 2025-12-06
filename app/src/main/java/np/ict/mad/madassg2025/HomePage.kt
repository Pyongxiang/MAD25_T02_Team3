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
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale
import kotlin.math.abs

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
            var weatherIconRes by remember { mutableStateOf<Int?>(null) }

            // Call your Retrofit WeatherRepository once when screen loads
            LaunchedEffect(Unit) {
                weatherError = null
                weatherTemp = null
                weatherCondition = null
                weatherIconRes = null

                try {
                    val response = WeatherRepository.getCurrentWeather("Singapore")
                    weatherCity = response.name
                    weatherTemp = response.main.temp
                    weatherCondition = response.weather.firstOrNull()?.description
                    weatherIconRes = pickWeatherIcon(weatherCondition)
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
                                    // Optional icon (multimedia – your feature)
                                    weatherIconRes?.let { resId ->
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Image(
                                            painter = painterResource(id = resId),
                                            contentDescription = "Weather icon",
                                            modifier = Modifier.size(72.dp)
                                        )
                                    }

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
        lifecycleScope.launch {
            onLocationTextChanged?.invoke("Getting location...")

            val location = locationHelper.getLastKnownLocation()

            if (location != null) {
                val lat = location.latitude
                val lon = location.longitude
                Log.d("HomePage", "Got location: $lat, $lon")

                // Use SG locale so Geocoder prefers Singapore naming
                val geocoder = Geocoder(this@HomePage, Locale("en", "SG"))
                val addresses = try {
                    geocoder.getFromLocation(lat, lon, 1)
                } catch (e: IOException) {
                    Log.e("HomePage", "Geocoder error: ${e.message}")
                    null
                }

                // First, whatever Geocoder gives us
                val rawName: String? = if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    listOfNotNull(
                        addr.subLocality,   // e.g. "Clementi", "Bukit Timah"
                        addr.locality,      // usually "Singapore"
                        addr.subAdminArea,
                        addr.adminArea,
                        addr.countryName
                    ).firstOrNull()
                } else {
                    null
                }

                // Then clean it up / override
                val areaName = when {
                    // If coords are near Ngee Ann Poly, force that name
                    abs(lat - 1.3323) < 0.01 && abs(lon - 103.7768) < 0.01 ->
                        "Ngee Ann Polytechnic"

                    // If Geocoder returned something meaningful and not "NIL"
                    rawName != null && !rawName.equals("NIL", ignoreCase = true) ->
                        rawName

                    else -> "Unknown location"
                }

                val text = "Location: $areaName\n($lat, $lon)"
                onLocationTextChanged?.invoke(text)

            } else {
                onLocationTextChanged?.invoke(
                    "Unable to get location.\nCheck that Location is ON and try again."
                )
            }
        }
    }
}
/**
 * Maps the OpenWeather description to one of your local icons.
 */
private fun pickWeatherIcon(description: String?): Int? {
    if (description == null) return null
    val lower = description.lowercase()

    return when {
        "rain" in lower || "shower" in lower -> R.drawable.ic_rainy
        "cloud" in lower || "overcast" in lower -> R.drawable.ic_cloudy
        "sun" in lower || "clear" in lower -> R.drawable.ic_sunny
        else -> R.drawable.ic_cloudy // fallback
    }
}
