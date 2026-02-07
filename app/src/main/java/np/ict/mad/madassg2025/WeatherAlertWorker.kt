package np.ict.mad.madassg2025

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import np.ict.mad.madassg2025.settings.SettingsStore

class WeatherAlertWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val store = SettingsStore(context)

        if (!store.isRainAlertsEnabled()) return Result.success()

        val def = store.getDefaultLocation() ?: return Result.success()

        createWeatherChannel(context)

        val forecast = withContext(Dispatchers.IO) {
            WeatherForecast().getHourly24AndDaily5(def.lat, def.lon)
        } ?: return Result.retry()

        // Rule: POP >= 60% OR rain >= 0.5mm within next 24h
        val rainSoon = forecast.hourlyNext24.any { h ->
            h.popPct >= 60 || h.rainMm >= 0.5
        }

        if (rainSoon) {
            sendRainNotification(context, def.name)
        }

        // IMPORTANT: We do NOT reschedule inside the worker anymore.
        // Scheduling is handled by PeriodicWorkRequest in WeatherAlertScheduler.

        return Result.success()
    }

    private fun sendRainNotification(context: Context, locationName: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, WEATHER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cloudy)
            .setContentTitle("Rain likely soon")
            .setContentText("Rain is expected in $locationName. Bring an umbrella.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(1001, notif)
    }
}
