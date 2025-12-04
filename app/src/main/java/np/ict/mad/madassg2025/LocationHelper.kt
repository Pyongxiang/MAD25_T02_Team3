package np.ict.mad.madassg2025

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
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
            // 1️⃣ First try the cached last location
            client.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        cont.resume(location)
                    } else {
                        // 2️⃣ If null, request a fresh current location
                        requestCurrentLocation(cont)
                    }
                }
                .addOnFailureListener {
                    // If lastLocation fails, also try current location
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
        ).addOnSuccessListener { currentLocation ->
            cont.resume(currentLocation)
        }.addOnFailureListener {
            cont.resume(null)
        }

        // Cancel the request if the coroutine is cancelled
        cont.invokeOnCancellation {
            cts.cancel()
        }
    }
}
