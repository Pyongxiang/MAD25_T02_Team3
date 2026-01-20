package np.ict.mad.madassg2025

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class ProfilePage : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = "Profile Page", style = MaterialTheme.typography.headlineLarge)

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(text = "User Info will go here later.")

                    Spacer(modifier = Modifier.height(40.dp))

                    Button(onClick = {
                        val intent = Intent(this@ProfilePage, HomePage::class.java)
                        startActivity(intent)
                        finish() // Close profile and go back
                    }) {
                        Text("Back to Home")
                    }
                }
            }
        }
    }
}