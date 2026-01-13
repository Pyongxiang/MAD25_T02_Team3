package np.ict.mad.madassg2025

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationHelper(context: Context) {

    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getFreshLocation(timeoutMs: Long = 6000L): Location? =
        suspendCancellableCoroutine { cont ->

            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                0L
            )
                .setWaitForAccurateLocation(true)
                .setMaxUpdates(1)
                .build()

            val handler = Handler(Looper.getMainLooper())

            // Make timeoutRunnable exist before callback references it
            var timeoutRunnable: Runnable? = null

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation
                    Log.d("LocationHelper", "Fresh location: ${loc?.latitude}, ${loc?.longitude}")

                    // Stop timeout + updates
                    timeoutRunnable?.let { handler.removeCallbacks(it) }
                    client.removeLocationUpdates(this)

                    if (cont.isActive) cont.resume(loc)
                }

                override fun onLocationAvailability(availability: LocationAvailability) {
                    Log.d("LocationHelper", "Location availability: ${availability.isLocationAvailable}")
                }
            }

            timeoutRunnable = Runnable {
                Log.e("LocationHelper", "Timeout waiting for location fix.")
                client.removeLocationUpdates(callback)
                if (cont.isActive) cont.resume(null)
            }

            // Start updates
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { e ->
                    Log.e("LocationHelper", "requestLocationUpdates failed: ${e.message}", e)
                    timeoutRunnable?.let { handler.removeCallbacks(it) }
                    client.removeLocationUpdates(callback)
                    if (cont.isActive) cont.resume(null)
                }

            // Timeout if no fix arrives
            handler.postDelayed(timeoutRunnable!!, timeoutMs)

            // Cleanup if coroutine cancelled
            cont.invokeOnCancellation {
                timeoutRunnable?.let { handler.removeCallbacks(it) }
                client.removeLocationUpdates(callback)
            }
        }
}
