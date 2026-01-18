package np.ict.mad.madassg2025.ui.home

data class SavedLocation(
    val name: String,
    val lat: Double,
    val lon: Double
)

data class MiniWeatherUi(
    val tempC: Double? = null,
    val desc: String? = null,
    val weatherId: Int? = null,
    val isLoading: Boolean = true
)

data class PlaceSuggestion(
    val name: String,
    val state: String?,
    val country: String?,
    val lat: Double,
    val lon: Double
) {
    val displayLabel: String
        get() = listOfNotNull(name.takeIf { it.isNotBlank() }, state?.takeIf { it.isNotBlank() }, country?.takeIf { it.isNotBlank() })
            .joinToString(", ")
}

enum class UnitPref(val label: String) { C("°C"), F("°F") }
enum class SkyMode { NIGHT, DAWN, DAY, DUSK }

data class HomeUiState(
    val isLoading: Boolean = false,
    val error: String? = null,

    val placeLabel: String = "–",
    val locationText: String = "",

    val tempC: Double? = null,
    val condition: String? = null,
    val weatherId: Int? = null,

    val sunriseUtc: Long? = null,
    val sunsetUtc: Long? = null,
    val tzOffsetSec: Int? = null,

    val unit: UnitPref = UnitPref.C,

    val lastLat: Double? = null,
    val lastLon: Double? = null,

    val savedLocations: List<SavedLocation> = emptyList(),
    val favouritesMini: Map<String, MiniWeatherUi> = emptyMap(),

    val searchQuery: String = "",
    val searchLoading: Boolean = false,
    val searchError: String? = null,
    val searchResults: List<PlaceSuggestion> = emptyList(),

    val skyMode: SkyMode = SkyMode.NIGHT,

    // AI Narrator state
    val isNarrating: Boolean = false,
    val narratorError: String? = null
) {
    val canOpenForecast: Boolean
        get() = !isLoading && lastLat != null && lastLon != null
}

fun favKey(loc: SavedLocation): String = "${loc.name}|${loc.lat}|${loc.lon}"

fun computeSkyMode(
    nowUtcSec: Long,
    sunriseUtcSec: Long?,
    sunsetUtcSec: Long?
): SkyMode {
    if (sunriseUtcSec != null && sunsetUtcSec != null) {
        val dawnStart = sunriseUtcSec - 45 * 60
        val dawnEnd = sunriseUtcSec + 45 * 60
        val duskStart = sunsetUtcSec - 45 * 60
        val duskEnd = sunsetUtcSec + 45 * 60

        return when {
            nowUtcSec in dawnStart..dawnEnd -> SkyMode.DAWN
            nowUtcSec in duskStart..duskEnd -> SkyMode.DUSK
            nowUtcSec in sunriseUtcSec..sunsetUtcSec -> SkyMode.DAY
            else -> SkyMode.NIGHT
        }
    }

    val cal = java.util.Calendar.getInstance()
    val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour in 5..7 -> SkyMode.DAWN
        hour in 8..17 -> SkyMode.DAY
        hour in 18..19 -> SkyMode.DUSK
        else -> SkyMode.NIGHT
    }
}