package np.ict.mad.madassg2025

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginPage() {
    val context = LocalContext.current

    // -- STATE VARIABLES --
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // toggle decides if the page acts as Login or Sign Up
    var isLoginMode by remember { mutableStateOf(true) }

    val firebaseHelper = remember { FirebaseHelper() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {

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

            // --- PASSWORD INPUT ---
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                // This hides the password with dots
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                )
            )

            // --- FORGOT PASSWORD LINK (NEW ADDITION) ---
            if (isLoginMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            // 1. Check if email is provided
                            if (email.isBlank()) {
                                errorMessage = "Enter your email to reset the password."
                                return@TextButton
                            }

                            isLoading = true
                            firebaseHelper.forgotPassword(email,
                                onSuccess = {
                                    isLoading = false
                                    errorMessage = null
                                    Toast.makeText(
                                        context,
                                        "Password reset email sent to $email!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                },
                                onFailure = { error ->
                                    isLoading = false
                                    // Often Firebase returns a generic success/failure for security,
                                    // but we show the error if it's a validation issue.
                                    errorMessage = error
                                }
                            )
                        }
                    ) {
                        Text("Forgot Password?")
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
                    errorMessage = null // Clear previous errors

                    if (isLoginMode) {
                        // === LOGIC FOR LOGIN ===
                        firebaseHelper.signIn(email, password,
                            onSuccess = {
                                isLoading = false
                                Toast.makeText(context, "Login Successful!", Toast.LENGTH_SHORT).show()
                                // Navigate to Home
                                val intent = Intent(context, HomePage::class.java)
                                context.startActivity(intent)
                            },
                            onFailure = { error ->
                                isLoading = false
                                errorMessage = error
                            }
                        )
                    } else {
                        // === LOGIC FOR SIGN UP ===
                        firebaseHelper.signUp(
                            email = email,
                            password = password,
                            username = username,
                            onSuccess = {
                                isLoading = false
                                Toast.makeText(context, "Account Created!", Toast.LENGTH_SHORT).show()
                                val intent = Intent(context, HomePage::class.java)
                                context.startActivity(intent)
                            },
                            onFailure = { error ->
                                isLoading = false
                                errorMessage = error
                            }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading // Disable button while loading
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
            // Clicking this switches between Login and Sign Up mode
            Text(
                text = if (isLoginMode) "Don't have an account? Sign Up" else "Already have an account? Login",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    isLoginMode = !isLoginMode
                    errorMessage = null // Clear errors when switching
                }
            )
        }
    }
}