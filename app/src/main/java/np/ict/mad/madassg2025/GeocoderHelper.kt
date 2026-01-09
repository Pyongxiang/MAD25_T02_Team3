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

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    var result: String? = null
                    geocoder.getFromLocation(lat, lon, 1) { addresses ->
                        val a = addresses.firstOrNull()
                        result = a?.subLocality
                            ?: a?.locality
                                    ?: a?.featureName
                                    ?: a?.thoroughfare
                    }
                    result
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(lat, lon, 1)
                    val a = addresses?.firstOrNull()
                    a?.subLocality
                        ?: a?.locality
                        ?: a?.featureName
                        ?: a?.thoroughfare
                }
            } catch (e: Exception) {
                Log.e("GeocoderHelper", "reverseGeocode failed: ${e.message}", e)
                null
            }
        }
}
