package np.ict.mad.madassg2025

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationHelper(private val context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(): Location? =
        suspendCancellableCoroutine { cont ->
            Log.d("LocationHelper", "Trying lastLocation")

            client.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        Log.d(
                            "LocationHelper",
                            "lastLocation OK: ${location.latitude}, ${location.longitude}"
                        )
                        cont.resume(location)
                    } else {
                        Log.d(
                            "LocationHelper",
                            "lastLocation is NULL, requesting current location"
                        )
                        requestCurrentLocation(cont)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("LocationHelper", "lastLocation failed: ${e.message}")
                    requestCurrentLocation(cont)
                }
        }

    @SuppressLint("MissingPermission")
    private fun requestCurrentLocation(
        cont: kotlinx.coroutines.CancellableContinuation<Location?>
    ) {
        val cts = CancellationTokenSource()

        client.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            cts.token
        ).addOnSuccessListener { loc ->
            if (loc != null) {
                Log.d(
                    "LocationHelper",
                    "currentLocation OK: ${loc.latitude}, ${loc.longitude}"
                )
                cont.resume(loc)
            } else {
                Log.d("LocationHelper", "currentLocation returned NULL")
                cont.resume(null)
            }
        }.addOnFailureListener { e ->
            Log.e("LocationHelper", "getCurrentLocation failed: ${e.message}")
            cont.resume(null)
        }

        cont.invokeOnCancellation { cts.cancel() }
    }
}
