package com.indeavour.coreader.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
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
import kotlinx.coroutines.launch

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
    var showWheelDialog by remember { mutableStateOf(false) }
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

    if (showWheelDialog) {
        SpinWheelDialog(
            books = books,
            onDismiss = { showWheelDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group Book Picker", color = MaterialTheme.colorScheme.secondary) },
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
        },
        bottomBar = {
            if (!activeGroupCode.isNullOrBlank() && books.isNotEmpty()) {
                Surface(
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = { showWheelDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Teal),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Casino,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Spin the Wheel to Pick Next Book",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
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

enum class WheelMode {
    NORMAL,
    WEIGHTED,
    KNOCKOUT
}

data class WheelSlice(
    val index: Int,
    val book: GroupReadingListBook,
    val startAngleOffset: Float,
    val sweepAngle: Float
)

@Composable
fun SpinWheelDialog(
    books: List<GroupReadingListBook>,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val animatedRotation = remember { Animatable(0f) }
    var isSpinning by remember { mutableStateOf(false) }
    var winningBook by remember { mutableStateOf<GroupReadingListBook?>(null) }
    var currentMode by remember { mutableStateOf(WheelMode.NORMAL) }

    val remainingBooks = remember { mutableStateListOf<GroupReadingListBook>() }
    val eliminatedBooks = remember { mutableStateListOf<GroupReadingListBook>() }

    LaunchedEffect(books, currentMode) {
        if (currentMode == WheelMode.KNOCKOUT) {
            remainingBooks.clear()
            remainingBooks.addAll(books)
            eliminatedBooks.clear()
            winningBook = null
        }
    }

    val candidateBooks = when (currentMode) {
        WheelMode.KNOCKOUT -> remainingBooks
        else -> books
    }

    val sliceColors = listOf(
        Teal,
        Color(0xFFE91E63),
        Color(0xFF9C27B0),
        Color(0xFF2196F3),
        Color(0xFFFF9800),
        Color(0xFF4CAF50),
        Color(0xFFFFC107),
        Color(0xFF00BCD4)
    )

    val sliceKeys = candidateBooks.map { it.id to (if (currentMode == WheelMode.WEIGHTED) it.votes.size else 0) }

    val slices = remember(sliceKeys, currentMode) {
        if (candidateBooks.isEmpty()) {
            emptyList()
        } else if (currentMode == WheelMode.WEIGHTED) {
            val totalWeight = candidateBooks.sumOf { it.votes.size + 1 }.toFloat()
            var currentOffset = 0f
            candidateBooks.mapIndexed { i, book ->
                val sweep = ((book.votes.size + 1) / totalWeight) * 360f
                val startOffset = currentOffset
                currentOffset += sweep
                WheelSlice(i, book, startOffset, sweep)
            }
        } else {
            val sweep = 360f / candidateBooks.size
            candidateBooks.mapIndexed { i, book ->
                WheelSlice(i, book, i * sweep, sweep)
            }
        }
    }

    val isKnockoutFinished = currentMode == WheelMode.KNOCKOUT && remainingBooks.size == 1 && eliminatedBooks.isNotEmpty()

    AlertDialog(
        onDismissRequest = {
            if (!isSpinning) onDismiss()
        },
        title = {
            Column {
                Text(
                    "Spin the Wheel!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Teal
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FilterChip(
                        selected = currentMode == WheelMode.NORMAL,
                        onClick = {
                            if (!isSpinning) {
                                currentMode = WheelMode.NORMAL
                                winningBook = null
                            }
                        },
                        label = { Text("Normal") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Teal, selectedLabelColor = MaterialTheme.colorScheme.secondary)
                    )
                    FilterChip(
                        selected = currentMode == WheelMode.WEIGHTED,
                        onClick = {
                            if (!isSpinning) {
                                currentMode = WheelMode.WEIGHTED
                                winningBook = null
                            }
                        },
                        label = { Text("Weighted") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Teal, selectedLabelColor = MaterialTheme.colorScheme.secondary)
                    )
                    FilterChip(
                        selected = currentMode == WheelMode.KNOCKOUT,
                        onClick = {
                            if (!isSpinning) {
                                currentMode = WheelMode.KNOCKOUT
                                winningBook = null
                                remainingBooks.clear()
                                remainingBooks.addAll(books)
                                eliminatedBooks.clear()
                            }
                        },
                        label = { Text("Knockout") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Teal, selectedLabelColor = MaterialTheme.colorScheme.secondary)
                    )
                }
                Text(
                    text = when (currentMode) {
                        WheelMode.NORMAL -> "Equal probability for every book"
                        WheelMode.WEIGHTED -> "Slice size scaled by votes + 1"
                        WheelMode.KNOCKOUT -> "Knocks out picked books until 1 remains!"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (books.isEmpty()) {
                    Text(
                        "No proposed books available to spin!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                } else if (currentMode == WheelMode.KNOCKOUT && books.size < 2) {
                    Text(
                        "Propose at least 2 books to play Knockout mode!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val radius = size.minDimension / 2f
                            val center = Offset(size.width / 2f, size.height / 2f)

                            val textPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = 26f
                                textAlign = android.graphics.Paint.Align.CENTER
                                isAntiAlias = true
                                isFakeBoldText = true
                            }

                            slices.forEachIndexed { i, slice ->
                                val startAngle = -90f + slice.startAngleOffset + animatedRotation.value
                                val color = sliceColors[i % sliceColors.size]

                                drawArc(
                                    color = color,
                                    startAngle = startAngle,
                                    sweepAngle = slice.sweepAngle,
                                    useCenter = true,
                                    topLeft = Offset(center.x - radius, center.y - radius),
                                    size = Size(radius * 2, radius * 2)
                                )

                                val midAngle = startAngle + slice.sweepAngle / 2f
                                rotate(degrees = midAngle, pivot = center) {
                                    val truncatedTitle = if (slice.book.title.length > 12) slice.book.title.take(10) + "…" else slice.book.title
                                    drawContext.canvas.nativeCanvas.drawText(
                                        truncatedTitle,
                                        center.x + radius * 0.5f,
                                        center.y + 10f,
                                        textPaint
                                    )
                                }
                            }

                            // Center Hub
                            drawCircle(color = Color.White, radius = 22.dp.toPx(), center = center)
                            drawCircle(color = Teal, radius = 16.dp.toPx(), center = center)

                            // Top Pointer Arrow
                            val pointerPath = Path().apply {
                                moveTo(center.x - 12.dp.toPx(), center.y - radius - 4.dp.toPx())
                                lineTo(center.x + 12.dp.toPx(), center.y - radius - 4.dp.toPx())
                                lineTo(center.x, center.y - radius + 14.dp.toPx())
                                close()
                            }
                            drawPath(pointerPath, color = Color.Red)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val activeWinner = if (isKnockoutFinished) remainingBooks.firstOrNull() else winningBook

                    if (activeWinner != null) {
                        val bannerTitle = when {
                            isKnockoutFinished -> "🏆 LAST BOOK STANDING WINNER! 🏆"
                            currentMode == WheelMode.KNOCKOUT -> "💥 Knocked Out! 💥"
                            currentMode == WheelMode.WEIGHTED -> "⚖️ Weighted Winner Chosen! ⚖️"
                            else -> "🎉 Winner Chosen! 🎉"
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Teal.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    bannerTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Teal
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    activeWinner.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                if (activeWinner.author.isNotBlank()) {
                                    Text(
                                        "by ${activeWinner.author}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                                if (currentMode == WheelMode.KNOCKOUT && !isKnockoutFinished) {
                                    Text(
                                        "${remainingBooks.size} books remaining",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Teal,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (currentMode == WheelMode.KNOCKOUT && eliminatedBooks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = {
                                remainingBooks.clear()
                                remainingBooks.addAll(books)
                                eliminatedBooks.clear()
                                winningBook = null
                            },
                            enabled = !isSpinning
                        ) {
                            Text("Reset Knockout (${eliminatedBooks.size} eliminated)", color = Teal, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (candidateBooks.isNotEmpty() && !(currentMode == WheelMode.KNOCKOUT && books.size < 2)) {
                Button(
                    onClick = {
                        if (!isSpinning && !isKnockoutFinished) {
                            isSpinning = true
                            winningBook = null
                            coroutineScope.launch {
                                val chosenSlice = if (currentMode == WheelMode.WEIGHTED) {
                                    val totalWeight = candidateBooks.sumOf { it.votes.size + 1 }.toFloat()
                                    val r = kotlin.random.Random.nextFloat() * totalWeight
                                    var accum = 0f
                                    var pickedIndex = 0
                                    for (i in candidateBooks.indices) {
                                        accum += candidateBooks[i].votes.size + 1
                                        if (r <= accum) {
                                            pickedIndex = i
                                            break
                                        }
                                    }
                                    slices[pickedIndex]
                                } else {
                                    val randomIndex = kotlin.random.Random.nextInt(candidateBooks.size)
                                    slices[randomIndex]
                                }

                                val midAngleOffset = chosenSlice.startAngleOffset + chosenSlice.sweepAngle / 2f
                                val targetAngleModulo = (360f - midAngleOffset) % 360f
                                val currentRot = animatedRotation.value
                                val delta = (targetAngleModulo - (currentRot % 360f) + 360f) % 360f
                                val targetRotation = currentRot + (360f * 8) + delta

                                animatedRotation.animateTo(
                                    targetValue = targetRotation,
                                    animationSpec = tween(
                                        durationMillis = 4000,
                                        easing = FastOutSlowInEasing
                                    )
                                )

                                val selected = chosenSlice.book
                                winningBook = selected

                                if (currentMode == WheelMode.KNOCKOUT) {
                                    remainingBooks.remove(selected)
                                    eliminatedBooks.add(selected)
                                }

                                isSpinning = false
                            }
                        }
                    },
                    enabled = !isSpinning && !isKnockoutFinished,
                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (isSpinning) "Spinning..." else if (isKnockoutFinished) "Winner Decided!" else if (winningBook != null) "Spin Again!" else "Spin!",
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSpinning
            ) {
                Text("Close", color = Teal, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
    )
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
