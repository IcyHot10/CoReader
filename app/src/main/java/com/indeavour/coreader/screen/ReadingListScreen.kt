package com.indeavour.coreader.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.indeavour.coreader.AppUtils
import com.indeavour.coreader.model.firebase.ReadingListBook
import com.indeavour.coreader.ui.theme.Teal
import com.indeavour.coreader.viewmodel.ReadingListViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingListScreen(onBack: () -> Unit) {
    val viewModel: ReadingListViewModel = viewModel()
    val readingList by viewModel.readingList.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val pendingSuggestions by viewModel.pendingSuggestions.collectAsState()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var bookTitle by remember { mutableStateOf("") }
    var bookAuthor by remember { mutableStateOf("") }

    var showChatDialog by remember { mutableStateOf(false) }
    var chatPrompt by remember { mutableStateOf("") }

    if (showChatDialog) {
        AlertDialog(
            onDismissRequest = { showChatDialog = false },
            title = {
                Text(
                    "Ask AI for Suggestions",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            },
            text = {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        "Describe what kind of book you are looking for, and our AI will find recommendations for you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    TextField(
                        value = chatPrompt,
                        onValueChange = { chatPrompt = it },
                        placeholder = { Text("e.g. A fast-paced thriller set in Tokyo") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
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
                        if (chatPrompt.isNotBlank()) {
                            viewModel.generateAISuggestions(chatPrompt)
                            showChatDialog = false
                            chatPrompt = ""
                            AppUtils.showToast(context, "AI is thinking...")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Text("Ask AI", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showChatDialog = false }) {
                    Text("Cancel", color = Teal, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
        )
    }

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

    if (pendingSuggestions.isNotEmpty()) {
        RecommendationSwipePopup(
            suggestions = pendingSuggestions,
            onAccept = { viewModel.acceptSuggestion(it) },
            onReject = { viewModel.dismissPendingSuggestion(it.id) }
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
                    IconButton(onClick = { 
                        viewModel.generateAISuggestions()
                        AppUtils.showToast(context, "Generating suggestions...")
                    }) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Suggestions", tint = MaterialTheme.colorScheme.secondary)
                    }
                    IconButton(onClick = { showChatDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "AI Chat", tint = MaterialTheme.colorScheme.secondary)
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
                            "Your reading list is empty.\nTap + to add a book, use sparkle for general suggestions, or use chat for specific requests!",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
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
fun RecommendationSwipePopup(
    suggestions: List<ReadingListBook>,
    onAccept: (ReadingListBook) -> Unit,
    onReject: (ReadingListBook) -> Unit
) {
    val topSuggestion = suggestions.last()
    
    Dialog(
        onDismissRequest = { /* No-op, force interaction */ },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Review Recommendations",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                Box(contentAlignment = Alignment.Center) {
                    // Show background cards if any
                    if (suggestions.size > 1) {
                        RecommendationCard(
                            book = suggestions[suggestions.size - 2],
                            modifier = Modifier.padding(top = 16.dp).graphicsLayer(scaleX = 0.95f, scaleY = 0.95f, alpha = 0.5f)
                        )
                    }

                    SwipeableCard(
                        book = topSuggestion,
                        onSwipeLeft = { onReject(topSuggestion) },
                        onSwipeRight = { onAccept(topSuggestion) }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { onReject(topSuggestion) },
                            modifier = Modifier.size(64.dp).background(Color.White, androidx.compose.foundation.shape.CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Reject", tint = Color.Red, modifier = Modifier.size(32.dp))
                        }
                        Text("Reject", color = Color.White, modifier = Modifier.padding(top = 8.dp))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { onAccept(topSuggestion) },
                            modifier = Modifier.size(64.dp).background(Color.White, androidx.compose.foundation.shape.CircleShape)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Add", tint = Teal, modifier = Modifier.size(32.dp))
                        }
                        Text("Add", color = Color.White, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                
                Text(
                    "${suggestions.size} remaining",
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 24.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun SwipeableCard(
    book: ReadingListBook,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
) {
    var offsetX by remember(book.id) { mutableStateOf(0f) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .graphicsLayer {
                rotationZ = offsetX / 20f
                alpha = 1f - (kotlin.math.abs(offsetX) / 1000f).coerceIn(0f, 0.5f)
            }
            .pointerInput(book.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > 300) onSwipeRight()
                        else if (offsetX < -300) onSwipeLeft()
                        else offsetX = 0f
                    },
                    onHorizontalDrag = { change: PointerInputChange, dragAmount: Float ->
                        change.consume()
                        offsetX += dragAmount
                    }
                )
            },
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    ) {
        RecommendationCardContent(book)
    }
}

@Composable
fun RecommendationCard(book: ReadingListBook, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    ) {
        RecommendationCardContent(book)
    }
}

@Composable
fun RecommendationCardContent(book: ReadingListBook) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Teal.copy(alpha = 0.2f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "by ${book.author}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontStyle = FontStyle.Italic
        )
        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = Teal.copy(alpha = 0.1f))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = book.description.ifBlank { "No description available." },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
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
                if (book.description.isNotBlank()) {
                    Text(
                        book.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 2,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
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
