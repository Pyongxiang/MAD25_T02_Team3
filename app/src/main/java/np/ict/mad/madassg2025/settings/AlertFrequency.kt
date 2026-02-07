package np.ict.mad.madassg2025.settings

import java.util.concurrent.TimeUnit

enum class AlertFrequency(
    val label: String,
    val repeatInterval: Long,
    val repeatUnit: TimeUnit
) {
    DAILY("Once daily", 24, TimeUnit.HOURS),
    HOURLY("Once every hour", 1, TimeUnit.HOURS),
    SIX_HOURLY("Once every 6 hours", 6, TimeUnit.HOURS),
    FIFTEEN_MIN("Once every 15 minutes", 15, TimeUnit.MINUTES);

    companion object {
        fun fromStored(name: String?): AlertFrequency {
            return values().firstOrNull { it.name == name } ?: DAILY
        }
    }
}
