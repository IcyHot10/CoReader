package com.indeavour.coreader.screen

import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.indeavour.coreader.model.firebase.UserModel
import com.indeavour.coreader.model.firebase.GroupBook
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.indeavour.coreader.viewmodel.UserViewModel
import com.indeavour.coreader.viewmodel.GroupViewModel
import com.indeavour.coreader.model.firebase.BookModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.indeavour.coreader.ui.theme.Teal
import kotlin.collections.mutableListOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LineAxis
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.core.net.toFile
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import com.indeavour.coreader.model.room.UserPreferences
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.getValue
import com.indeavour.coreader.AppRoomDatabase
import com.indeavour.coreader.AppUtils
import com.indeavour.coreader.R
import com.indeavour.coreader.model.room.RoomBook
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.content
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.util.asset.Asset
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.CoroutineContext


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(routeToLogin: () -> Unit, routeToBook: () -> Unit, routeToGroup: () -> Unit, routeToProgress: () -> Unit, routeToStats: () -> Unit){
    val context = LocalContext.current
    val database by lazy { AppRoomDatabase.getDatabase(context = context) }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val userViewModel: UserViewModel = viewModel()
    val user by userViewModel.user.collectAsState()
    val groupViewModel: GroupViewModel = viewModel()
    val activeGroupId by groupViewModel.activeGroupId.collectAsState()
    val groupBooks by groupViewModel.groupBooks.collectAsState()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var filterType by rememberSaveable { mutableStateOf("All") }
    var showFilterDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        database.userPreferencesDao().getPreferences().collect { prefs ->
            prefs?.let {
                filterType = it.libraryFilter
            }
        }
    }

    fun updateLibraryFilter(newFilter: String) {
        filterType = newFilter
        scope.launch(Dispatchers.IO) {
            val currentPrefs = database.userPreferencesDao().getPreferences().first() ?: UserPreferences()
            database.userPreferencesDao().insertOrUpdate(currentPrefs.copy(libraryFilter = newFilter))
        }
    }

    var books by remember {
        mutableStateOf(listOf<RoomBook>())
    }

    var activeBook by remember {
        mutableStateOf<RoomBook?>(null)
    }

    var isDeletionMode by remember { mutableStateOf(false) }
    var selectedBookIds by remember { mutableStateOf(setOf<Int>()) }

    LaunchedEffect(Unit) {
        database.bookDao().getActiveFlow().collect { book ->
            activeBook = book
        }
    }


    LaunchedEffect(Unit) {
        database.bookDao().getAll().collect { booksList ->
            books = booksList
        }
    }

    var displayMoreMenu by rememberSaveable {
        mutableStateOf(false)
    }


    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(325.dp),
                drawerContainerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                SideMenuContent(user, userViewModel, routeToLogin, routeToGroup, routeToProgress, routeToStats)
            }
        }
    ) {
        Scaffold(topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Teal,
                    titleContentColor = MaterialTheme.colorScheme.secondary),
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
                        Text(if (isDeletionMode) "${selectedBookIds.size} Selected" else "CoReader Library")
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
                    } else if (isDeletionMode) {
                        IconButton(onClick = { 
                            isDeletionMode = false 
                            selectedBookIds = emptySet()
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Exit Deletion Mode",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    } else {
                        IconButton(onClick = { 
                            scope.launch {
                                if (drawerState.isClosed) drawerState.open() else drawerState.close()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "Open Burger Menu",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                },
                actions = {
                    if (isDeletionMode) {
                        if (selectedBookIds.isNotEmpty()) {
                            IconButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    selectedBookIds.forEach { id ->
                                        val book = database.bookDao().getById(id)
                                        book?.let {
                                            val file = File(it.filePath)
                                            if (file.exists()) {
                                                file.delete()
                                            }
                                            database.bookDao().markAsDeleted(id)
                                        }
                                    }
                                    withContext(Dispatchers.Main) {
                                        isDeletionMode = false
                                        selectedBookIds = emptySet()
                                    }
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete Selected"
                                )
                            }

                            IconButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    selectedBookIds.forEach { id ->
                                        val book = database.bookDao().getById(id)
                                        book?.let {
                                            database.bookDao().setFavourite(id, !it.isFavourite)
                                        }
                                    }
                                    withContext(Dispatchers.Main) {
                                        isDeletionMode = false
                                        selectedBookIds = emptySet()
                                    }
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = "Toggle Favourite"
                                )
                            }
                        }
                    } else {
                        Box {
                            IconButton(onClick = { displayMoreMenu = !displayMoreMenu }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "Open More"
                                )
                            }
                            MoreMenu(
                                expanded = displayMoreMenu,
                                onDismissRequest = { displayMoreMenu = false },
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
            )
        }, floatingActionButton = {
            if (activeBook != null && drawerState.isClosed) {
                FloatingActionButton(onClick = {
                    routeToBook()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = "Open Active Book"
                    )
                }
            }
        }) {
            innerPadding ->
            if (showFilterDialog) {
                AlertDialog(
                    onDismissRequest = { showFilterDialog = false },
                    title = { 
                        Text(
                            "Filter Books", 
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.secondary 
                        ) 
                    },
                    text = {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            val filterOptions = listOf(
                                "All", 
                                "Favourites",
                                "In Group", 
                                "Not in Group", 
                                "In Progress", 
                                "Not Completed",
                                "Unread", 
                                "Completed"
                            )
                            filterOptions.forEach { type ->
                                val isSelected = filterType == type
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                        .clickable { 
                                            updateLibraryFilter(type)
                                            showFilterDialog = false 
                                        }
                                        .padding(vertical = 4.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected, 
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(selectedColor = Teal)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        type, 
                                        color = if (isSelected) Teal else MaterialTheme.colorScheme.onSurface,
                                        style = if (isSelected) MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) 
                                                else MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showFilterDialog = false }) { 
                            Text("Close", color = Teal, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) 
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
                )
            }

            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                val filteredBooks = remember(books, searchQuery, filterType, groupBooks) {
                    books.filter { book ->
                        val matchesSearch = book.title.contains(searchQuery, ignoreCase = true) || 
                                          book.author.contains(searchQuery, ignoreCase = true)
                        
                        val bookKey = "${book.title}_${book.author}"
                        val progress = try {
                            if (book.progression.isNullOrBlank()) 0f
                            else {
                                val json = JSONObject(book.progression)
                                val locations = json.optJSONObject("locations")
                                locations?.optDouble("totalProgression", 0.0)?.toFloat() ?: 0f
                            }
                        } catch (e: Exception) { 0f }

                        val matchesFilter = when(filterType) {
                            "Favourites" -> book.isFavourite
                            "In Group" -> groupBooks.containsKey(bookKey)
                            "Not in Group" -> !groupBooks.containsKey(bookKey)
                            "In Progress" -> progress > 0.005f && progress < 0.995f
                            "Not Completed" -> progress < 0.995f
                            "Unread" -> progress <= 0.005f
                            "Completed" -> progress >= 0.995f
                            else -> true
                        }
                        matchesSearch && matchesFilter
                    }
                }
                val configuration = LocalConfiguration.current
                val columns = (configuration.screenWidthDp / 120).coerceAtLeast(1)
                val bookRows = remember(filteredBooks, columns) { filteredBooks.chunked(columns) }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(bookRows) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (book in row) {
                                Spacer(modifier = Modifier.width(4.dp))
                                BookCard(
                                    book = book,
                                    routeToBook = routeToBook,
                                    modifier = Modifier.weight(1f),
                                    context = context,
                                    isDeletionMode = isDeletionMode,
                                    isSelected = selectedBookIds.contains(book.id),
                                    onLongClick = {
                                        isDeletionMode = true
                                        selectedBookIds = selectedBookIds + book.id
                                    },
                                    onToggleSelection = {
                                        if (selectedBookIds.contains(book.id)) {
                                            selectedBookIds = selectedBookIds - book.id
                                            if (selectedBookIds.isEmpty()) {
                                                isDeletionMode = false
                                            }
                                        } else {
                                            selectedBookIds = selectedBookIds + book.id
                                        }
                                    },
                                    user = user,
                                    activeGroupId = activeGroupId,
                                    groupBooks = groupBooks,
                                    onUpload = { bookModel ->
                                        val bKey = "${book.title}_${book.author}"
                                        val isAlreadyInUserLibrary = user?.books?.containsKey(bKey) == true
                                        
                                        if (!isAlreadyInUserLibrary) {
                                            userViewModel.addBook(bookModel) { _, _ -> 
                                                // Even if adding to user library fails, we still try to upload to group
                                                // though usually it should succeed.
                                                groupViewModel.uploadBookToGroup(bookModel) { success ->
                                                    if (success) {
                                                        AppUtils.showToast(context, "Book uploaded to group")
                                                    } else {
                                                        AppUtils.showToast(context, "Failed to upload book")
                                                    }
                                                }
                                            }
                                        } else {
                                            groupViewModel.uploadBookToGroup(bookModel) { success ->
                                                if (success) {
                                                    AppUtils.showToast(context, "Book uploaded to group")
                                                } else {
                                                    AppUtils.showToast(context, "Failed to upload book")
                                                }
                                            }
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            // Add invisible spacers to maintain grid alignment if the row is incomplete
                            if (row.size < columns) {
                                repeat(columns - row.size) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Spacer(modifier = Modifier.weight(1f))
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                            }
                        }
                        HorizontalDivider(thickness = 10.dp, color = Teal, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCard(
    book: RoomBook, 
    routeToBook: () -> Unit, 
    modifier: Modifier = Modifier, 
    context: Context,
    isDeletionMode: Boolean,
    isSelected: Boolean,
    onLongClick: () -> Unit,
    onToggleSelection: () -> Unit,
    user: UserModel?,
    activeGroupId: String?,
    groupBooks: Map<String, GroupBook>,
    onUpload: (BookModel) -> Unit
) {
    val database by lazy { AppRoomDatabase.getDatabase(context = context) }
    val scope = rememberCoroutineScope()
    val bookKey = "${book.title}_${book.author}"
    val isUploaded = groupBooks.containsKey(bookKey)
    val showUploadButton = activeGroupId != null && !isUploaded && !isDeletionMode

    Column(
        modifier = modifier.combinedClickable(
            onClick = {
                if (isDeletionMode) {
                    onToggleSelection()
                } else {
                    scope.launch(Dispatchers.IO) {
                        database.bookDao().setInActive()
                        database.bookDao().setActive(book.id)
                        withContext(Dispatchers.Main) {
                            routeToBook()
                        }
                    }
                }
            },
            onLongClick = onLongClick
        ), 
        verticalArrangement = Arrangement.SpaceEvenly, 
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        Box {
            Image(
                bitmap = book.cover?.let { BitmapFactory.decodeFile(it) }?.asImageBitmap() ?: ImageBitmap.imageResource(R.drawable.logo),
                contentDescription = book.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp),
                contentScale = ContentScale.Crop,
                alpha = if (isSelected) 0.5f else 1f
            )
            
            if (showUploadButton) {
                IconButton(
                    onClick = {
                        val bookModel = user?.books?.get(bookKey) ?: BookModel(
                            title = book.title,
                            author = book.author,
                            progress = book.progression ?: "0",
                            addedTimestamp = book.addedTimestamp,
                            completedTimestamp = book.completedTimestamp
                        )
                        onUpload(bookModel)
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudUpload,
                        contentDescription = "Upload to Group",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (book.isFavourite) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Favourite",
                    tint = Teal,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape)
                        .padding(2.dp)
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SideMenuContent(user: UserModel?, userViewModel: UserViewModel, routeToLogin: () -> Unit, routeToGroup: () -> Unit, routeToProgress: () -> Unit, routeToStats: () -> Unit){
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }
    var newUsername by remember { mutableStateOf("") }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Update Username", color = MaterialTheme.colorScheme.secondary) },
            text = {
                TextField(
                    value = newUsername,
                    onValueChange = { newUsername = it },
                    label = { Text("New Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Teal,
                        cursorColor = Teal
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        userViewModel.updateUsername(newUsername) { success ->
                            if (success) {
                                showEditDialog = false
                            } else {
                                AppUtils.showToast(context, "Failed to update username")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Teal)
                ) {
                    Text("Update", color = MaterialTheme.colorScheme.secondary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = Teal)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column() {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()){
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)){
                    IconButton(onClick = {  }, modifier = Modifier.clip(CircleShape)) {
                        Image(
                            painter = painterResource(R.drawable.logo),
                            contentDescription = "Open Burger Menu",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.clip(CircleShape).fillMaxSize()
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        user?.username ?: "Loading...",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { 
                    newUsername = user?.username ?: ""
                    showEditDialog = true 
                }) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit profile",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Image(painter = painterResource(R.drawable.library_image), contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
            
            Spacer(modifier = Modifier.height(16.dp))

            NavigationDrawerItem(
                label = { Text("Manage Group") },
                selected = false,
                onClick = routeToGroup,
                icon = { Icon(Icons.Default.Group, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            )

            NavigationDrawerItem(
                label = { Text("View Group Book Progress") },
                selected = false,
                onClick = routeToProgress,
                icon = { Icon(Icons.Default.LineAxis, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            )

            NavigationDrawerItem(
                label = { Text("View Reading Stats") },
                selected = false,
                onClick = routeToStats,
                icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            )
        }

        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )

            NavigationDrawerItem(
                label = { Text("Logout") },
                selected = false,
                onClick = {
                    auth.signOut()
                    try {
                        val credentialManager = CredentialManager.create(context)
                        val clearRequest = ClearCredentialStateRequest()
                        CoroutineScope(Dispatchers.Main).launch {
                            credentialManager.clearCredentialState(
                                clearRequest
                            )
                        }
                    } catch (e: ClearCredentialException) {
                        Log.e(TAG, "Couldn't clear user credentials: ${e.localizedMessage}")
                    }
                    routeToLogin()
                },
                icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun MoreMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onSearchClick: () -> Unit,
    onFilterClick: () -> Unit
){
    val context = LocalContext.current
    val database by lazy { AppRoomDatabase.getDatabase(context = context) }
    val userViewModel: UserViewModel = viewModel()
    val user by userViewModel.user.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris: List<Uri> ->
            uris.forEach { uri ->
                CoroutineScope(Dispatchers.IO).launch {
                    val httpClient = DefaultHttpClient()
                    val assetRetriever = AssetRetriever(
                        contentResolver = context.contentResolver,
                        httpClient = httpClient
                    )
                    val publicationOpener = PublicationOpener(
                        publicationParser = DefaultPublicationParser(
                            context,
                            httpClient = httpClient,
                            assetRetriever = assetRetriever,
                            pdfFactory = PdfiumDocumentFactory(context)
                        )
                    )
                    val inputStream = context.contentResolver.openInputStream(uri)!!

                    val destFile = File(context.filesDir, AppUtils.getFileName(context, uri))
                    inputStream.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    val asset = assetRetriever.retrieve(destFile)
                        .getOrElse {
                            Log.e("ASSET", it.message.toString())
                            withContext(Dispatchers.Main){AppUtils.showToast(context, "Failed to import $uri")}
                        }
                    if (asset is Asset){
                        val publication = publicationOpener.open(asset, allowUserInteraction = true)
                            .getOrElse { withContext(Dispatchers.Main){AppUtils.showToast(context, "Failed to import $uri")} }
                        if (publication is Publication){
                            val title = publication.metadata.title ?: "Untitled Book"
                            val author = publication.metadata.authors.firstOrNull()?.name ?: "Unknown Author"
                            val bookKey = "${title}_${author}"
                            val firebaseBook = user?.books?.get(bookKey)
                            val progress = firebaseBook?.progress ?: ""

                            val existingBook = database.bookDao().findByTitleAndAuthor(title, author)

                            if (existingBook != null) {
                                if (existingBook.isDeleted) {
                                    database.bookDao().restoreBook(existingBook.id, destFile.absolutePath, uri.toString())
                                    database.bookDao().updateProgressionByTitleAndAuthor(title, author, progress)
                                    userViewModel.addBook(BookModel(title, author, progress), { success, message ->
                                        CoroutineScope(Dispatchers.Main).launch {
                                            AppUtils.showToast(context, message)
                                        }
                                    })
                                } else {
                                    withContext(Dispatchers.Main) {
                                        AppUtils.showToast(context, "Book is already in the library")
                                    }
                                }
                            } else {
                                val now = System.currentTimeMillis()
                                database.bookDao().insert(RoomBook(
                                    title = title,
                                    author = author,
                                    cover = publication.cover()?.let { AppUtils.saveBitmapToInternalStorage(context, it, title) },
                                    filePath = destFile.absolutePath,
                                    isFavourite = false,
                                    uri = uri.toString(),
                                    progression = progress,
                                    addedTimestamp = now
                                ))
                                userViewModel.addBook(BookModel(title, author, progress, addedTimestamp = now), { success, message ->
                                    CoroutineScope(Dispatchers.Main).launch {
                                        AppUtils.showToast(context, message)
                                    }
                                })
                            }
                        }
                    }
                }
            }
            onDismissRequest()
        }
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        tonalElevation = 8.dp
    ) {
        DropdownMenuItem(
            text = { Text("Import Books", style = MaterialTheme.typography.bodyLarge) },
            onClick = { launcher.launch(arrayOf("application/epub+zip")) },
            leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Teal, modifier = Modifier.size(24.dp)) }
        )
        DropdownMenuItem(
            text = { Text("Search", style = MaterialTheme.typography.bodyLarge) },
            onClick = onSearchClick,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Teal, modifier = Modifier.size(24.dp)) }
        )
        DropdownMenuItem(
            text = { Text("Filter", style = MaterialTheme.typography.bodyLarge) },
            onClick = onFilterClick,
            leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, tint = Teal, modifier = Modifier.size(24.dp)) }
        )
    }
}


@Preview
@Composable
fun LibraryScreenPreview(){
    LibraryScreen({}, {}, {}, {}, {})
}