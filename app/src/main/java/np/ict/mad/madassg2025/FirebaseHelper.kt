package np.ict.mad.madassg2025

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class FirebaseHelper {
    // getting the "keys" to Firebase's authentication system
    private val auth: FirebaseAuth = Firebase.auth
    private val db = FirebaseFirestore.getInstance()

    // ========== FUNCTIONS ==========

    /**
     * Get the currently logged-in user
     *
     * @return FirebaseUser if someone is logged in, null if not
     *
     * Usage: val user = firebaseHelper.getCurrentUser()
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    /**
     * Get the currently logged-in user's email
     *
     * @return Email string if logged in, null if not
     *
     * Usage: val email = firebaseHelper.getCurrentUserEmail()
     */
    fun getCurrentUserEmail(): String? {
        return auth.currentUser?.email
    }

    /**
     * Check if anyone is logged in
     *
     * @return true if logged in, false if not
     *
     * Usage: if (firebaseHelper.isLoggedIn()) { ... }
     */
    fun isLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    /**
     * Create a new user account (Sign Up)
     *
     * @param email User's email address
     * @param password User's password (minimum 6 characters)
     * @param onSuccess Callback - called if sign up succeeds
     * @param onFailure Callback - called if sign up fails, gets error message
     *
     * Usage:
     * firebaseHelper.signUp(
     *     email = "test@example.com",
     *     password = "123456",
     *     onSuccess = { /* Account created! */ },
     *     onFailure = { error -> /* Show error */ }
     * )
     */
    fun signUp(
        email: String,
        password: String,
        username: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        // Basic validation
        if (username.isBlank()) { onFailure("Username cannot be empty"); return }
        if (password.length < 6) { onFailure("Password must be at least 6 characters"); return }

        // This is the "Normalized" version that blocks others
        // e.g., "aDmin" becomes "admin"
        val normalizedUsername = username.lowercase().trim()

        // STEP 1: Check if the character string is already "taken"
        db.collection("users")
            .whereEqualTo("username_lowercase", normalizedUsername)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    // If "admin" exists, this blocks "Admin", "ADMIN", "aDmin", etc.
                    onFailure("The username '$username' is unavailable.")
                } else {
                    // STEP 2: The name is free! Create the Auth account
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener { result ->
                            val userId = result.user?.uid
                            if (userId != null) {
                                // STEP 3: Save the profile with both versions
                                val userProfile = hashMapOf(
                                    "username" to username,           // "aDmin" (for display)
                                    "username_lowercase" to normalizedUsername, // "admin" (for safety)
                                    "email" to email,
                                    "uid" to userId
                                )

                                db.collection("users").document(userId)
                                    .set(userProfile)
                                    .addOnSuccessListener { onSuccess() }
                                    .addOnFailureListener { onFailure("Profile save failed.") }
                            }
                        }
                        .addOnFailureListener { onFailure(it.message ?: "Sign up failed") }
                }
            }
            .addOnFailureListener { onFailure("Error checking username availability.") }
    }

    /**
     * Login with existing account (Sign In)
     *
     * @param email User's email address
     * @param password User's password
     * @param onSuccess Callback - called if login succeeds
     * @param onFailure Callback - called if login fails, gets error message
     *
     * Usage:
     * firebaseHelper.signIn(
     *     email = "test@example.com",
     *     password = "123456",
     *     onSuccess = { /* Logged in! */ },
     *     onFailure = { error -> /* Show error */ }
     * )
     */
    fun signIn(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        // Validate email is not empty
        if (email.isBlank()) {
            onFailure("Email cannot be empty")
            return
        }

        // Validate password is not empty
        if (password.isBlank()) {
            onFailure("Password cannot be empty")
            return
        }

        // Call Firebase to sign in
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                // Success! User is logged in
                onSuccess()
            }
            .addOnFailureListener { exception ->
                // Failed! Show error message
                onFailure(exception.message ?: "Sign in failed")
            }
    }

    /**
     * Sign out current user (Logout)
     *
     * Usage: firebaseHelper.signOut()
     */
    fun signOut() {
        auth.signOut()
    }


    /**
     * Sends a password reset email to the specified email address.
     *
     * @param email The user's email address.
     * @param onSuccess Callback - called if the email is successfully sent.
     * @param onFailure Callback - called if sending fails, gets error message.
     *
     * Usage:
     * firebaseHelper.forgotPassword(
     * email = "test@example.com",
     * onSuccess = { /* Tell the user to check their email */ },
     * onFailure = { error -> /* Show error */ }
     * )
     */

    /**
     * Retrieve user profile details from Firestore
     */
    fun getUserProfile(onSuccess: (Map<String, Any>?) -> Unit, onFailure: (String) -> Unit) {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("users").document(userId)
                .get()
                .addOnSuccessListener { document ->
                    onSuccess(document.data)
                }
                .addOnFailureListener { e ->
                    onFailure(e.message ?: "Failed to fetch profile")
                }
        } else {
            onFailure("No user logged in")
        }
    }
    fun forgotPassword(
        email: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (email.isBlank()) {
            onFailure("Please enter your email address.")
            return
        }

        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                // Note: Firebase often returns a success message even if the email doesn't exist
                // for security reasons (to prevent user enumeration). We will trust the default0
                // success/failure outcome here.
                onFailure(exception.message ?: "Failed to send reset email.")
            }
    }
}

