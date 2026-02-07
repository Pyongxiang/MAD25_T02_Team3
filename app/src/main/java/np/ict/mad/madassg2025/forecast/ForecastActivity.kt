package np.ict.mad.madassg2025.forecast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class ForecastActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val lat = intent.getDoubleExtra("lat", Double.NaN)
        val lon = intent.getDoubleExtra("lon", Double.NaN)
        val place = intent.getStringExtra("place") ?: "My Location"

        setContent {
            ForecastScreen(
                place = place,
                lat = lat,
                lon = lon,
                onBack = { finish() }
            )
        }
    }
}