package np.ict.mad.madassg2025

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val firebaseHelper = FirebaseHelper()
        val prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val isRemembered = prefs.getBoolean("remember", false)

        // check if firebase is logged in and if user checked remember me
        if (firebaseHelper.isLoggedIn() && isRemembered) {
            val intent = Intent(this, HomePage::class.java)
            startActivity(intent)
            finish()
            return
        }

        setContent {
            LoginPage()
        }
    }
}