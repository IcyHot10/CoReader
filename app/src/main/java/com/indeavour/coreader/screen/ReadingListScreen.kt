package com.indeavour.coreader.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.indeavour.coreader.AppUtils
import com.indeavour.coreader.model.firebase.ReadingListBook
import com.indeavour.coreader.ui.theme.Teal
import com.indeavour.coreader.viewmodel.ReadingListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingListScreen(onBack: () -> Unit) {
    val viewModel: ReadingListViewModel = viewModel()
    val readingList by viewModel.readingList.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var bookTitle by remember { mutableStateOf("") }
    var bookAuthor by remember { mutableStateOf("") }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { 
                Text(
                    "Add to Reading List", 
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
                            viewModel.addBook(bookTitle, bookAuthor) { success ->
                                if (success) {
                                    showAddDialog = false
                                    bookTitle = ""
                                    bookAuthor = ""
                                } else {
                                    AppUtils.showToast(context, "Failed to add book")
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Text("Add", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
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
                title = { Text("Reading List", color = MaterialTheme.colorScheme.secondary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.secondary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.generateAISuggestions() }) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Suggestions", tint = MaterialTheme.colorScheme.secondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Teal)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }, containerColor = Teal) {
                Icon(Icons.Default.Add, contentDescription = "Add Book", tint = MaterialTheme.colorScheme.secondary)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (readingList.isEmpty() && suggestions.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Your reading list is empty.\nTap + to add a book or use the AI sparkle for suggestions!",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            if (readingList.isNotEmpty()) {
                item {
                    Text("My Reading List", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Teal)
                }
                itemsIndexed(readingList) { index, book ->
                    ReadingListCard(
                        book = book,
                        isFirst = index == 0,
                        isLast = index == readingList.size - 1,
                        onRemove = { viewModel.removeBook(book.id) },
                        onMove = { moveUp -> viewModel.moveBook(book.id, moveUp) }
                    )
                }
            }

            if (suggestions.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Recommended for You (AI)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Teal)
                }
                items(suggestions) { suggestion ->
                    ReadingListCard(
                        suggestion,
                        isSuggestion = true,
                        onAdd = { viewModel.convertSuggestionToToRead(suggestion.id) },
                        onRemove = { viewModel.dismissSuggestion(suggestion.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ReadingListCard(
    book: ReadingListBook,
    isSuggestion: Boolean = false,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onAdd: () -> Unit = {},
    onRemove: () -> Unit = {},
    onMove: (Boolean) -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (!isSuggestion) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { onMove(true) },
                        enabled = !isFirst,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Move Up",
                            tint = if (isFirst) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f) else Teal
                        )
                    }
                    IconButton(
                        onClick = { onMove(false) },
                        enabled = !isLast,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Move Down",
                            tint = if (isLast) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f) else Teal
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(book.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSuggestion) {
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Default.Check, contentDescription = "Add to List", tint = Teal)
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                } else {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}
