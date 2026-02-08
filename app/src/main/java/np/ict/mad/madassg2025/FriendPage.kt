package np.ict.mad.madassg2025

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            val context = LocalContext.current
            var searchQuery by remember { mutableStateOf("") }
            val searchResults = remember { mutableStateListOf<UserAccount>() }
            val pendingRequests = remember { mutableStateListOf<UserAccount>() }
            val currentFriends = remember { mutableStateListOf<UserAccount>() }
            val sentRequestIds = remember { mutableStateListOf<String>() }

            // --- DIALOG STATES ---
            var showRemoveDialog by remember { mutableStateOf(false) }
            var userToRemove by remember { mutableStateOf<UserAccount?>(null) }
            var showProfileDialog by remember { mutableStateOf(false) }
            var selectedUserForProfile by remember { mutableStateOf<UserAccount?>(null) }

            val selectedUserFavorites = remember { mutableStateListOf<Map<String, Any>>() }
            val myFavorites = remember { mutableStateListOf<Map<String, Any>>() }

            val myId = firebaseHelper.getCurrentUser()?.uid ?: ""

            // 1. Initial Data Listeners
            LaunchedEffect(Unit) {
                firebaseHelper.listenToFriendRequests { pendingRequests.clear(); pendingRequests.addAll(it) }
                firebaseHelper.listenToFriendsList { currentFriends.clear(); currentFriends.addAll(it) }
                firebaseHelper.listenToSentRequests { sentRequestIds.clear(); sentRequestIds.addAll(it) }

                if (myId.isNotEmpty()) {
                    firebaseHelper.listenToFavourites(myId, { favs ->
                        myFavorites.clear(); myFavorites.addAll(favs)
                    }, {})
                }
            }

            // 2. Search Logic Trigger
            LaunchedEffect(searchQuery) {
                val query = searchQuery.trim()
                if (query.length >= 3) {
                    firebaseHelper.searchUsers(query, onSuccess = { results ->
                        searchResults.clear()
                        searchResults.addAll(results.filter { it.uid != myId })
                    }, onFailure = {})
                } else {
                    searchResults.clear()
                }
            }

            // --- PROFILE DETAIL DIALOG ---
            if (showProfileDialog && selectedUserForProfile != null) {
                LaunchedEffect(selectedUserForProfile) {
                    firebaseHelper.listenToFavourites(selectedUserForProfile!!.uid, { favs ->
                        selectedUserFavorites.clear(); selectedUserFavorites.addAll(favs)
                    }, {})
                }

                AlertDialog(
                    onDismissRequest = { showProfileDialog = false },
                    title = {
                        Column {
                            Text(text = selectedUserForProfile!!.username, fontWeight = FontWeight.Bold)
                            Text(text = selectedUserForProfile!!.email, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                            Text("Favourite Locations", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            if (selectedUserFavorites.isEmpty()) {
                                Text("No favorites saved yet.", color = Color.Gray)
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(selectedUserFavorites) { fav ->
                                        val locName = fav["name"]?.toString() ?: "Unknown"
                                        val lat = (fav["lat"] as? Double) ?: 0.0
                                        val lon = (fav["lon"] as? Double) ?: 0.0
                                        val isAlreadyMyFavorite = myFavorites.any { (it["lat"] == lat && it["lon"] == lon) }
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        ) {
                                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                                Text(locName, modifier = Modifier.weight(1f).padding(start = 8.dp), fontSize = 14.sp)
                                                IconButton(onClick = {
                                                    if (!isAlreadyMyFavorite) {
                                                        firebaseHelper.addFavourite(myId, locName, lat, lon, {
                                                            Toast.makeText(context, "Added!", Toast.LENGTH_SHORT).show()
                                                        }, {})
                                                    }
                                                }) {
                                                    Icon(
                                                        imageVector = if (isAlreadyMyFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                        contentDescription = null,
                                                        tint = if (isAlreadyMyFavorite) Color(0xFFE91E63) else Color.Gray
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showProfileDialog = false }) { Text("Close") } }
                )
            }

            // --- REMOVE FRIEND DIALOG ---
            if (showRemoveDialog && userToRemove != null) {
                AlertDialog(
                    onDismissRequest = { showRemoveDialog = false },
                    title = { Text("Remove Friend") },
                    text = { Text("Are you sure you want to remove ${userToRemove?.username}?") },
                    confirmButton = {
                        TextButton(onClick = {
                            firebaseHelper.removeFriend(userToRemove!!.uid, {
                                showRemoveDialog = false
                                Toast.makeText(context, "Friend Removed", Toast.LENGTH_SHORT).show()
                            }, {})
                        }) { Text("Yes", color = Color.Red) }
                    },
                    dismissButton = { TextButton(onClick = { showRemoveDialog = false }) { Text("No") } }
                )
            }

            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = { finish() }) { Icon(Icons.Default.ArrowBack, null, tint = MaterialTheme.colorScheme.primary) }
                        Text("Friends", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                    }

                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search by username...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (searchQuery.isNotEmpty()) {
                            item { SectionTitle("Search Results") }
                            items(searchResults) { user ->
                                val isFriend = currentFriends.any { it.uid == user.uid }
                                val isSent = sentRequestIds.contains(user.uid)
                                val action = when {
                                    isFriend -> "Friend"
                                    isSent -> "Pending"
                                    else -> "Add"
                                }
                                UserCard(
                                    user = user,
                                    actionText = action,
                                    onAction = {
                                        when (action) {
                                            "Add" -> firebaseHelper.sendFriendRequest(user, {
                                                Toast.makeText(context, "Request Sent!", Toast.LENGTH_SHORT).show()
                                            }, { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() })
                                            "Pending" -> firebaseHelper.cancelFriendRequest(user.uid) {
                                                Toast.makeText(context, "Request Cancelled", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onProfileClick = { selectedUserForProfile = user; showProfileDialog = true },
                                    onMessageClick = {
                                        val chatId = firebaseHelper.getChatId(myId, user.uid)
                                        firebaseHelper.restoreChatVisibility(chatId) {
                                            val intent = Intent(context, ChatRoomActivity::class.java).apply {
                                                putExtra("CHAT_ID", chatId)
                                                putExtra("FRIEND_NAME", user.username)
                                                putExtra("IS_GROUP", false)
                                            }
                                            context.startActivity(intent)
                                        }
                                    }
                                )
                            }
                        } else {
                            if (pendingRequests.isNotEmpty()) {
                                item { SectionTitle("Friend Requests") }
                                items(pendingRequests) { user ->
                                    RequestActionCard(
                                        user = user,
                                        onAccept = {
                                            firebaseHelper.acceptFriendRequest(user, {
                                                Toast.makeText(context, "Buddy Accepted!", Toast.LENGTH_SHORT).show()
                                            }, {})
                                        },
                                        onDeny = {
                                            firebaseHelper.denyFriendRequest(user.uid) {
                                                Toast.makeText(context, "Request Denied", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }
                            item { SectionTitle("My Friends") }
                            items(currentFriends) { friend ->
                                UserCard(
                                    user = friend,
                                    actionText = "Remove",
                                    onAction = { userToRemove = friend; showRemoveDialog = true },
                                    onProfileClick = { selectedUserForProfile = friend; showProfileDialog = true },
                                    onMessageClick = {
                                        val chatId = firebaseHelper.getChatId(myId, friend.uid)
                                        firebaseHelper.restoreChatVisibility(chatId) {
                                            val intent = Intent(context, ChatRoomActivity::class.java).apply {
                                                putExtra("CHAT_ID", chatId)
                                                putExtra("FRIEND_NAME", friend.username)
                                                putExtra("IS_GROUP", false)
                                            }
                                            context.startActivity(intent)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserCard(
    user: UserAccount,
    actionText: String,
    onAction: () -> Unit,
    onProfileClick: () -> Unit,
    onMessageClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f).clickable { onProfileClick() }, verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null)
                }
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text(user.username, fontWeight = FontWeight.SemiBold)
                    Text(user.email, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (actionText == "Remove" || actionText == "Friend") {
                IconButton(onClick = onMessageClick, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Chat, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                }
            }

            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (actionText) {
                        "Remove" -> Color(0xFFD32F2F)
                        "Pending" -> Color(0xFF616161)
                        "Friend" -> Color.Gray
                        else -> MaterialTheme.colorScheme.primary
                    }
                ),
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                enabled = actionText != "Friend"
            ) {
                Text(actionText, color = Color.White, fontSize = 12.sp)
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
            TextButton(onClick = onDeny, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) { Text("Deny") }
            Button(onClick = onAccept) { Text("Accept") }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
}