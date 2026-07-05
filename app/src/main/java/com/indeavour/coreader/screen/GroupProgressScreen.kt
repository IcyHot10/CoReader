package com.indeavour.coreader.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.indeavour.coreader.model.firebase.BookModel
import com.indeavour.coreader.model.firebase.GroupBook
import com.indeavour.coreader.ui.theme.Teal
import com.indeavour.coreader.viewmodel.GroupViewModel
import com.indeavour.coreader.viewmodel.ReaderViewModel
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupProgressScreen(onBack: () -> Unit) {
    val groupViewModel: GroupViewModel = viewModel()
    val groupBooks by groupViewModel.groupBooks.collectAsState()
    val activeGroupId by groupViewModel.activeGroupId.collectAsState()
    val usernames by groupViewModel.usernames.collectAsState()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var filterType by rememberSaveable { mutableStateOf("All") }
    var showFilterDialog by remember { mutableStateOf(false) }
    var displayMoreMenu by rememberSaveable { mutableStateOf(false) }

    // Trigger name fetching for all users in the book progression
    LaunchedEffect(groupBooks) {
        groupBooks.values.forEach { groupBook ->
            groupBook.bookProgression.keys.forEach { userId ->
                groupViewModel.fetchUsername(userId)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search books...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.secondary,
                                focusedTextColor = MaterialTheme.colorScheme.secondary,
                                unfocusedTextColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("Group Book Progress", color = MaterialTheme.colorScheme.secondary)
                    }
                },
                navigationIcon = {
                    if (isSearchActive) {
                        IconButton(onClick = { 
                            isSearchActive = false
                            searchQuery = ""
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close Search",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.secondary)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { displayMoreMenu = !displayMoreMenu }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Open More",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Teal)
            )
        }
    ) { padding ->
        if (showFilterDialog) {
            AlertDialog(
                onDismissRequest = { showFilterDialog = false },
                title = { Text("Filter Books", color = MaterialTheme.colorScheme.secondary) },
                text = {
                    Column {
                        val filterOptions = listOf("All", "In Progress", "Unread", "Completed")
                        filterOptions.forEach { type ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        filterType = type
                                        showFilterDialog = false 
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = filterType == type, 
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = Teal)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(type, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFilterDialog = false }) { 
                        Text("Close", color = Teal) 
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val filteredKeys = remember(groupBooks, searchQuery, filterType) {
                groupBooks.keys.filter { bookKey ->
                    val groupBook = groupBooks[bookKey]!!
                    val sampleBook = groupBook.bookProgression.values.firstOrNull()?.get(bookKey)?.let { BookModel.fromAny(it) }
                    val title = sampleBook?.title ?: ""
                    val author = sampleBook?.author ?: ""
                    
                    val matchesSearch = title.contains(searchQuery, ignoreCase = true) || 
                                      author.contains(searchQuery, ignoreCase = true)
                    
                    val userProgress = groupBook.bookProgression[FirebaseAuth.getInstance().currentUser?.uid]
                        ?.get(bookKey)?.let { BookModel.fromAny(it) }?.progress
                    
                    val progress = try {
                        if (userProgress.isNullOrBlank()) 0f
                        else {
                            val json = JSONObject(userProgress)
                            val locations = json.optJSONObject("locations")
                            locations?.optDouble("totalProgression", 0.0)?.toFloat() ?: 0f
                        }
                    } catch (e: Exception) { 0f }

                    val matchesFilter = when(filterType) {
                        "In Progress" -> progress > 0.005f && progress < 0.995f
                        "Unread" -> progress <= 0.005f
                        "Completed" -> progress >= 0.995f
                        else -> true
                    }
                    matchesSearch && matchesFilter
                }.sortedByDescending { bookKey ->
                    // Find the earliest added timestamp among all members for this book to determine when it was "added to group"
                    val groupBook = groupBooks[bookKey]!!
                    groupBook.bookProgression.values.mapNotNull { 
                        BookModel.fromAny(it[bookKey])?.addedTimestamp 
                    }.filter { it > 0 }.minOrNull() ?: 0L
                }
            }

            if (activeGroupId == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No active group selected")
                }
            } else if (groupBooks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No books uploaded to this group yet")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // groupBooks is Map<BookKey, GroupBook>
                    items(filteredKeys) { bookKey ->
                        val groupBook = groupBooks[bookKey]!!
                        BookProgressItem(bookKey, groupBook, usernames)
                    }
                }
            }

            if (displayMoreMenu) {
                ProgressMoreMenu(
                    modifier = Modifier
                        .width(200.dp)
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp),
                    onSearchClick = {
                        isSearchActive = true
                        displayMoreMenu = false
                    },
                    onFilterClick = {
                        showFilterDialog = true
                        displayMoreMenu = false
                    }
                )
            }
        }
    }
}

@Composable
fun ProgressMoreMenu(
    modifier: Modifier,
    onSearchClick: () -> Unit,
    onFilterClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSearchClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Teal, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text("Search", style = MaterialTheme.typography.bodyLarge)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onFilterClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FilterList, contentDescription = null, tint = Teal, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text("Filter", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun BookProgressItem(bookKey: String, groupBook: GroupBook, usernames: Map<String, String>) {
    var expanded by remember { mutableStateOf(false) }
    
    val sortedMembers = remember(groupBook, bookKey) {
        groupBook.bookProgression.keys.mapNotNull { userId ->
            groupBook.getBookModel(userId, bookKey)?.let { bookModel ->
                val progressValue = try {
                    if (bookModel.progress.isBlank()) 0f
                    else {
                        val json = JSONObject(bookModel.progress)
                        val locations = json.optJSONObject("locations")
                        locations?.optDouble("totalProgression", 0.0)?.toFloat() ?: 0f
                    }
                } catch (e: Exception) { 0f }
                Triple(userId, bookModel, progressValue)
            }
        }.sortedWith(compareByDescending<Triple<String, BookModel, Float>> { it.third }
            .thenBy { if (it.third >= 0.995f) it.second.completedTimestamp else Long.MAX_VALUE }
        )
    }
    
    val sampleBook = sortedMembers.firstOrNull()?.second
    val title = sampleBook?.title ?: "Unknown Title"
    val author = sampleBook?.author ?: "Unknown Author"

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Show less" else "Show more",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    if (sortedMembers.isEmpty()) {
                        Text("No progress recorded for this book", style = MaterialTheme.typography.bodySmall)
                    }

                    sortedMembers.forEachIndexed { index, (userId, bookModel, _) ->
                        val displayName = usernames[userId] ?: "User $userId"
                        UserProgressRow(userId, bookModel, displayName)
                        if (index < sortedMembers.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp), 
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserProgressRow(userId: String, bookModel: BookModel, username: String) {
    val progressValue = remember(bookModel.progress) {
        try {
            if (bookModel.progress.isBlank()) 0f
            else {
                val json = JSONObject(bookModel.progress)
                val locations = json.optJSONObject("locations")
                locations?.optDouble("totalProgression", 0.0)?.toFloat() ?: 0f
            }
        } catch (e: Exception) {
            0f
        }
    }

    val percentageLabel = remember(progressValue) {
        val p = (progressValue * 100).coerceIn(0f, 100f)
        if (p > 99.5f) "100%" else "${kotlin.math.round(p).toInt()}%"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = username, 
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = percentageLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progressValue },
            modifier = Modifier.fillMaxWidth(),
            color = Teal,
            trackColor = Teal.copy(alpha = 0.2f)
        )
    }
}
