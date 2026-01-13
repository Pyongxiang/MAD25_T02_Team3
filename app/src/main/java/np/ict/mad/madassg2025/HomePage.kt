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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    private var onPlaceLabelUpdated: ((String) -> Unit)? = null
    private var onLoadingChanged: ((Boolean) -> Unit)? = null
    private var onBeginFetchUiReset: (() -> Unit)? = null

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                fetchLocationAndWeather()
            } else {
                onLoadingChanged?.invoke(false)
                onPlaceLabelUpdated?.invoke("—")
                onLocationTextChanged?.invoke("Permission denied.\nPlease allow location to use this feature.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationHelper = LocationHelper(applicationContext)

        setContent {
            var isLoading by remember { mutableStateOf(false) }

            var locationText by remember { mutableStateOf("") }

            // Main label shown in the UI header
            var placeLabel by remember { mutableStateOf("—") }

            var weatherTemp by remember { mutableStateOf<Double?>(null) }
            var weatherCondition by remember { mutableStateOf<String?>(null) }
            var weatherError by remember { mutableStateOf<String?>(null) }

            // Emoji "icon"
            var weatherEmoji by remember { mutableStateOf<String?>(null) }

            // Store last successful coordinates so the "Forecast" card can open forecast screen
            var lastLat by remember { mutableStateOf<Double?>(null) }
            var lastLon by remember { mutableStateOf<Double?>(null) }

            onLoadingChanged = { loading -> isLoading = loading }
            onLocationTextChanged = { newText -> locationText = newText }
            onPlaceLabelUpdated = { newLabel -> placeLabel = newLabel }

            onBeginFetchUiReset = {
                // Important: clear old UI so previous location (e.g., Chinese Garden) doesn't flash
                isLoading = true
                placeLabel = "Loading…"
                locationText = ""
                weatherError = null
                weatherTemp = null
                weatherCondition = null
                weatherEmoji = null
            }

            onWeatherUpdated = { response ->
                weatherError = null
                weatherTemp = response.main.temp

                val first = response.weather.firstOrNull()
                weatherCondition = first?.description
                weatherEmoji = pickWeatherEmojiById(first?.id)

                // Fallback only (you later override using reverse-geocoding)
                if (placeLabel == "—" || placeLabel.isBlank() || placeLabel == "Loading…") {
                    placeLabel = response.name
                }
            }

            onWeatherError = { msg ->
                weatherError = msg
                weatherTemp = null
                weatherCondition = null
                weatherEmoji = null
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
                    // Header
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
                            text = placeLabel.ifBlank { "—" },
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        when {
                            isLoading -> {
                                Text(
                                    text = "Loading…",
                                    color = Color.White.copy(alpha = 0.80f),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            weatherError != null -> Text(
                                text = "Error: $weatherError",
                                color = Color.White.copy(alpha = 0.9f)
                            )

                            weatherTemp == null || weatherCondition == null -> Text(
                                text = "Tap Use My Location to load weather",
                                color = Color.White.copy(alpha = 0.75f)
                            )

                            else -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = weatherEmoji ?: "",
                                        modifier = Modifier.size(42.dp),
                                        color = Color.White,
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                    Text(
                                        text = "${weatherTemp!!.toInt()}°",
                                        color = Color.White,
                                        style = MaterialTheme.typography.displayLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Text(
                                    text = weatherCondition!!.replaceFirstChar { it.uppercase() },
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }

                    // Forecast card (tap to open ForecastActivity)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = (lastLat != null && lastLon != null && !isLoading)) {
                                val intent = Intent(this@HomePage, ForecastActivity::class.java).apply {
                                    putExtra("lat", lastLat!!)
                                    putExtra("lon", lastLon!!)
                                    putExtra("place", placeLabel)
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
                                text = when {
                                    isLoading -> "Fetching your location…"
                                    lastLat == null -> "Load location to view forecast"
                                    else -> "Tap to view the next 7 days"
                                },
                                color = Color.White.copy(alpha = 0.75f)
                            )

                            if (!isLoading && weatherTemp != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = weatherEmoji ?: "",
                                        color = Color.White,
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                    Text(
                                        text = "${weatherTemp!!.toInt()}°C",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Show coords / resolved label only when we actually have it
                            if (!isLoading && locationText.isNotBlank()) {
                                Text(
                                    text = locationText,
                                    color = Color.White.copy(alpha = 0.80f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

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
                                // Don’t flash old data while permission dialog is shown
                                onBeginFetchUiReset?.invoke()
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
            onBeginFetchUiReset?.invoke()

            try {
                val location: Location? = locationHelper.getFreshLocation()
                if (location == null) {
                    onWeatherError?.invoke("No location fix")
                    onPlaceLabelUpdated?.invoke("—")
                    onLocationTextChanged?.invoke("Unable to get location.")
                    return@launch
                }

                val lat = location.latitude
                val lon = location.longitude
                Log.d("HomePage", "Using coords lat=$lat lon=$lon")

                onCoordsUpdated?.invoke(lat, lon)

                val weather: WeatherResponse? = WeatherRepository.getCurrentWeather(lat, lon)
                if (weather == null) {
                    onWeatherError?.invoke("Weather unavailable (API returned null)")
                    onPlaceLabelUpdated?.invoke("—")
                    onLocationTextChanged?.invoke("($lat, $lon)")
                    return@launch
                }

                onWeatherUpdated?.invoke(weather)

                val betterName = WeatherRepository.getPlaceName(lat, lon)
                val finalLabel = betterName ?: weather.name

                onPlaceLabelUpdated?.invoke(finalLabel)
                onLocationTextChanged?.invoke("Location: $finalLabel\n($lat, $lon)")
            } catch (e: Exception) {
                Log.e("HomePage", "Fetch failed: ${e.message}", e)
                onWeatherError?.invoke(e.message ?: "Error")
                onPlaceLabelUpdated?.invoke("—")
                onLocationTextChanged?.invoke("Unable to load location/weather.")
            } finally {
                onLoadingChanged?.invoke(false)
            }
        }
    }
}

private fun pickWeatherEmojiById(weatherId: Int?): String {
    return when (weatherId) {
        in 200..232 -> "⛈️"   // thunderstorm
        in 300..321 -> "🌦️"   // drizzle
        in 500..504 -> "🌧️"   // rain
        511 -> "🌨️"           // freezing rain
        in 520..531 -> "🌧️"   // shower rain
        in 600..622 -> "❄️"   // snow
        in 701..781 -> "🌫️"   // mist / fog / haze / etc
        800 -> "☀️"           // clear
        in 801..804 -> "☁️"   // clouds
        else -> "🌡️"
    }
}
