package com.indeavour.coreader.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
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
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var showJoinDialog by remember { mutableStateOf(false) }
    var groupCode by remember { mutableStateOf("") }
    var groupToLeave by remember { mutableStateOf<GroupModel?>(null) }

    if (groupToLeave != null) {
        AlertDialog(
            onDismissRequest = { groupToLeave = null },
            title = { Text("Leave Group", color = MaterialTheme.colorScheme.secondary) },
            text = { Text("Are you sure you want to leave '${groupToLeave?.groupName}'? Your progress will still be saved personally, but you won't be able to see others' progress in this group.") },
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
                    colors = ButtonDefaults.buttonColors(containerColor = Teal)
                ) {
                    Text("Leave", color = MaterialTheme.colorScheme.secondary)
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToLeave = null }) {
                    Text("Cancel", color = Teal)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("Join Group", color = MaterialTheme.colorScheme.secondary) },
            text = {
                Column {
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
                        )
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
                    colors = ButtonDefaults.buttonColors(containerColor = Teal)
                ) {
                    Text("Join", color = MaterialTheme.colorScheme.secondary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showJoinDialog = false }
                ) {
                    Text("Cancel", color = Teal)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Group", color = MaterialTheme.colorScheme.secondary) },
            text = {
                Column {
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
                        )
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
                    colors = ButtonDefaults.buttonColors(containerColor = Teal)
                ) {
                    Text("Create", color = MaterialTheme.colorScheme.secondary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateDialog = false }
                ) {
                    Text("Cancel", color = Teal)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
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
                            IconButton(onClick = { groupToLeave = group }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Leave Group",
                                    tint = if (isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                                )
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
