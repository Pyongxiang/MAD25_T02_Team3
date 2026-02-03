package np.ict.mad.madassg2025

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration

class FirebaseHelper {
    // getting the "keys" to Firebase's authentication system
    private val auth: FirebaseAuth = Firebase.auth
    private val db = FirebaseFirestore.getInstance()

    // ========== FUNCTIONS ==========

    // Get currently logged in user
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    // Get currently logged in user email
    fun getCurrentUserEmail(): String? {
        return auth.currentUser?.email
    }

   // Check if anyone is logged in
    fun isLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    // Create new user by signing up
    fun signUp(
        email: String,
        password: String,
        username: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        // Data validation
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
                                    "username" to username,
                                    "username_lowercase" to normalizedUsername,
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

   // Log in with existing account
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

    // Sign out logged in account
    fun signOut() {
        auth.signOut()
    }

    // Send password reset email to email used when signing up
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

    // Get userprofile for friends page
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

    // Send a friend request
    fun sendFriendRequest(targetUser: UserAccount, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val currentUserId = auth.currentUser?.uid ?: return

        // Create the unique ID based on the sender and receiver
        val requestId = "${currentUserId}_${targetUser.uid}"

        // STEP 1: Check if this specific request already exists
        db.collection("friend_requests").document(requestId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // If the document exists, a request is already pending
                    onFailure("Request already sent to this user!")
                } else {
                    // STEP 2: The request is new, so we fetch your profile info to "stamp" the request
                    getUserProfile(onSuccess = { profile ->
                        val myUsername = profile?.get("username")?.toString() ?: "Someone"
                        val myEmail = profile?.get("email")?.toString() ?: ""

                        val requestData = hashMapOf(
                            "fromId" to currentUserId,
                            "fromUsername" to myUsername,
                            "fromEmail" to myEmail,
                            "toId" to targetUser.uid,
                            "status" to "pending"
                        )

                        // STEP 3: Write the request to the 'friend_requests' collection
                        db.collection("friend_requests").document(requestId)
                            .set(requestData)
                            .addOnSuccessListener { onSuccess() }
                            .addOnFailureListener { onFailure(it.message ?: "Failed to send request") }

                    }, onFailure = {
                        onFailure("Could not fetch your profile to send request")
                    })
                }
            }
            .addOnFailureListener { e ->
                onFailure("Database error: ${e.message}")
            }
    }

    fun cancelFriendRequest(targetUserId: String, onSuccess: () -> Unit) {
        val myId = auth.currentUser?.uid ?: return
        val requestId = "${myId}_$targetUserId"
        db.collection("friend_requests").document(requestId)
            .delete()
            .addOnSuccessListener { onSuccess() }
    }

    // Accept friend request
    fun acceptFriendRequest(friend: UserAccount, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val myId = auth.currentUser?.uid ?: return

        getUserProfile(onSuccess = { profile ->
            val myUsername = profile?.get("username")?.toString() ?: "User"
            val myEmail = profile?.get("email")?.toString() ?: ""

            val batch = db.batch()

            // 1. Add friend to MY list
            val myFriendRef = db.collection("users").document(myId).collection("friends").document(friend.uid)
            batch.set(myFriendRef, friend)

            // 2. Add ME to THEIR list
            val theirFriendRef = db.collection("users").document(friend.uid).collection("friends").document(myId)
            batch.set(theirFriendRef, UserAccount(uid = myId, username = myUsername, email = myEmail))

            // 3. Delete the request (Note: requestId is senderId_receiverId)
            val requestRef = db.collection("friend_requests").document("${friend.uid}_$myId")
            batch.delete(requestRef)

            batch.commit()
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onFailure(it.message ?: "Failed") }
        }, onFailure = { onFailure("Profile error") })
    }

    fun denyFriendRequest(senderId: String, onSuccess: () -> Unit) {
        val myId = auth.currentUser?.uid ?: return
        db.collection("friend_requests").document("${senderId}_$myId")
            .delete()
            .addOnSuccessListener { onSuccess() }
    }

    fun removeFriend(friendId: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val myId = auth.currentUser?.uid ?: return
        val batch = db.batch()

        // 1. Reference to the friend in my list
        val myFriendRef = db.collection("users").document(myId).collection("friends").document(friendId)
        batch.delete(myFriendRef)

        // 2. Reference to me in the friend's list
        val theirFriendRef = db.collection("users").document(friendId).collection("friends").document(myId)
        batch.delete(theirFriendRef)

        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it.message ?: "Failed to remove friend") }
    }

    // Search user by their username
    fun searchUsers(query: String, onSuccess: (List<UserAccount>) -> Unit, onFailure: (String) -> Unit) {
        val normalizedQuery = query.lowercase().trim()
        val currentUserId = auth.currentUser?.uid

        db.collection("users")
            .whereEqualTo("username_lowercase", normalizedQuery)
            .get()
            .addOnSuccessListener { documents ->
                val userList = mutableListOf<UserAccount>()
                for (doc in documents) {
                    val user = doc.toObject(UserAccount::class.java)
                    // Don't show yourself in the search results!
                    if (user.uid != currentUserId) {
                        userList.add(user)
                    }
                }
                onSuccess(userList)
            }
            .addOnFailureListener { e ->
                onFailure(e.message ?: "Search failed")
            }
    }

    fun listenToSentRequests(onUpdate: (List<String>) -> Unit) {
        val myId = auth.currentUser?.uid ?: return
        db.collection("friend_requests")
            .whereEqualTo("fromId", myId)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, _ ->
                // We only need the list of UIDs we sent requests to
                val sentToIds = snapshot?.documents?.mapNotNull { it.getString("toId") } ?: emptyList()
                onUpdate(sentToIds)
            }
    }

    // Get friend requests from the user
    fun listenToFriendRequests(onUpdate: (List<UserAccount>) -> Unit) {
        val myId = auth.currentUser?.uid ?: return
        db.collection("friend_requests")
            .whereEqualTo("toId", myId)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, _ ->
                val requests = snapshot?.documents?.mapNotNull { doc ->
                    UserAccount(
                        uid = doc.getString("fromId") ?: "",
                        username = doc.getString("fromUsername") ?: "",
                        email = doc.getString("fromEmail") ?: "" // Add this line to fetch the email
                    )
                } ?: emptyList()
                onUpdate(requests)
            }
    }

    // Get friend requests sent from another user
    fun listenToFriendsList(onUpdate: (List<UserAccount>) -> Unit) {
        val myId = auth.currentUser?.uid ?: return
        db.collection("users").document(myId).collection("friends")
            .addSnapshotListener { snapshot, _ ->
                val friends = snapshot?.documents?.mapNotNull { it.toObject(UserAccount::class.java) } ?: emptyList()
                onUpdate(friends)
            }
    }

    // ========== FAVOURITES (Firestore) ==========

    // Stable doc id so "same location" overwrites instead of duplicates
    private fun favDocId(lat: Double, lon: Double): String {
        // Firestore doc ids cannot contain "/" etc
        // Using fixed precision reduces duplicates caused by tiny float differences
        val latKey = "%.5f".format(lat).replace(".", "_")
        val lonKey = "%.5f".format(lon).replace(".", "_")
        return "${latKey}_${lonKey}"
    }

    fun listenToFavourites(
        userId: String,
        onUpdate: (List<Map<String, Any>>) -> Unit,
        onFailure: (String) -> Unit
    ): ListenerRegistration {
        return db.collection("users")
            .document(userId)
            .collection("favourites")
            .orderBy("updatedAt") // optional
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onFailure(e.message ?: "Failed to listen to favourites")
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.data } ?: emptyList()
                onUpdate(list)
            }
    }

    fun addFavourite(
        userId: String,
        name: String,
        lat: Double,
        lon: Double,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val docId = favDocId(lat, lon)
        val data = hashMapOf(
            "name" to name,
            "lat" to lat,
            "lon" to lon,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("users")
            .document(userId)
            .collection("favourites")
            .document(docId)
            .set(data)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it.message ?: "Failed to add favourite") }
    }

    fun removeFavourite(
        userId: String,
        lat: Double,
        lon: Double,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val docId = favDocId(lat, lon)

        db.collection("users")
            .document(userId)
            .collection("favourites")
            .document(docId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it.message ?: "Failed to remove favourite") }
    }
}

