package np.ict.mad.madassg2025

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import np.ict.mad.madassg2025.ui.home.HomeActions
import np.ict.mad.madassg2025.ui.home.HomeScreen
import np.ict.mad.madassg2025.ui.home.HomeUiState
import np.ict.mad.madassg2025.ui.home.SavedLocation
import np.ict.mad.madassg2025.ui.home.UnitPref
import np.ict.mad.madassg2025.ui.home.computeSkyMode
import org.json.JSONArray
import org.json.JSONObject

class HomePage : ComponentActivity() {

    private lateinit var locationHelper: LocationHelper
    private val firebaseHelper = FirebaseHelper()

    private var uiState by mutableStateOf(HomeUiState())

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                fetchLocationAndWeather()
            } else {
                uiState = uiState.copy(
                    isLoading = false,
                    error = "Permission denied.\nPlease allow location to use this feature.",
                    placeLabel = "—"
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationHelper = LocationHelper(applicationContext)

        val userKey = buildUserKey(firebaseHelper)
        uiState = uiState.copy(
            unit = loadUnitPref(),
            savedLocations = loadSavedLocations(userKey)
        )

        setContent {
            HomeScreen(
                state = uiState,
                actions = HomeActions(
                    onToggleUnit = { u ->
                        uiState = uiState.copy(unit = u)
                        saveUnitPref(u)
                    },
                    onUseMyLocation = { useMyLocation() },
                    onOpenForecast = { openForecastIfPossible() },
                    onSelectSaved = { loc -> fetchWeatherForSaved(loc) },
                    onAddCurrent = { addCurrentToSaved() },
                    onRemoveSaved = { loc -> removeSaved(loc) }
                )
            )
        }
    }

    private fun useMyLocation() {
        val status = ContextCompat.checkSelfPermission(
            this@HomePage,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (status == PackageManager.PERMISSION_GRANTED) {
            fetchLocationAndWeather()
        } else {
            uiState = uiState.copy(
                isLoading = true,
                error = null,
                placeLabel = "Loading…",
                locationText = "",
                tempC = null,
                condition = null,
                weatherId = null
            )
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun openForecastIfPossible() {
        if (!uiState.canOpenForecast) return
        val lat = uiState.lastLat ?: return
        val lon = uiState.lastLon ?: return

        val intent = Intent(this@HomePage, ForecastActivity::class.java).apply {
            putExtra("lat", lat)
            putExtra("lon", lon)
            putExtra("place", uiState.placeLabel)
        }
        startActivity(intent)
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
            uiState = uiState.copy(
                isLoading = true,
                error = null,
                placeLabel = "Loading…",
                locationText = "",
                tempC = null,
                condition = null,
                weatherId = null
            )

            try {
                val location: Location? = locationHelper.getFreshLocation()
                if (location == null) {
                    uiState = uiState.copy(
                        isLoading = false,
                        error = "Unable to get location."
                    )
                    return@launch
                }

                val lat = location.latitude
                val lon = location.longitude

                val weather: WeatherResponse? = WeatherRepository.getCurrentWeather(lat, lon)
                if (weather == null) {
                    uiState = uiState.copy(
                        isLoading = false,
                        error = "Weather unavailable (API returned null)"
                    )
                    return@launch
                }

                val betterName = WeatherRepository.getPlaceName(lat, lon)
                val finalLabel = betterName ?: weather.name

                val wid = weather.weather.firstOrNull()?.id
                val desc = weather.weather.firstOrNull()?.description
                val nowUtc = System.currentTimeMillis() / 1000L
                val sky = computeSkyMode(
                    nowUtcSec = nowUtc,
                    sunriseUtcSec = weather.sys.sunrise,
                    sunsetUtcSec = weather.sys.sunset
                )

                uiState = uiState.copy(
                    isLoading = false,
                    error = null,
                    placeLabel = finalLabel,
                    locationText = "Location: $finalLabel\n($lat, $lon)",
                    tempC = weather.main.temp,
                    condition = desc,
                    weatherId = wid,
                    sunriseUtc = weather.sys.sunrise,
                    sunsetUtc = weather.sys.sunset,
                    tzOffsetSec = weather.timezone,
                    lastLat = lat,
                    lastLon = lon,
                    skyMode = sky
                )
            } catch (e: Exception) {
                Log.e("HomePage", "Fetch failed: ${e.message}", e)
                uiState = uiState.copy(
                    isLoading = false,
                    error = e.message ?: "Error"
                )
            }
        }
    }

    private fun fetchWeatherForSaved(loc: SavedLocation) {
        lifecycleScope.launch {
            uiState = uiState.copy(
                isLoading = true,
                error = null,
                placeLabel = "Loading…",
                locationText = "",
                tempC = null,
                condition = null,
                weatherId = null
            )

            try {
                val weather: WeatherResponse? = WeatherRepository.getCurrentWeather(loc.lat, loc.lon)
                if (weather == null) {
                    uiState = uiState.copy(isLoading = false, error = "Weather unavailable (API returned null)")
                    return@launch
                }

                val betterName = WeatherRepository.getPlaceName(loc.lat, loc.lon)
                val finalLabel = betterName ?: weather.name

                val wid = weather.weather.firstOrNull()?.id
                val desc = weather.weather.firstOrNull()?.description
                val nowUtc = System.currentTimeMillis() / 1000L
                val sky = computeSkyMode(
                    nowUtcSec = nowUtc,
                    sunriseUtcSec = weather.sys.sunrise,
                    sunsetUtcSec = weather.sys.sunset
                )

                uiState = uiState.copy(
                    isLoading = false,
                    error = null,
                    placeLabel = finalLabel,
                    locationText = "Location: $finalLabel\n(${loc.lat}, ${loc.lon})",
                    tempC = weather.main.temp,
                    condition = desc,
                    weatherId = wid,
                    sunriseUtc = weather.sys.sunrise,
                    sunsetUtc = weather.sys.sunset,
                    tzOffsetSec = weather.timezone,
                    lastLat = loc.lat,
                    lastLon = loc.lon,
                    skyMode = sky
                )
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, error = e.message ?: "Error")
            }
        }
    }

    private fun addCurrentToSaved() {
        val lat = uiState.lastLat
        val lon = uiState.lastLon
        val name = uiState.placeLabel.takeIf { it.isNotBlank() && it != "—" && it != "Loading…" }

        if (lat == null || lon == null || name.isNullOrBlank()) return

        val userKey = buildUserKey(firebaseHelper)
        val newItem = SavedLocation(name = name, lat = lat, lon = lon)

        val updated = (uiState.savedLocations + newItem)
            .distinctBy { "${it.name}|${it.lat}|${it.lon}" }
            .take(12)

        uiState = uiState.copy(savedLocations = updated)
        saveSavedLocations(userKey, updated)
    }

    private fun removeSaved(loc: SavedLocation) {
        val userKey = buildUserKey(firebaseHelper)
        val updated = uiState.savedLocations.filterNot {
            it.name == loc.name && it.lat == loc.lat && it.lon == loc.lon
        }
        uiState = uiState.copy(savedLocations = updated)
        saveSavedLocations(userKey, updated)
    }

    // -------- prefs / storage --------

    private fun prefs() = getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)

    private fun buildUserKey(firebaseHelper: FirebaseHelper): String {
        val uid = firebaseHelper.getCurrentUser()?.uid
        val email = firebaseHelper.getCurrentUserEmail()
        return uid ?: email ?: "guest"
    }

    private fun loadUnitPref(): UnitPref {
        val raw = prefs().getString("unit_pref", UnitPref.C.name) ?: UnitPref.C.name
        return runCatching { UnitPref.valueOf(raw) }.getOrDefault(UnitPref.C)
    }

    private fun saveUnitPref(unit: UnitPref) {
        prefs().edit().putString("unit_pref", unit.name).apply()
    }

    private fun loadSavedLocations(userKey: String): List<SavedLocation> {
        val raw = prefs().getString("saved_locations_$userKey", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        SavedLocation(
                            name = o.getString("name"),
                            lat = o.getDouble("lat"),
                            lon = o.getDouble("lon")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveSavedLocations(userKey: String, list: List<SavedLocation>) {
        val arr = JSONArray()
        list.forEach { loc ->
            val o = JSONObject()
            o.put("name", loc.name)
            o.put("lat", loc.lat)
            o.put("lon", loc.lon)
            arr.put(o)
        }
        prefs().edit().putString("saved_locations_$userKey", arr.toString()).apply()
    }
}
