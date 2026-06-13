package com.indeavour.coreader.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("Join Group") },
            text = {
                Column {
                    TextField(
                        value = groupCode,
                        onValueChange = { groupCode = it },
                        label = { Text("Group Code") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
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
                    enabled = groupCode.isNotBlank()
                ) {
                    Text("Join")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showJoinDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Group") },
            text = {
                Column {
                    TextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Group Name") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
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
                    enabled = groupName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateDialog = false }
                ) {
                    Text("Cancel")
                }
            }
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
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = group.groupName, style = MaterialTheme.typography.titleLarge)
                            Text(text = "Code: ${group.groupCode}", style = MaterialTheme.typography.bodyMedium)
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
