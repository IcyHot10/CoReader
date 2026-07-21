package com.indeavour.coreader.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.indeavour.coreader.AppUtils
import com.indeavour.coreader.model.firebase.GroupModel
import com.indeavour.coreader.ui.theme.Teal
import com.indeavour.coreader.viewmodel.GroupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(onBack: () -> Unit) {
    val groupViewModel: GroupViewModel = viewModel()
    val groups by groupViewModel.groups.collectAsState()
    val activeGroupId by groupViewModel.activeGroupId.collectAsState()
    val usernames by groupViewModel.usernames.collectAsState()
    val context = LocalContext.current
    val currentUserId = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }
    var showCreateDialog by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var showJoinDialog by remember { mutableStateOf(false) }
    var groupCode by remember { mutableStateOf("") }
    var groupToLeave by remember { mutableStateOf<GroupModel?>(null) }
    var memberToRemove by remember { mutableStateOf<Pair<GroupModel, String>?>(null) }
    var menuTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    if (memberToRemove != null) {
        val (group, memberId) = memberToRemove!!
        val memberName = usernames[memberId] ?: "this member"
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { 
                Text(
                    "Remove Member", 
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.secondary 
                ) 
            },
            text = { 
                Text(
                    "Are you sure you want to remove '$memberName' from '${group.groupName}'?",
                    style = MaterialTheme.typography.bodyLarge
                ) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        groupViewModel.removeMember(group.groupCode, memberId) { success ->
                            if (success) {
                                AppUtils.showToast(context, "Removed member")
                            } else {
                                AppUtils.showToast(context, "Failed to remove member")
                            }
                        }
                        memberToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.secondary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) {
                    Text("Cancel", color = Teal, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
        )
    }

    if (groupToLeave != null) {
        AlertDialog(
            onDismissRequest = { groupToLeave = null },
            title = { 
                Text(
                    "Leave Group", 
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.secondary 
                ) 
            },
            text = { 
                Text(
                    "Are you sure you want to leave '${groupToLeave?.groupName}'? Your progress will still be saved personally, but you won't be able to see others' progress in this group.",
                    style = MaterialTheme.typography.bodyLarge
                ) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        groupToLeave?.let { group ->
                            groupViewModel.leaveGroup(group.groupCode) { success ->
                                if (success) {
                                    AppUtils.showToast(context, "Left group")
                                } else {
                                    AppUtils.showToast(context, "Failed to leave group")
                                }
                            }
                        }
                        groupToLeave = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Text("Leave", color = MaterialTheme.colorScheme.secondary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToLeave = null }) {
                    Text("Cancel", color = Teal, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
        )
    }

    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { 
                Text(
                    "Join Group", 
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.secondary 
                ) 
            },
            text = {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    TextField(
                        value = groupCode,
                        onValueChange = { groupCode = it },
                        label = { Text("Group Code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = Teal,
                            cursorColor = Teal
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (groupCode.isNotBlank()) {
                            groupViewModel.joinGroup(groupCode) { success, error ->
                                if (success) {
                                    showJoinDialog = false
                                    groupCode = ""
                                    AppUtils.showToast(context, "Joined group!")
                                } else {
                                    AppUtils.showToast(context, error ?: "Failed to join group")
                                }
                            }
                        }
                    },
                    enabled = groupCode.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Text("Join", color = MaterialTheme.colorScheme.secondary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showJoinDialog = false }
                ) {
                    Text("Cancel", color = Teal, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { 
                Text(
                    "Create New Group", 
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.secondary 
                ) 
            },
            text = {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    TextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Group Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = Teal,
                            cursorColor = Teal
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (groupName.isNotBlank()) {
                            groupViewModel.createGroup(groupName) { success, code ->
                                if (success) {
                                    showCreateDialog = false
                                    groupName = ""
                                    AppUtils.showToast(context, "Group created! Code: $code")
                                } else {
                                    AppUtils.showToast(context, "Failed to create group")
                                }
                            }
                        }
                    },
                    enabled = groupName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Text("Create", color = MaterialTheme.colorScheme.secondary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateDialog = false }
                ) {
                    Text("Cancel", color = Teal, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Groups") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Teal,
                    titleContentColor = MaterialTheme.colorScheme.secondary,
                    navigationIconContentColor = MaterialTheme.colorScheme.secondary
                )
            )
        },
        floatingActionButton = {
            if (groups.isNotEmpty()) {
                FloatingActionButton(onClick = {
                    // Show a menu or just create by default? 
                    // Let's just show Create dialog for now, maybe add a way to join too.
                    showCreateDialog = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Create Group")
                }
            }
        }
    ) { innerPadding ->
        if (groups.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Group Management",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "This screen will allow you to create and manage reading groups.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth()
                ) {
                    Text("Create New Group")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showJoinDialog = true },
                    modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth()
                ) {
                    Text("Join Group")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                items(groups) { group ->
                    val isActive = group.groupCode == activeGroupId
                    val isExpanded = expandedGroups[group.groupCode] ?: false
                    val amIAdmin = group.groupMembers[currentUserId]?.admin == true
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable {
                                groupViewModel.setActiveGroup(group.groupCode)
                                AppUtils.showToast(context, "${group.groupName} is now active")
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = if (isActive) {
                            CardDefaults.cardColors(
                                containerColor = Teal,
                                contentColor = MaterialTheme.colorScheme.secondary
                            )
                        } else {
                            CardDefaults.cardColors()
                        }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = group.groupName, style = MaterialTheme.typography.titleLarge)
                                    Text(text = "Code: ${group.groupCode}", style = MaterialTheme.typography.bodyMedium)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { 
                                        expandedGroups[group.groupCode] = !isExpanded
                                    }) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Show Members",
                                            tint = if (isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    IconButton(onClick = { groupToLeave = group }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Logout,
                                            contentDescription = "Leave Group",
                                            tint = if (isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                            
                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    HorizontalDivider(
                                        color = if (isActive) MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f) 
                                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Members (${group.groupMembers.size})",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isActive) MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    group.groupMembers.keys.forEach { memberId ->
                                        val memberInfo = group.groupMembers[memberId]
                                        val memberName = usernames[memberId] ?: "Loading..."
                                        val isMe = memberId == currentUserId
                                        val isTargetAdmin = memberInfo?.admin == true
                                        
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = if (isActive) MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (isMe) "$memberName (You)" else memberName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f),
                                                fontWeight = if (isMe) androidx.compose.ui.text.font.FontWeight.Bold else null
                                            )
                                            
                                            if (isTargetAdmin) {
                                                Surface(
                                                    color = if (isActive) MaterialTheme.colorScheme.secondary
                                                           else Teal,
                                                    shape = CircleShape,
                                                    modifier = Modifier.padding(start = 8.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Star,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(12.dp),
                                                            tint = if (isActive) Teal else MaterialTheme.colorScheme.surface
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "Admin",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (isActive) Teal else MaterialTheme.colorScheme.surface,
                                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }

                                            if (amIAdmin && !isMe) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Box {
                                                    IconButton(
                                                        onClick = { menuTarget = group.groupCode to memberId },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.MoreVert,
                                                            contentDescription = "Manage Member",
                                                            modifier = Modifier.size(20.dp),
                                                            tint = if (isActive) MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                                                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                        )
                                                    }
                                                    
                                                    DropdownMenu(
                                                        expanded = menuTarget?.first == group.groupCode && menuTarget?.second == memberId,
                                                        onDismissRequest = { menuTarget = null },
                                                        containerColor = MaterialTheme.colorScheme.surface
                                                    ) {
                                                        DropdownMenuItem(
                                                            text = { Text(if (isTargetAdmin) "Revoke Admin" else "Make Admin") },
                                                            onClick = {
                                                                groupViewModel.updateAdminStatus(group.groupCode, memberId, !isTargetAdmin) { success ->
                                                                    if (!success) AppUtils.showToast(context, "Failed to update admin status")
                                                                }
                                                                menuTarget = null
                                                            },
                                                            leadingIcon = { 
                                                                Icon(
                                                                    Icons.Default.Star, 
                                                                    null,
                                                                    tint = if (isTargetAdmin) Teal else MaterialTheme.colorScheme.onSurfaceVariant
                                                                ) 
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("Remove from Group", color = MaterialTheme.colorScheme.error) },
                                                            onClick = {
                                                                memberToRemove = group to memberId
                                                                menuTarget = null
                                                            },
                                                            leadingIcon = { 
                                                                Icon(
                                                                    Icons.Default.PersonRemove, 
                                                                    null, 
                                                                    tint = MaterialTheme.colorScheme.error
                                                                ) 
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { showJoinDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Join Another Group")
                    }
                }
            }
        }
    }
}
