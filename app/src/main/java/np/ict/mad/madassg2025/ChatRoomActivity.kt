package np.ict.mad.madassg2025

import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
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
        var currentGroupName by mutableStateOf(intent.getStringExtra("FRIEND_NAME") ?: "Group")
        val isGroup = intent.getBooleanExtra("IS_GROUP", false)

        setContent {
            val context = LocalContext.current
            var messageText by remember { mutableStateOf("") }
            val messages = remember { mutableStateListOf<ChatMessage>() }
            val myId = firebaseHelper.getCurrentUser()?.uid ?: ""

            // --- UI STATES ---
            var showMenu by remember { mutableStateOf(false) }
            var showDeleteDialog by remember { mutableStateOf(false) }
            var showGroupSummary by remember { mutableStateOf(false) }
            var showEditNameDialog by remember { mutableStateOf(false) }
            var showAddMemberDialog by remember { mutableStateOf(false) } // NEW

            var newNameInput by remember { mutableStateOf(currentGroupName) }
            val participants = remember { mutableStateListOf<UserAccount>() }
            val myFriends = remember { mutableStateListOf<UserAccount>() } // NEW
            var leaderId by remember { mutableStateOf("") }

            // --- REAL-TIME UPDATES ---
            LaunchedEffect(chatId) {
                if (chatId.isNotEmpty()) {
                    firebaseHelper.markChatAsRead(chatId)
                    firebaseHelper.listenToMessages(chatId) { newList ->
                        messages.clear()
                        messages.addAll(newList)
                    }

                    if (isGroup) {
                        firebaseHelper.listenToRoomMetadata(chatId) { data ->
                            currentGroupName = data["groupName"] as? String ?: currentGroupName
                            leaderId = data["groupLeaderId"] as? String ?: ""
                        }

                        firebaseHelper.getGroupParticipants(chatId) { list ->
                            participants.clear()
                            participants.addAll(list)
                        }

                        // Load friends to check who can be added
                        firebaseHelper.listenToFriendsList { friends ->
                            myFriends.clear()
                            myFriends.addAll(friends)
                        }
                    }
                }
            }

            // --- ADD MEMBER DIALOG ---
            if (showAddMemberDialog) {
                // Filter friends: Only show friends NOT already in participants
                val addableFriends = myFriends.filter { friend ->
                    participants.none { it.uid == friend.uid }
                }

                AlertDialog(
                    onDismissRequest = { showAddMemberDialog = false },
                    title = { Text("Add to Group") },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (addableFriends.isEmpty()) {
                                Text("All your friends are already in this group!", style = MaterialTheme.typography.bodySmall)
                            } else {
                                LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                                    items(addableFriends) { friend ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    firebaseHelper.addMemberToGroup(chatId, friend)
                                                    showAddMemberDialog = false
                                                    Toast.makeText(context, "${friend.username} added!", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.PersonAdd, null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(12.dp))
                                            Text(friend.username)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showAddMemberDialog = false }) { Text("Cancel") }
                    }
                )
            }

            // --- EDIT GROUP NAME DIALOG ---
            if (showEditNameDialog) {
                AlertDialog(
                    onDismissRequest = { showEditNameDialog = false },
                    title = { Text("Edit Group Name") },
                    text = {
                        OutlinedTextField(
                            value = newNameInput,
                            onValueChange = { newNameInput = it },
                            label = { Text("New Name") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            if (newNameInput.isNotBlank()) {
                                firebaseHelper.updateGroupName(chatId, newNameInput) {
                                    showEditNameDialog = false
                                }
                            }
                        }) { Text("Update") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEditNameDialog = false }) { Text("Cancel") }
                    }
                )
            }

            // --- GROUP SUMMARY DIALOG ---
            if (showGroupSummary && isGroup) {
                AlertDialog(
                    onDismissRequest = { showGroupSummary = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = currentGroupName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { showEditNameDialog = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Name", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val leader = participants.find { it.uid == leaderId }
                            val membersOnly = participants.filter { it.uid != leaderId }

                            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                item { SectionHeader("Group Leader") }
                                leader?.let {
                                    item { ParticipantRow(it, isMe = it.uid == myId, isLeader = true) }
                                }

                                item { Spacer(Modifier.height(16.dp)) }

                                // UPDATED SECTION HEADER WITH ADD BUTTON
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SectionHeader("Members")
                                        IconButton(
                                            onClick = { showAddMemberDialog = true },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.AddCircle, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }

                                items(membersOnly) { member ->
                                    ParticipantRow(member, isMe = member.uid == myId, isLeader = false)
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

            // --- MAIN CHAT UI (DELETE DIALOG & SCAFFOLD) ---
            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Delete Chat?") },
                    text = { Text("This will hide the chat locally. Others will still see it.") },
                    confirmButton = {
                        TextButton(onClick = {
                            firebaseHelper.deleteChatForMe(chatId)
                            showDeleteDialog = false
                            finish()
                        }) { Text("Delete", color = Color.Red) }
                    },
                    dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
                )
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = isGroup) { showGroupSummary = true }
                            ) {
                                Text(text = currentGroupName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(
                                    text = if (isGroup) "Group Info" else "Online",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isGroup) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) { Icon(Icons.Default.ArrowBack, null) }
                        },
                        actions = {
                            IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Delete Chat") },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) },
                                    onClick = { showMenu = false; showDeleteDialog = true }
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    Surface(tonalElevation = 2.dp, modifier = Modifier.imePadding()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding(),
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
                                        firebaseHelper.sendMessage(chatId, messageText) { messageText = "" }
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                                modifier = Modifier.size(48.dp)
                            ) { Icon(Icons.Default.Send, null, tint = Color.White) }
                        }
                    }
                }
            ) { padding ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
                ) {
                    items(messages) { msg ->
                        ChatBubble(message = msg, isMe = msg.senderId == myId, isGroupChat = isGroup)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.ExtraBold
    )
}

@Composable
fun ParticipantRow(user: UserAccount, isMe: Boolean, isLeader: Boolean) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isLeader) Icons.Default.Star else Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (isLeader) Color(0xFFFFB300) else Color.Gray
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = if (isMe) "${user.username} (You)" else user.username,
            fontSize = 14.sp,
            fontWeight = if (isLeader) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun ChatBubble(message: ChatMessage, isMe: Boolean, isGroupChat: Boolean) {
    val timeString = remember(message.timestamp) { formatTimestamp(message.timestamp) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
        Text(text = timeString, style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp))
    }
}

fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}