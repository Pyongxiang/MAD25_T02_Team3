package np.ict.mad.madassg2025

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class ProfilePage : ComponentActivity() {
    private val firebaseHelper = FirebaseHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var username by remember { mutableStateOf("Loading...") }
            var email by remember { mutableStateOf("Loading...") }
            var isLoading by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                firebaseHelper.getUserProfile(
                    onSuccess = { data ->
                        username = data?.get("username")?.toString() ?: "No Username"
                        email = data?.get("email")?.toString() ?: "No Email"
                        isLoading = false
                    },
                    onFailure = { error ->
                        username = "Error"
                        email = error
                        isLoading = false
                    }
                )
            }

            Surface(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize()) {

                    // --- TOP LEFT BACK BUTTON ---
                    IconButton(
                        onClick = {
                            val intent = Intent(this@ProfilePage, HomePage::class.java)
                            startActivity(intent)
                            finish()
                        },
                        modifier = Modifier
                            .padding(top = 16.dp, start = 8.dp)
                            .align(Alignment.TopStart)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to Home",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Spacer(modifier = Modifier.height(60.dp))

                        // --- DEFAULT AVATAR ---
                        Surface(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Default Avatar",
                                    modifier = Modifier.size(80.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        if (isLoading) {
                            CircularProgressIndicator()
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(text = "Username:", style = MaterialTheme.typography.labelLarge)
                                    Text(text = username, style = MaterialTheme.typography.bodyLarge)

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(text = "Email:", style = MaterialTheme.typography.labelLarge)
                                    Text(text = email, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val intent = Intent(this@ProfilePage, SettingsActivity::class.java)
                                startActivity(intent)
                            }
                        ) {
                            Text("Settings")
                        }

                        Spacer(modifier = Modifier.height(12.dp))


                        // --- LOGOUT BUTTON ---
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Red,
                                contentColor = Color.White
                            ),
                            onClick = {
                                firebaseHelper.signOut()

                                // CLEAR STORED PREFERENCES
                                val prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                                prefs.edit().putBoolean("remember", false).apply()

                                // 3. Redirect to Login and Clear Stack
                                val intent = Intent(this@ProfilePage, MainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                            }
                        ) {
                            Text("Logout")
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}