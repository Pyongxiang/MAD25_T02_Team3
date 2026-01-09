package np.ict.mad.madassg2025

import android.Manifest
import android.content.pm.PackageManager
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

class HomePage : ComponentActivity() {

    private lateinit var locationHelper: LocationHelper

    private var onLocationTextChanged: ((String) -> Unit)? = null
    private var onWeatherUpdated: ((WeatherResponse) -> Unit)? = null
    private var onWeatherError: ((String) -> Unit)? = null

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) fetchLocationAndWeather()
            else onLocationTextChanged?.invoke("Permission denied.\nPlease allow location to use this feature.")
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

            var weatherCity by remember { mutableStateOf("—") }
            var weatherTemp by remember { mutableStateOf<Double?>(null) }
            var weatherCondition by remember { mutableStateOf<String?>(null) }
            var weatherError by remember { mutableStateOf<String?>(null) }
            var weatherIconRes by remember { mutableStateOf<Int?>(null) }

            onWeatherUpdated = { response ->
                weatherError = null
                weatherCity = response.name
                weatherTemp = response.main.temp
                weatherCondition = response.weather.firstOrNull()?.description
                weatherIconRes = pickWeatherIcon(weatherCondition)
            }

            onWeatherError = { msg ->
                weatherError = msg
                weatherTemp = null
                weatherCondition = null
                weatherIconRes = null
            }

            Surface(
                modifier = Modifier.fillMaxSize().background(Color(0xFF101820)),
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Current Weather",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelLarge
                            )

                            when {
                                weatherError != null -> Text(
                                    text = "Error: $weatherError",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                weatherTemp == null || weatherCondition == null -> Text(
                                    text = "No weather yet (tap Use My Location)",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                else -> {
                                    weatherIconRes?.let { resId ->
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Image(
                                            painter = painterResource(id = resId),
                                            contentDescription = "Weather icon",
                                            modifier = Modifier.size(72.dp)
                                        )
                                    }

                                    Text(
                                        text = weatherCity,
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "${weatherTemp!!.toInt()}°C",
                                        color = Color.White,
                                        style = MaterialTheme.typography.displaySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = weatherCondition!!.replaceFirstChar { it.uppercase() },
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .background(Color(0x331CFFFFFF))
                                .padding(vertical = 24.dp, horizontal = 20.dp)
                        ) {
                            Text(text = locationText, color = Color.White)
                        }

                        Button(
                            onClick = {
                                val status = ContextCompat.checkSelfPermission(
                                    this@HomePage,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                )
                                if (status == PackageManager.PERMISSION_GRANTED) {
                                    fetchLocationAndWeather()
                                } else {
                                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
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

    private fun fetchLocationAndWeather() {
        val status = ContextCompat.checkSelfPermission(
            this@HomePage,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (status != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        lifecycleScope.launch {
            onLocationTextChanged?.invoke("Getting fresh location...")

            val location: Location? = locationHelper.getFreshLocation()
            if (location == null) {
                onLocationTextChanged?.invoke("Unable to get location.\nTurn on Location and try again.")
                onWeatherError?.invoke("No location fix")
                return@launch
            }

            val lat = location.latitude
            val lon = location.longitude
            Log.d("HomePage", "Using coords lat=$lat lon=$lon")

            // 1) Fetch weather using SAME coords
            try {
                val weather = WeatherRepository.getCurrentWeather(lat, lon)
                onWeatherUpdated?.invoke(weather)

                // 2) Get a human place name (Geocoder). If fails, fallback to weather.name.
                val placeName = GeocoderHelper.reverseGeocode(applicationContext, lat, lon)
                    ?: weather.name
                    ?: "Unknown location"

                onLocationTextChanged?.invoke("Location: $placeName\n($lat, $lon)")
            } catch (e: Exception) {
                Log.e("HomePage", "Weather fetch failed: ${e.message}", e)
                onWeatherError?.invoke(e.message ?: "Weather error")

                // Still show coords even if weather fails
                val placeName = GeocoderHelper.reverseGeocode(applicationContext, lat, lon) ?: "Unknown location"
                onLocationTextChanged?.invoke("Location: $placeName\n($lat, $lon)")
            }
        }
    }
}

private fun pickWeatherIcon(description: String?): Int? {
    if (description == null) return null
    val lower = description.lowercase()
    return when {
        "rain" in lower || "shower" in lower -> R.drawable.ic_rainy
        "cloud" in lower || "overcast" in lower -> R.drawable.ic_cloudy
        "sun" in lower || "clear" in lower -> R.drawable.ic_sunny
        else -> R.drawable.ic_cloudy
    }
}
