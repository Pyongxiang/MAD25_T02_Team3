package np.ict.mad.madassg2025

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationHelper(context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    /**
     * Forces a fresh location fix (not cached "last known").
     * This fixes the "stuck at Chinese Garden" issue.
     */
    @SuppressLint("MissingPermission")
    suspend fun getFreshLocation(): Location? = suspendCancellableCoroutine { cont ->
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            0L
        )
            .setWaitForAccurateLocation(true)
            .setMaxUpdates(1)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation
                Log.d("LocationHelper", "Fresh location: ${loc?.latitude}, ${loc?.longitude}")
                client.removeLocationUpdates(this)
                cont.resume(loc)
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                Log.d("LocationHelper", "Location availability: ${availability.isLocationAvailable}")
            }
        }

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener { e ->
                Log.e("LocationHelper", "requestLocationUpdates failed: ${e.message}")
                client.removeLocationUpdates(callback)
                cont.resume(null)
            }

        cont.invokeOnCancellation {
            client.removeLocationUpdates(callback)
        }
    }
}
