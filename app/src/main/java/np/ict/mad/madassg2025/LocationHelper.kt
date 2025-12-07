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
            Log.d("LocationHelper", "Requesting current location only")

            val cts = CancellationTokenSource()

            client.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cts.token
            ).addOnSuccessListener { loc ->
                if (loc != null) {
                    Log.d(
                        "LocationHelper",
                        "getCurrentLocation OK: ${loc.latitude}, ${loc.longitude}"
                    )
                    cont.resume(loc)
                } else {
                    Log.d("LocationHelper", "getCurrentLocation returned NULL")
                    cont.resume(null)
                }
            }.addOnFailureListener { e ->
                Log.e("LocationHelper", "getCurrentLocation failed: ${e.message}")
                cont.resume(null)
            }

            cont.invokeOnCancellation { cts.cancel() }
        }
}
