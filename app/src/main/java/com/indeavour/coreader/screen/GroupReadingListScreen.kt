package com.indeavour.coreader.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.indeavour.coreader.AppUtils
import com.indeavour.coreader.model.firebase.GroupReadingListBook
import com.indeavour.coreader.model.firebase.GroupReadingListComment
import com.indeavour.coreader.ui.theme.Teal
import com.indeavour.coreader.viewmodel.GroupReadingListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupReadingListScreen(
    onBack: () -> Unit,
    routeToManageGroup: () -> Unit
) {
    val viewModel: GroupReadingListViewModel = viewModel()
    val activeGroupCode by viewModel.activeGroupCode.collectAsState()
    val groupName by viewModel.groupName.collectAsState()
    val books by viewModel.books.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val context = LocalContext.current
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    var showAddDialog by remember { mutableStateOf(false) }
    var bookTitle by remember { mutableStateOf("") }
    var bookAuthor by remember { mutableStateOf("") }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    "Propose Next Book",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            },
            text = {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    TextField(
                        value = bookTitle,
                        onValueChange = { bookTitle = it },
                        label = { Text("Book Title") },
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
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = bookAuthor,
                        onValueChange = { bookAuthor = it },
                        label = { Text("Author") },
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
                        if (bookTitle.isNotBlank()) {
                            viewModel.addProposedBook(bookTitle, bookAuthor) { success ->
                                if (success) {
                                    showAddDialog = false
                                    bookTitle = ""
                                    bookAuthor = ""
                                } else {
                                    AppUtils.showToast(context, "Failed to propose book")
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Text("Propose", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = Teal, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group Next Book Selection", color = MaterialTheme.colorScheme.secondary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.secondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Teal)
            )
        },
        floatingActionButton = {
            if (!activeGroupCode.isNullOrBlank()) {
                FloatingActionButton(onClick = { showAddDialog = true }, containerColor = Teal) {
                    Icon(Icons.Default.Add, contentDescription = "Propose Book", tint = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    ) { padding ->
        if (activeGroupCode.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No Active Group",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Teal
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "You are not currently in an active group.\nJoin or select a group to collaborate on your next book!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = routeToManageGroup,
                            colors = ButtonDefaults.buttonColors(containerColor = Teal)
                        ) {
                            Text("Manage Group", color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = if (groupName.isNotBlank()) "Group: $groupName" else "Group Code: $activeGroupCode",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Teal
                    )
                }

                if (books.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No proposed books yet.\nTap + to propose the next book for your group to read!",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    items(books, key = { it.id }) { book ->
                        GroupReadingListCard(
                            book = book,
                            currentUid = currentUid,
                            isAdmin = isAdmin,
                            canDeleteBook = isAdmin || book.addedByUid == currentUid,
                            onVote = { viewModel.toggleVote(book.id) },
                            onDeleteBook = { viewModel.removeBook(book.id) },
                            onAddComment = { commentText -> viewModel.addComment(book.id, commentText) },
                            onDeleteComment = { comment -> viewModel.removeComment(book.id, comment) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GroupReadingListCard(
    book: GroupReadingListBook,
    currentUid: String,
    isAdmin: Boolean,
    canDeleteBook: Boolean,
    onVote: () -> Unit,
    onDeleteBook: () -> Unit,
    onAddComment: (String) -> Unit,
    onDeleteComment: (GroupReadingListComment) -> Unit
) {
    var expandedComments by remember { mutableStateOf(false) }
    var commentInput by remember { mutableStateOf("") }
    val hasVoted = book.votes.contains(currentUid)
    val voteCount = book.votes.size
    val commentCount = book.comments.size

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (book.author.isNotBlank()) {
                        Text(book.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                    if (book.addedByUsername.isNotBlank()) {
                        Text(
                            "Proposed by ${book.addedByUsername}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Teal
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onVote) {
                        Icon(
                            imageVector = if (hasVoted) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Vote",
                            tint = Teal
                        )
                    }
                    Text(
                        text = "$voteCount",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Teal,
                        modifier = Modifier.padding(end = 4.dp)
                    )

                    IconButton(onClick = { expandedComments = !expandedComments }) {
                        Icon(
                            imageVector = if (commentCount > 0) Icons.AutoMirrored.Filled.Comment else Icons.AutoMirrored.Outlined.Comment,
                            contentDescription = "Comments",
                            tint = Teal
                        )
                    }
                    if (commentCount > 0) {
                        Text(
                            text = "$commentCount",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Teal,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }

                    if (canDeleteBook) {
                        IconButton(onClick = onDeleteBook) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            if (expandedComments) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                if (book.comments.isEmpty()) {
                    Text(
                        "No comments yet. Be the first to comment!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        book.comments.forEach { comment ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = comment.username.ifBlank { "User" },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Teal
                                    )
                                    Text(
                                        text = comment.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (comment.userId == currentUid || isAdmin) {
                                    IconButton(
                                        onClick = { onDeleteComment(comment) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Delete comment",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = commentInput,
                        onValueChange = { commentInput = it },
                        placeholder = { Text("Add a comment...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedIndicatorColor = Teal,
                            cursorColor = Teal
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (commentInput.isNotBlank()) {
                                onAddComment(commentInput)
                                commentInput = ""
                            }
                        },
                        enabled = commentInput.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send comment",
                            tint = if (commentInput.isNotBlank()) Teal else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}
