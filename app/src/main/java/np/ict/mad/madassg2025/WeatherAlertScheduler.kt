package np.ict.mad.madassg2025

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import np.ict.mad.madassg2025.settings.AlertFrequency

object WeatherAlertScheduler {
    private const val WORK_NAME = "weather_alerts_periodic"

    fun schedule(context: Context, freq: AlertFrequency) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val req = PeriodicWorkRequestBuilder<WeatherAlertWorker>(
            freq.repeatInterval,
            freq.repeatUnit
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
