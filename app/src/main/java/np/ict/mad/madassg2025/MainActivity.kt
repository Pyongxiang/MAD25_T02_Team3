package np.ict.mad.madassg2025

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val firebaseHelper = FirebaseHelper()
        val prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val isRemembered = prefs.getBoolean("remember", false)

        // If Firebase has a session AND user checked "Remember Me"
        if (firebaseHelper.isLoggedIn() && isRemembered) {
            val intent = Intent(this, HomePage::class.java)
            startActivity(intent)
            finish() // Close MainActivity so they can't go back to Login
            return // Skip setContent
        }

        setContent {
            LoginPage() // Otherwise, show the Login screen
        }
    }
}