package np.ict.mad.madassg2025.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import np.ict.mad.madassg2025.R

object WeatherNotifier {
    private const val CHANNEL_ID = "weather_alerts"
    private const val CHANNEL_NAME = "Weather Alerts"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Rain and weather reminders"
                enableVibration(true)
            }
            mgr.createNotificationChannel(channel)
        }
    }

        fun showTestAlert(context: Context, locationName: String) {
        ensureChannel(context)

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher) // use a guaranteed icon
            .setContentTitle("Test weather alert")
            .setContentText("Notifications are working! (Default: $locationName)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // sound + vibration + lights
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(2001, notif)
    }
}
