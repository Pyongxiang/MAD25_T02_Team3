package np.ict.mad.madassg2025

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class HomePage : ComponentActivity() {

    private lateinit var locationHelper: LocationHelper

    private var onLocationTextChanged: ((String) -> Unit)? = null
    private var onWeatherUpdated: ((WeatherResponse) -> Unit)? = null
    private var onWeatherError: ((String) -> Unit)? = null
    private var onCoordsUpdated: ((Double, Double) -> Unit)? = null

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

            // Store last successful coordinates so the "My Location" card can open forecast screen
            var lastLat by remember { mutableStateOf<Double?>(null) }
            var lastLon by remember { mutableStateOf<Double?>(null) }

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

            onCoordsUpdated = { lat, lon ->
                lastLat = lat
                lastLon = lon
            }

            val bg = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0B1220),
                    Color(0xFF101B2D),
                    Color(0xFF1A2A45)
                )
            )

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg),
                color = Color.Transparent
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header / hero section (Apple-like)
                    Column(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "My Location",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = weatherCity,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        when {
                            weatherError != null -> Text(
                                text = "Error: $weatherError",
                                color = Color.White.copy(alpha = 0.9f)
                            )

                            weatherTemp == null || weatherCondition == null -> Text(
                                text = "Tap Use My Location to load weather",
                                color = Color.White.copy(alpha = 0.75f)
                            )

                            else -> {
                                Text(
                                    text = "${weatherTemp!!.toInt()}°",
                                    color = Color.White,
                                    style = MaterialTheme.typography.displayLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = weatherCondition!!.replaceFirstChar { it.uppercase() },
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }

                    // Clickable "My Location" card -> opens 7-day forecast screen
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = (lastLat != null && lastLon != null)) {
                                val intent = Intent(this@HomePage, ForecastActivity::class.java).apply {
                                    putExtra("lat", lastLat!!)
                                    putExtra("lon", lastLon!!)
                                    putExtra("place", weatherCity)
                                }
                                startActivity(intent)
                            },
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.10f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Forecast (7 days)",
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.titleSmall
                            )

                            Text(
                                text = if (lastLat == null) "Load location to view forecast"
                                else "Tap to view the next 7 days",
                                color = Color.White.copy(alpha = 0.75f)
                            )

                            // Current weather icon (optional, keeps your existing icons)
                            if (weatherIconRes != null && weatherTemp != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = weatherIconRes!!),
                                        contentDescription = "Weather icon",
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Text(
                                        text = "${weatherTemp!!.toInt()}°C",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Location text (kept, but styled)
                            Text(
                                text = locationText,
                                color = Color.White.copy(alpha = 0.80f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Use My Location button (kept)
                    Button(
                        modifier = Modifier.fillMaxWidth(),
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

            // Save coords for navigation to forecast screen
            onCoordsUpdated?.invoke(lat, lon)

            try {
                // Weather
                val weather = WeatherRepository.getCurrentWeather(lat, lon)
                onWeatherUpdated?.invoke(weather)

                // Better place name from OpenWeather reverse geocoding
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
