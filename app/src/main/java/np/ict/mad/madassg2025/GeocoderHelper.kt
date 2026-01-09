package np.ict.mad.madassg2025

import android.content.Context
import android.location.Geocoder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object GeocoderHelper {

    suspend fun reverseGeocode(context: Context, lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())

                fun pickName(a: android.location.Address?): String? {
                    if (a == null) return null
                    // Prefer more human-friendly names first
                    return a.featureName
                        ?: a.subLocality
                        ?: a.locality
                        ?: a.thoroughfare
                        ?: a.adminArea
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    var result: String? = null
                    geocoder.getFromLocation(lat, lon, 1) { addresses ->
                        result = pickName(addresses.firstOrNull())
                    }
                    result
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(lat, lon, 1)
                    pickName(addresses?.firstOrNull())
                }
            } catch (e: Exception) {
                Log.e("GeocoderHelper", "reverseGeocode failed: ${e.message}", e)
                null
            }
        }
}
