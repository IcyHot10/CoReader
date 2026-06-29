package com.indeavour.coreader.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupProgressScreen(onBack: () -> Unit) {
    val groupViewModel: GroupViewModel = viewModel()
    val groupBooks by groupViewModel.groupBooks.collectAsState()
    val activeGroupId by groupViewModel.activeGroupId.collectAsState()
    val usernames by groupViewModel.usernames.collectAsState()

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
                title = { Text("Group Book Progress", color = MaterialTheme.colorScheme.secondary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.secondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Teal)
            )
        }
    ) { padding ->
        if (activeGroupId == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No active group selected")
            }
        } else if (groupBooks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No books uploaded to this group yet")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // groupBooks is Map<BookKey, GroupBook>
                items(groupBooks.keys.toList()) { bookKey ->
                    val groupBook = groupBooks[bookKey]!!
                    BookProgressItem(bookKey, groupBook, usernames)
                }
            }
        }
    }
}

@Composable
fun BookProgressItem(bookKey: String, groupBook: GroupBook, usernames: Map<String, String>) {
    var expanded by remember { mutableStateOf(false) }
    
    val membersInThisBook = groupBook.bookProgression.keys.mapNotNull { userId ->
        groupBook.getBookModel(userId, bookKey)?.let { userId to it }
    }.toMap()
    
    val sampleBook = membersInThisBook.values.firstOrNull()
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
                    if (membersInThisBook.isEmpty()) {
                        Text("No progress recorded for this book", style = MaterialTheme.typography.bodySmall)
                    }

                    membersInThisBook.forEach { (userId, bookModel) ->
                        // Show "User [ID]" while loading, but the fetchUsername logic in GroupViewModel
                        // should be populating the 'usernames' map automatically.
                        val displayName = usernames[userId] ?: "User $userId"
                        UserProgressRow(userId, bookModel, displayName)
                        if (membersInThisBook.keys.toList().last() != userId) {
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

    val percentage = (progressValue * 100).toInt()

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
                text = "$percentage%", 
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
