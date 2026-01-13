package np.ict.mad.madassg2025.ui.home

data class SavedLocation(
    val name: String,
    val lat: Double,
    val lon: Double
)

enum class UnitPref(val label: String) {
    C("°C"),
    F("°F")
}

enum class SkyMode { NIGHT, DAWN, DAY, DUSK }

data class HomeUiState(
    val isLoading: Boolean = false,
    val error: String? = null,

    val placeLabel: String = "—",
    val locationText: String = "",

    // Current weather
    val tempC: Double? = null,
    val condition: String? = null,
    val weatherId: Int? = null,

    // Sunrise/sunset from current weather
    val sunriseUtc: Long? = null,
    val sunsetUtc: Long? = null,
    val tzOffsetSec: Int? = null,

    val unit: UnitPref = UnitPref.C,

    val lastLat: Double? = null,
    val lastLon: Double? = null,

    val savedLocations: List<SavedLocation> = emptyList(),

    val skyMode: SkyMode = SkyMode.NIGHT
) {
    val canOpenForecast: Boolean
        get() = !isLoading && lastLat != null && lastLon != null
}

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
