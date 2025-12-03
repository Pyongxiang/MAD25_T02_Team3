package np.ict.mad.madassg2025

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class FirebaseHelper {
    // getting the "keys" to Firebase's authentication system
    private val auth: FirebaseAuth = Firebase.auth

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

        // Validate password is at least 6 characters
        if (password.length < 6) {
            onFailure("Password must be at least 6 characters")
            return
        }

        // Call Firebase to create account
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                // Success! Account created
                onSuccess()
            }
            .addOnFailureListener { exception ->
                // Failed! Show error message
                onFailure(exception.message ?: "Sign up failed")
            }
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
}