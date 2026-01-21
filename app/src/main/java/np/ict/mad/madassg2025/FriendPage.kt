package np.ict.mad.madassg2025

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Data class to represent a user in the list
data class UserAccount(
    val uid: String = "",
    val username: String = "",
    val email: String = ""
)

class FriendPage : ComponentActivity() {
    private val firebaseHelper = FirebaseHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var searchQuery by remember { mutableStateOf("") }
            val searchResults = remember { mutableStateListOf<UserAccount>() }
            val pendingRequests = remember { mutableStateListOf<UserAccount>() }
            val currentFriends = remember { mutableStateListOf<UserAccount>() }

            // --- NEW STATE: Track requests I have sent ---
            val sentRequestIds = remember { mutableStateListOf<String>() }

            // --- DIALOG STATES ---
            var showRemoveDialog by remember { mutableStateOf(false) }
            var userToRemove by remember { mutableStateOf<UserAccount?>(null) }

            // Listeners for real-time updates
            LaunchedEffect(Unit) {
                // People who want to be my friend
                firebaseHelper.listenToFriendRequests { requests ->
                    pendingRequests.clear()
                    pendingRequests.addAll(requests)
                }

                // People I am already friends with
                firebaseHelper.listenToFriendsList { friends ->
                    currentFriends.clear()
                    currentFriends.addAll(friends)
                }

                // People I have sent a request to
                firebaseHelper.listenToSentRequests { sentIds ->
                    sentRequestIds.clear()
                    sentRequestIds.addAll(sentIds)
                }
            }

            // --- CONFIRMATION DIALOG ---
            if (showRemoveDialog && userToRemove != null) {
                AlertDialog(
                    onDismissRequest = { showRemoveDialog = false },
                    title = { Text(text = "Remove Friend") },
                    text = { Text(text = "Are you sure you want to remove ${userToRemove?.username}?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                firebaseHelper.removeFriend(userToRemove!!.uid,
                                    onSuccess = {
                                        showRemoveDialog = false
                                        userToRemove = null
                                    },
                                    onFailure = { /* Handle error */ }
                                )
                            }
                        ) {
                            Text("Yes", color = Color.Red)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRemoveDialog = false }) {
                            Text("No")
                        }
                    }
                )
            }

            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

                    // --- TOP BAR ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            val intent = Intent(this@FriendPage, HomePage::class.java)
                            startActivity(intent)
                            finish()
                        }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back to Home",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = "Friends",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    // --- SEARCH BAR ---
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { newValue ->
                            searchQuery = newValue
                            if (newValue.length >= 3) {
                                firebaseHelper.searchUsers(newValue, onSuccess = { results ->
                                    searchResults.clear()
                                    searchResults.addAll(results)
                                }, onFailure = {})
                            } else {
                                searchResults.clear()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search by unique username...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                        // --- SECTION 1: SEARCH RESULTS ---
                        if (searchQuery.isNotEmpty()) {
                            item { SectionTitle("Search Results") }
                            items(searchResults) { user ->
                                // Check status to decide button text
                                val isAlreadyFriend = currentFriends.any { it.uid == user.uid }
                                val isSent = sentRequestIds.contains(user.uid)

                                val buttonText = when {
                                    isAlreadyFriend -> "Friend"
                                    isSent -> "Pending"
                                    else -> "Add"
                                }

                                UserCard(user, actionText = buttonText) {
                                    when (buttonText) {
                                        "Add" -> firebaseHelper.sendFriendRequest(user, onSuccess = {}, onFailure = {})
                                        "Pending" -> firebaseHelper.cancelFriendRequest(user.uid) {}
                                        "Friend" -> { /* Optional: show profile */ }
                                    }
                                }
                            }
                        } else {
                            // --- SECTION 2: INCOMING REQUESTS ---
                            if (pendingRequests.isNotEmpty()) {
                                item { SectionTitle("Friend Requests") }
                                items(pendingRequests) { user ->
                                    RequestActionCard(user,
                                        onAccept = { firebaseHelper.acceptFriendRequest(user, onSuccess = {}, onFailure = {}) },
                                        onDeny = { firebaseHelper.denyFriendRequest(user.uid) {} }
                                    )
                                }
                            }

                            // --- SECTION 3: MY FRIENDS ---
                            item { SectionTitle("My Friends") }
                            if (currentFriends.isEmpty() && pendingRequests.isEmpty()) {
                                item {
                                    Text("No friends yet. Search to add some!",
                                        modifier = Modifier.padding(8.dp),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            items(currentFriends) { friend ->
                                UserCard(friend, actionText = "Remove") {
                                    userToRemove = friend
                                    showRemoveDialog = true
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
fun UserCard(user: UserAccount, actionText: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, null, modifier = Modifier.size(24.dp))
            }

            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(user.username, fontWeight = FontWeight.SemiBold)
                Text(user.email, style = MaterialTheme.typography.bodySmall)
            }

            // Button style changes if it is "Pending" or "Friend"
            val isNeutral = actionText == "Pending" || actionText == "Friend"

            Button(
                onClick = onAction,
                colors = if (isNeutral) ButtonDefaults.buttonColors(containerColor = Color.Gray) else ButtonDefaults.buttonColors()
            ) {
                Text(actionText)
            }
        }
    }
}

@Composable
fun RequestActionCard(user: UserAccount, onAccept: () -> Unit, onDeny: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(user.username, fontWeight = FontWeight.Bold)
                Text("wants to be friends", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onDeny, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) {
                Text("Deny")
            }
            Button(onClick = onAccept) {
                Text("Accept")
            }
        }
    }
}