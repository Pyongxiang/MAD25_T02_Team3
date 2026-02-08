package np.ict.mad.madassg2025

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MessagePage : ComponentActivity() {
    private val firebaseHelper = FirebaseHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // -- UI STATE --
            var chatSearchQuery by remember { mutableStateOf("") }
            val chatRooms = remember { mutableStateListOf<Map<String, Any>>() }
            val myId = firebaseHelper.getCurrentUser()?.uid ?: ""

            // -- REAL-TIME LISTENER --
            LaunchedEffect(Unit) {
                firebaseHelper.listenToChatRooms { rooms ->
                    chatRooms.clear()
                    chatRooms.addAll(rooms)
                }
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // --- HEADER SECTION ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { finish() }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column {
                            Text(
                                text = "Buddy Chats",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Weather Buddies",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- SEARCH BAR ---
                    OutlinedTextField(
                        value = chatSearchQuery,
                        onValueChange = { chatSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search conversations...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // --- CHAT LIST ---
                    if (chatRooms.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No buddies yet. Accept a request to start chatting!", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val filteredRooms = if (chatSearchQuery.isEmpty()) chatRooms
                            else chatRooms.filter { room ->
                                val usernames = room["usernames"] as? Map<String, String> ?: emptyMap()
                                usernames.values.any { it.contains(chatSearchQuery, ignoreCase = true) }
                            }

                            items(filteredRooms) { room ->
                                val usernames = room["usernames"] as? Map<String, String> ?: emptyMap()
                                val friendName = usernames.entries.find { it.key != myId }?.value ?: "Unknown"
                                val lastMsg = room["lastMessage"]?.toString() ?: "No messages"
                                val chatId = room["chatId"]?.toString() ?: ""

                                // --- UNREAD COUNT LOGIC ---
                                val unreadMap = room["unreadCounts"] as? Map<String, Long> ?: emptyMap()
                                val count = unreadMap[myId]?.toInt() ?: 0

                                ChatBuddyCard(
                                    name = friendName,
                                    lastMessage = lastMsg,
                                    unreadCount = count,
                                    onClick = {
                                        // Reset count in DB immediately on click
                                        firebaseHelper.markChatAsRead(chatId)

                                        val intent = Intent(this@MessagePage, ChatRoomActivity::class.java)
                                        intent.putExtra("CHAT_ID", chatId)
                                        intent.putExtra("FRIEND_NAME", friendName)
                                        startActivity(intent)
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
fun ChatBuddyCard(name: String, lastMessage: String, unreadCount: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Surface(
                modifier = Modifier.size(50.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    text = lastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (unreadCount > 0) MaterialTheme.colorScheme.onSurface else Color.Gray,
                    fontWeight = if (unreadCount > 0) FontWeight.ExtraBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // --- UNREAD BADGE ---
            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}