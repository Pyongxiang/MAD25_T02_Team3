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

            var weatherCity by remember { mutableStateOf("—") }
            var weatherTemp by remember { mutableStateOf<Double?>(null) }
            var weatherCondition by remember { mutableStateOf<String?>(null) }
            var weatherError by remember { mutableStateOf<String?>(null) }
            var weatherIconRes by remember { mutableStateOf<Int?>(null) }

            onLocationTextChanged = { newText -> locationText = newText }

            onWeatherUpdated = { response ->
                weatherError = null
                weatherCity = response.name
                weatherTemp = response.main.temp

                val first = response.weather.firstOrNull()
                weatherCondition = first?.description
                weatherIconRes = pickWeatherIconById(first?.id)
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
                                    color = Color.White
                                )

                                weatherTemp == null || weatherCondition == null -> Text(
                                    text = "No weather yet (tap Use My Location)",
                                    color = Color.White
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
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
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
                onLocationTextChanged?.invoke("Location: Unknown\n(Unable to get location)")
                onWeatherError?.invoke("No location fix")
                return@launch
            }

            val lat = location.latitude
            val lon = location.longitude
            Log.d("HomePage", "Using coords lat=$lat lon=$lon")

            try {
                // Weather
                val weather = WeatherRepository.getCurrentWeather(lat, lon)
                onWeatherUpdated?.invoke(weather)

                // ✅ Better place name from OpenWeather reverse geocoding
                val placeName = WeatherRepository.getPlaceName(lat, lon) ?: "Unknown location"

                onLocationTextChanged?.invoke("Location: $placeName\n($lat, $lon)")
            } catch (e: Exception) {
                Log.e("HomePage", "Fetch failed: ${e.message}", e)
                onWeatherError?.invoke(e.message ?: "Error")
                onLocationTextChanged?.invoke("Location: Unknown location\n($lat, $lon)")
            }
        }
    }
}

private fun pickWeatherIconById(weatherId: Int?): Int {
    return when (weatherId) {
        in 200..232 -> R.drawable.ic_rainy
        in 300..531 -> R.drawable.ic_rainy
        in 600..622 -> R.drawable.ic_cloudy
        in 701..781 -> R.drawable.ic_cloudy
        800 -> R.drawable.ic_sunny
        in 801..804 -> R.drawable.ic_cloudy
        else -> R.drawable.ic_cloudy
    }
}
