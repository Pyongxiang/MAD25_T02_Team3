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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GroupAdd
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
            var chatSearchQuery by remember { mutableStateOf("") }
            val chatRooms = remember { mutableStateListOf<Map<String, Any>>() }
            val myId = firebaseHelper.getCurrentUser()?.uid ?: ""

            LaunchedEffect(Unit) {
                firebaseHelper.listenToChatRooms { rooms ->
                    val visibleRooms = rooms.filter { room ->
                        val hiddenFrom = room["hiddenFrom"] as? List<String> ?: emptyList()
                        !hiddenFrom.contains(myId)
                    }
                    chatRooms.clear()
                    chatRooms.addAll(visibleRooms)
                }
            }

            Scaffold(
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = {
                            val intent = Intent(this@MessagePage, CreateGroupActivity::class.java)
                            startActivity(intent)
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.GroupAdd, contentDescription = "Create Group Chat")
                    }
                }
            ) { paddingValues ->
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(text = "Buddy Chats", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(text = "Weather Buddies", fontSize = 12.sp, color = Color.Gray)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

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
                                    val isGroup = room["isGroup"] as? Boolean ?: false
                                    if (isGroup) {
                                        (room["groupName"] as? String)?.contains(chatSearchQuery, ignoreCase = true) == true
                                    } else {
                                        val usernames = room["usernames"] as? Map<String, String> ?: emptyMap()
                                        usernames.values.any { it.contains(chatSearchQuery, ignoreCase = true) }
                                    }
                                }

                                items(filteredRooms) { room ->
                                    val isGroup = room["isGroup"] as? Boolean ?: false
                                    val chatId = room["chatId"]?.toString() ?: ""
                                    val lastMsg = room["lastMessage"]?.toString() ?: "No messages"

                                    // Extract unread count
                                    val unreadMap = room["unreadCounts"] as? Map<String, Long> ?: emptyMap()
                                    val count = unreadMap[myId]?.toInt() ?: 0

                                    // Resolve Display Name
                                    val displayName = if (isGroup) {
                                        room["groupName"]?.toString() ?: "Group Chat"
                                    } else {
                                        val usernames = room["usernames"] as? Map<String, String> ?: emptyMap()
                                        usernames.entries.find { it.key != myId }?.value ?: "Unknown"
                                    }

                                    ChatBuddyCard(
                                        name = displayName,
                                        lastMessage = lastMsg,
                                        unreadCount = count,
                                        isGroup = isGroup,
                                        onClick = {
                                            firebaseHelper.markChatAsRead(chatId)
                                            val intent = Intent(this@MessagePage, ChatRoomActivity::class.java)
                                            intent.putExtra("CHAT_ID", chatId)
                                            intent.putExtra("FRIEND_NAME", displayName)
                                            intent.putExtra("IS_GROUP", isGroup)
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
}

@Composable
fun ChatBuddyCard(name: String, lastMessage: String, unreadCount: Int, isGroup: Boolean, onClick: () -> Unit) {
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
            Surface(
                modifier = Modifier.size(50.dp),
                shape = CircleShape,
                color = if (isGroup) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

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

            if (unreadCount > 0) {
                Box(
                    modifier = Modifier.padding(start = 8.dp).size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = if (unreadCount > 9) "9+" else unreadCount.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}