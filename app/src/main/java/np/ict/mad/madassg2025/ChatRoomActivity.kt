package np.ict.mad.madassg2025

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatRoomActivity : ComponentActivity() {
    private val firebaseHelper = FirebaseHelper()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val chatId = intent.getStringExtra("CHAT_ID") ?: ""
        val friendName = intent.getStringExtra("FRIEND_NAME") ?: "Buddy"
        val isGroup = intent.getBooleanExtra("IS_GROUP", false)

        setContent {
            var messageText by remember { mutableStateOf("") }
            val messages = remember { mutableStateListOf<ChatMessage>() }
            val myId = firebaseHelper.getCurrentUser()?.uid ?: ""

            // --- UI STATES ---
            var showMenu by remember { mutableStateOf(false) }
            var showDeleteDialog by remember { mutableStateOf(false) }
            var showGroupSummary by remember { mutableStateOf(false) }
            val participants = remember { mutableStateListOf<UserAccount>() }

            // --- REAL-TIME UPDATES ---
            LaunchedEffect(chatId) {
                if (chatId.isNotEmpty()) {
                    firebaseHelper.markChatAsRead(chatId)
                    firebaseHelper.listenToMessages(chatId) { newList ->
                        messages.clear()
                        messages.addAll(newList)
                    }

                    if (isGroup) {
                        firebaseHelper.getGroupParticipants(chatId) { list ->
                            participants.clear()
                            participants.addAll(list)
                        }
                    }
                }
            }

            // --- GROUP SUMMARY DIALOG ---
            if (showGroupSummary && isGroup) {
                AlertDialog(
                    onDismissRequest = { showGroupSummary = false },
                    title = { Text(text = friendName, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Members", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                            Spacer(Modifier.height(8.dp))

                            Box(modifier = Modifier.heightIn(max = 200.dp)) {
                                LazyColumn {
                                    items(participants) { member ->
                                        Row(
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Person, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                text = if (member.uid == myId) "${member.username} (You)" else member.username,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    firebaseHelper.removeMemberFromGroup(chatId, myId)
                                    showGroupSummary = false
                                    finish()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Leave Group", color = Color.White)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showGroupSummary = false }) { Text("Close") }
                    }
                )
            }

            // --- DELETE CONFIRMATION DIALOG ---
            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Delete Chat?") },
                    text = { Text("This will hide the chat and clear your local history. Others will still see the conversation.") },
                    confirmButton = {
                        TextButton(onClick = {
                            firebaseHelper.deleteChatForMe(chatId)
                            showDeleteDialog = false
                            finish()
                        }) {
                            Text("Delete", color = Color.Red)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                    }
                )
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            // Header is clickable only if it's a group
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = isGroup) { showGroupSummary = true }
                            ) {
                                Text(text = friendName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                if (isGroup) {
                                    Text("Tap for group info", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                } else {
                                    Text("Online", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Delete Chat") },
                                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) },
                                        onClick = {
                                            showMenu = false
                                            showDeleteDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    )
                },
                bottomBar = {
                    Surface(tonalElevation = 2.dp, modifier = Modifier.imePadding()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .navigationBarsPadding(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = messageText,
                                onValueChange = { messageText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Type a message...") },
                                shape = RoundedCornerShape(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            FloatingActionButton(
                                onClick = {
                                    if (messageText.isNotBlank()) {
                                        firebaseHelper.sendMessage(chatId, messageText) {
                                            messageText = ""
                                        }
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                            }
                        }
                    }
                }
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
                ) {
                    items(messages) { msg ->
                        ChatBubble(
                            message = msg,
                            isMe = msg.senderId == myId,
                            isGroupChat = isGroup
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage, isMe: Boolean, isGroupChat: Boolean) {
    val timeString = remember(message.timestamp) {
        formatTimestamp(message.timestamp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        if (isGroupChat && !isMe) {
            Text(
                text = message.senderName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }

        Surface(
            color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(
                topStart = if (isMe || isGroupChat) 16.dp else 4.dp,
                topEnd = if (isMe) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            tonalElevation = 1.dp
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                color = if (isMe) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                fontSize = 15.sp
            )
        }

        Text(
            text = timeString,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
        )
    }
}

fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}