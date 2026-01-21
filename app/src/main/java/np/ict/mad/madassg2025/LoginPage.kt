package np.ict.mad.madassg2025

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginPage() {
    val context = LocalContext.current

    // Local Storage to handle "Remember Me"
    val prefs = remember { context.getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE) }

    // -- STATE VARIABLES --
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) } // Toggle for Eye Icon
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(false) }

    // toggle decides if the page acts as Login or Sign Up
    var isLoginMode by remember { mutableStateOf(true) }

    val firebaseHelper = remember { FirebaseHelper() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {

        // --- OPTIONAL HOME BYPASS ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = {
                val intent = Intent(context, HomePage::class.java)
                context.startActivity(intent)
            }) {
                Text("Home")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // --- HEADER ---
            Text(
                text = if (isLoginMode) "Welcome Back" else "Create Account",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // --- USERNAME (ONLY FOR SIGN UP) ---
            if (!isLoginMode) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // --- EMAIL INPUT ---
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // --- PASSWORD INPUT (WITH EYE TOGGLE) ---
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                // Switches between Dots and Plain Text
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    val description = if (passwordVisible) "Hide password" else "Show password"

                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = description)
                    }
                }
            )

            // --- REMEMBER ME & FORGOT PASSWORD ROW ---
            if (isLoginMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // LEFT SIDE: Remember Me Checkbox
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it }
                        )
                        Text("Remember Me", style = MaterialTheme.typography.bodySmall)
                    }

                    // RIGHT SIDE: Forgot Password Link
                    TextButton(
                        onClick = {
                            if (email.isBlank()) {
                                errorMessage = "Enter your email to reset the password."
                                return@TextButton
                            }

                            isLoading = true
                            firebaseHelper.forgotPassword(email,
                                onSuccess = {
                                    isLoading = false
                                    errorMessage = null
                                    Toast.makeText(context, "Reset email sent!", Toast.LENGTH_SHORT).show()
                                },
                                onFailure = { error ->
                                    isLoading = false
                                    errorMessage = error
                                }
                            )
                        }
                    ) {
                        Text("Forgot Password?", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // --- ERROR MESSAGE ---
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- ACTION BUTTON (LOGIN / SIGN UP) ---
            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null

                    if (isLoginMode) {
                        firebaseHelper.signIn(email, password,
                            onSuccess = {
                                isLoading = false

                                // === SAVE PREFERENCE TO LOCAL STORAGE ===
                                prefs.edit().putBoolean("remember", rememberMe).apply()

                                Toast.makeText(context, "Login Successful!", Toast.LENGTH_SHORT).show()
                                val intent = Intent(context, HomePage::class.java)
                                context.startActivity(intent)
                                (context as? android.app.Activity)?.finish()
                            },
                            onFailure = { error ->
                                isLoading = false
                                errorMessage = error
                            }
                        )
                    } else {
                        firebaseHelper.signUp(
                            email = email,
                            password = password,
                            username = username,
                            onSuccess = {
                                isLoading = false
                                Toast.makeText(context, "Account Created!", Toast.LENGTH_SHORT).show()
                                val intent = Intent(context, HomePage::class.java)
                                context.startActivity(intent)
                                (context as? android.app.Activity)?.finish()
                            },
                            onFailure = { error ->
                                isLoading = false
                                errorMessage = error
                            }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(text = if (isLoginMode) "Login" else "Sign Up")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- TOGGLE TEXT ---
            Text(
                text = if (isLoginMode) "Don't have an account? Sign Up" else "Already have an account? Login",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    isLoginMode = !isLoginMode
                    errorMessage = null
                }
            )
        }
    }
}