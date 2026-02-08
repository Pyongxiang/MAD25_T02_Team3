package np.ict.mad.madassg2025

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class CreateGroupActivity : ComponentActivity() {
    private val firebaseHelper = FirebaseHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            var groupName by remember { mutableStateOf("") }
            val friendsList = remember { mutableStateListOf<UserAccount>() }

            val selectedMembers = remember { mutableStateMapOf<String, Boolean>() }


            // load friends list
            LaunchedEffect(Unit) {
                firebaseHelper.listenToFriendsList { friends ->
                    friendsList.clear()
                    friendsList.addAll(friends)
                }
            }

            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Text("New Group Chat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // input group name
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Group Name") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Group, null) },
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Select Friends", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(friendsList) { friend ->
                            val isSelected = selectedMembers[friend.uid] ?: false

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                onClick = { selectedMembers[friend.uid] = !isSelected }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(friend.username, fontWeight = FontWeight.SemiBold)
                                        Text(friend.email, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { selectedMembers[friend.uid] = it }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // create Button
                    Button(
                        onClick = {
                            val memberUids = selectedMembers.filter { it.value }.keys.toList()

                            if (groupName.isBlank()) {
                                Toast.makeText(context, "Please enter a group name", Toast.LENGTH_SHORT).show()
                            } else if (memberUids.isEmpty()) {
                                Toast.makeText(context, "Select at least one friend", Toast.LENGTH_SHORT).show()
                            } else {
                                firebaseHelper.createGroupChat(groupName, memberUids) {
                                    Toast.makeText(context, "Group Created!", Toast.LENGTH_SHORT).show()
                                    finish()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Create Group")
                    }
                }
            }
        }
    }
}