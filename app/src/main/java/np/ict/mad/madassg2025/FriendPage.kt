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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
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

            // Profile Detail States
            var showProfileDialog by remember { mutableStateOf(false) }
            var selectedUserForProfile by remember { mutableStateOf<UserAccount?>(null) }
            val selectedUserFavorites = remember { mutableStateListOf<Map<String, Any>>() }

            // Current User's favorites to check for "Filled Heart"
            val myFavorites = remember { mutableStateListOf<Map<String, Any>>() }

            val myId = firebaseHelper.getCurrentUser()?.uid ?: ""

            LaunchedEffect(Unit) {
                firebaseHelper.listenToFriendRequests { pendingRequests.clear(); pendingRequests.addAll(it) }
                firebaseHelper.listenToFriendsList { currentFriends.clear(); currentFriends.addAll(it) }
                firebaseHelper.listenToSentRequests { sentRequestIds.clear(); sentRequestIds.addAll(it) }

                // Keep track of my own favorites
                if (myId.isNotEmpty()) {
                    firebaseHelper.listenToFavourites(myId, { favs ->
                        myFavorites.clear()
                        myFavorites.addAll(favs)
                    }, {})
                }
            }

            // --- PROFILE DETAIL DIALOG ---
            if (showProfileDialog && selectedUserForProfile != null) {
                LaunchedEffect(selectedUserForProfile) {
                    firebaseHelper.listenToFavourites(selectedUserForProfile!!.uid, { favs ->
                        selectedUserFavorites.clear()
                        selectedUserFavorites.addAll(favs)
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
                                Text("No favorites saved yet.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(selectedUserFavorites) { fav ->
                                        val locName = fav["name"]?.toString() ?: "Unknown"
                                        val lat = (fav["lat"] as? Double) ?: 0.0
                                        val lon = (fav["lon"] as? Double) ?: 0.0

                                        // Logic to check if I already have this location
                                        val isAlreadyMyFavorite = myFavorites.any {
                                            val myLat = (it["lat"] as? Double) ?: 0.0
                                            val myLon = (it["lon"] as? Double) ?: 0.0
                                            // Matching by coordinates for precision
                                            myLat == lat && myLon == lon
                                        }

                                        // Location Item Boxed
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                    Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(locName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                                }

                                                IconButton(
                                                    onClick = {
                                                        if (myId.isNotEmpty() && !isAlreadyMyFavorite) {
                                                            firebaseHelper.addFavourite(
                                                                userId = myId,
                                                                name = locName,
                                                                lat = lat,
                                                                lon = lon,
                                                                onSuccess = {
                                                                    Toast.makeText(context, "Added to your favorites!", Toast.LENGTH_SHORT).show()
                                                                },
                                                                onFailure = { error ->
                                                                    Toast.makeText(context, "Error: $error", Toast.LENGTH_SHORT).show()
                                                                }
                                                            )
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = if (isAlreadyMyFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                        contentDescription = "Favorite status",
                                                        tint = if (isAlreadyMyFavorite) Color(0xFFE91E63) else Color.Gray,
                                                        modifier = Modifier.size(22.dp)
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

            // --- REMOVE CONFIRMATION DIALOG ---
            if (showRemoveDialog && userToRemove != null) {
                AlertDialog(
                    onDismissRequest = { showRemoveDialog = false },
                    confirmButton = {
                        TextButton(onClick = {
                            firebaseHelper.removeFriend(userToRemove!!.uid, { showRemoveDialog = false }, {})
                        }) { Text("Yes", color = Color.Red) }
                    },
                    dismissButton = { TextButton(onClick = { showRemoveDialog = false }) { Text("No") } },
                    title = { Text("Remove Friend") },
                    text = { Text("Are you sure you want to remove ${userToRemove?.username}?") }
                )
            }

            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        IconButton(onClick = { finish() }) { Icon(Icons.Default.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary) }
                        Text("Friends", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search by username...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        shape = RoundedCornerShape(24.dp)
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
                                UserCard(user, actionText = action, onAction = {
                                    if (action == "Add") firebaseHelper.sendFriendRequest(user, {}, {})
                                }, onProfileClick = {})
                            }
                        } else {
                            if (pendingRequests.isNotEmpty()) {
                                item { SectionTitle("Friend Requests") }
                                items(pendingRequests) { user ->
                                    RequestActionCard(user, onAccept = { firebaseHelper.acceptFriendRequest(user, {}, {}) }, onDeny = {})
                                }
                            }

                            item { SectionTitle("My Friends") }
                            items(currentFriends) { friend ->
                                UserCard(
                                    user = friend,
                                    actionText = "Remove",
                                    onAction = { userToRemove = friend; showRemoveDialog = true },
                                    onProfileClick = { selectedUserForProfile = friend; showProfileDialog = true }
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
fun UserCard(user: UserAccount, actionText: String, onAction: () -> Unit, onProfileClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f).clickable { onProfileClick() }, verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, modifier = Modifier.size(24.dp))
                }
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text(user.username, fontWeight = FontWeight.SemiBold)
                    Text(user.email, style = MaterialTheme.typography.bodySmall)
                }
            }
            val buttonColor = when (actionText) {
                "Remove" -> ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                "Pending", "Friend" -> ButtonDefaults.buttonColors(containerColor = Color.Gray)
                else -> ButtonDefaults.buttonColors()
            }
            Button(onClick = onAction, colors = buttonColor) {
                Text(actionText, color = Color.White)
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
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}