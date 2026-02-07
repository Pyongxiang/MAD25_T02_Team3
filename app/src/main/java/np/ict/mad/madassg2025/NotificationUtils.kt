package np.ict.mad.madassg2025

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

const val WEATHER_CHANNEL_ID = "weather_alerts"

fun createWeatherChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            WEATHER_CHANNEL_ID,
            "Weather alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for rain reminders"
            enableVibration(true)
        }

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }
}
