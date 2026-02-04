package np.ict.mad.madassg2025.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    private val KEY_UNITS = stringPreferencesKey("units")
    private val KEY_RAIN_ALERTS = booleanPreferencesKey("rain_alerts_enabled")

    private val KEY_DEFAULT_NAME = stringPreferencesKey("default_loc_name")
    private val KEY_DEFAULT_LAT = doublePreferencesKey("default_loc_lat")
    private val KEY_DEFAULT_LON = doublePreferencesKey("default_loc_lon")

    private val KEY_ALERT_HOUR = stringPreferencesKey("alert_hour")     // "0".."23"
    private val KEY_ALERT_MIN = stringPreferencesKey("alert_minute")    // "0".."59"

    val unitsFlow: Flow<String> = context.dataStore.data.map { it[KEY_UNITS] ?: "C" }
    val rainAlertsFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_RAIN_ALERTS] ?: false }

    val defaultNameFlow: Flow<String> = context.dataStore.data.map { it[KEY_DEFAULT_NAME] ?: "" }
    val defaultLatFlow: Flow<Double> = context.dataStore.data.map { it[KEY_DEFAULT_LAT] ?: Double.NaN }
    val defaultLonFlow: Flow<Double> = context.dataStore.data.map { it[KEY_DEFAULT_LON] ?: Double.NaN }

    val alertHourFlow: Flow<Int> = context.dataStore.data.map { (it[KEY_ALERT_HOUR] ?: "8").toInt() }
    val alertMinuteFlow: Flow<Int> = context.dataStore.data.map { (it[KEY_ALERT_MIN] ?: "0").toInt() }

    suspend fun setUnits(value: String) {
        context.dataStore.edit { it[KEY_UNITS] = value }

        // Mirror to existing prefs so your current UI stays compatible
        val legacy = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
        legacy.edit().putString("unit_pref", value).apply()
    }

    suspend fun setRainAlertsEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_RAIN_ALERTS] = value }
    }

    suspend fun setDefaultLocation(name: String, lat: Double, lon: Double) {
        context.dataStore.edit {
            it[KEY_DEFAULT_NAME] = name
            it[KEY_DEFAULT_LAT] = lat
            it[KEY_DEFAULT_LON] = lon
        }
    }

    suspend fun setAlertTime(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[KEY_ALERT_HOUR] = hour.coerceIn(0, 23).toString()
            it[KEY_ALERT_MIN] = minute.coerceIn(0, 59).toString()
        }
    }

    suspend fun getAlertTime(): Pair<Int, Int> {
        val data = context.dataStore.data.first()
        val h = (data[KEY_ALERT_HOUR] ?: "8").toInt()
        val m = (data[KEY_ALERT_MIN] ?: "0").toInt()
        return h to m
    }

    suspend fun getDefaultLocation(): DefaultLocation? {
        val data = context.dataStore.data.first()
        val lat = data[KEY_DEFAULT_LAT] ?: Double.NaN
        val lon = data[KEY_DEFAULT_LON] ?: Double.NaN
        val name = data[KEY_DEFAULT_NAME] ?: ""

        if (lat.isNaN() || lon.isNaN()) return null
        return DefaultLocation(name.ifBlank { "Default location" }, lat, lon)
    }

    suspend fun isRainAlertsEnabled(): Boolean {
        return context.dataStore.data.first()[KEY_RAIN_ALERTS] ?: false
    }
}

data class DefaultLocation(
    val name: String,
    val lat: Double,
    val lon: Double
)
