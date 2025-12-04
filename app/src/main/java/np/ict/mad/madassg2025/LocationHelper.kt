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
            // Log the status of the location fetch attempt
            Log.d("LocationHelper", "Attempting to fetch location...")

            client.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        Log.d("LocationHelper", "Location found: ${location.latitude}, ${location.longitude}")
                        cont.resume(location)
                    } else {
                        Log.d("LocationHelper", "No last known location, requesting new location")
                        requestCurrentLocation(cont)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("LocationHelper", "Failed to get location: ${e.message}")
                    cont.resume(null)
                }
        }

    @SuppressLint("MissingPermission")
    private fun requestCurrentLocation(
        cont: kotlinx.coroutines.CancellableContinuation<Location?>
    ) {
        val cts = CancellationTokenSource()

        client.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,  // Set to this to balance between accuracy and battery
            cts.token
        ).addOnSuccessListener { currentLocation ->
            Log.d("LocationHelper", "Current location fetched: ${currentLocation.latitude}, ${currentLocation.longitude}")
            cont.resume(currentLocation)
        }.addOnFailureListener {
            Log.e("LocationHelper", "Failed to fetch current location.")
            cont.resume(null)  // Return null if no location found
        }

        cont.invokeOnCancellation {
            cts.cancel()
        }
    }
}
