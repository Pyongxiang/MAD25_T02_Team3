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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import np.ict.mad.madassg2025.ui.home.HomeActions
import np.ict.mad.madassg2025.ui.home.HomeScreen
import np.ict.mad.madassg2025.ui.home.HomeUiState
import np.ict.mad.madassg2025.ui.home.MiniWeatherUi
import np.ict.mad.madassg2025.ui.home.SavedLocation
import np.ict.mad.madassg2025.ui.home.UnitPref
import np.ict.mad.madassg2025.ui.home.computeSkyMode
import np.ict.mad.madassg2025.ui.home.favKey
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
        val saved = loadSavedLocations(userKey)

        uiState = uiState.copy(
            unit = loadUnitPref(),
            savedLocations = saved,
            favouritesMini = seedMiniMap(saved, existing = emptyMap())
        )

        // Load mini weather for favourites immediately
        refreshFavouritesMiniWeather()

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
                    uiState = uiState.copy(isLoading = false, error = "Unable to get location.")
                    return@launch
                }

                val lat = location.latitude
                val lon = location.longitude

                val weather: WeatherResponse? = WeatherRepository.getCurrentWeather(lat, lon)
                if (weather == null) {
                    uiState = uiState.copy(isLoading = false, error = "Weather unavailable (API returned null)")
                    return@launch
                }

                val betterName = WeatherRepository.getPlaceName(lat, lon)
                val finalLabel = betterName ?: weather.name

                val wid = weather.weather.firstOrNull()?.id
                val desc = weather.weather.firstOrNull()?.description
                val nowUtc = System.currentTimeMillis() / 1000L
                val sky = computeSkyMode(nowUtc, weather.sys.sunrise, weather.sys.sunset)

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
                uiState = uiState.copy(isLoading = false, error = e.message ?: "Error")
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
                val sky = computeSkyMode(nowUtc, weather.sys.sunrise, weather.sys.sunset)

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
            .take(20)

        // show the new card immediately (as Loading…)
        val seeded = seedMiniMap(updated, uiState.favouritesMini)

        uiState = uiState.copy(savedLocations = updated, favouritesMini = seeded)
        saveSavedLocations(userKey, updated)

        // fetch mini weather in background
        refreshFavouritesMiniWeather()
    }

    private fun removeSaved(loc: SavedLocation) {
        val userKey = buildUserKey(firebaseHelper)
        val updated = uiState.savedLocations.filterNot {
            it.name == loc.name && it.lat == loc.lat && it.lon == loc.lon
        }

        val updatedMini = uiState.favouritesMini.toMutableMap().apply {
            remove(favKey(loc))
        }

        uiState = uiState.copy(savedLocations = updated, favouritesMini = updatedMini)
        saveSavedLocations(userKey, updated)
    }

    /**
     * Fetch mini weather for each favourite card (if missing or still loading).
     */
    private fun refreshFavouritesMiniWeather() {
        val favourites = uiState.savedLocations
        if (favourites.isEmpty()) return

        lifecycleScope.launch {
            favourites.forEach { loc ->
                val key = favKey(loc)
                val current = uiState.favouritesMini[key]
                if (current != null && current.isLoading.not() && current.tempC != null) return@forEach

                // mark loading
                uiState = uiState.copy(
                    favouritesMini = uiState.favouritesMini.toMutableMap().apply {
                        put(key, MiniWeatherUi(isLoading = true))
                    }
                )

                val mini = withContext(Dispatchers.IO) {
                    runCatching {
                        val w = WeatherRepository.getCurrentWeather(loc.lat, loc.lon)
                        if (w == null) null
                        else MiniWeatherUi(
                            tempC = w.main.temp,
                            desc = w.weather.firstOrNull()?.description,
                            weatherId = w.weather.firstOrNull()?.id,
                            isLoading = false
                        )
                    }.getOrNull()
                }

                uiState = uiState.copy(
                    favouritesMini = uiState.favouritesMini.toMutableMap().apply {
                        put(key, mini ?: MiniWeatherUi(isLoading = false))
                    }
                )
            }
        }
    }

    private fun seedMiniMap(
        saved: List<SavedLocation>,
        existing: Map<String, MiniWeatherUi>
    ): Map<String, MiniWeatherUi> {
        val out = existing.toMutableMap()
        saved.forEach { loc ->
            val key = favKey(loc)
            if (!out.containsKey(key)) {
                out[key] = MiniWeatherUi(isLoading = true)
            }
        }
        // remove keys that are no longer saved
        val keep = saved.map { favKey(it) }.toSet()
        out.keys.toList().forEach { k -> if (!keep.contains(k)) out.remove(k) }
        return out
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
