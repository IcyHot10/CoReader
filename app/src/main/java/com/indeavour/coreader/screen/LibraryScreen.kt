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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.TopAppBarDefaults
import com.indeavour.coreader.model.firebase.UserModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.indeavour.coreader.viewmodel.UserViewModel
import androidx.compose.ui.Modifier
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
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
fun LibraryScreen(routeToLogin: () -> Unit, routeToBook: () -> Unit){
    val context = LocalContext.current
    val database by lazy { AppRoomDatabase.getDatabase(context = context) }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val userViewModel: UserViewModel = viewModel()
    val user by userViewModel.user.collectAsState()

    var books by remember {
        mutableStateOf(mutableListOf<MutableList<RoomBook>>())
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
            var counter = 0
            var row = -1
            val newBooks: MutableList<MutableList<RoomBook>> = mutableListOf()
            for (book in booksList) {
                if (counter % 3 == 0){
                    newBooks.add(mutableListOf(book))
                    row++
                } else {
                    newBooks[row].add(book)
                }
                counter++
            }
            books = newBooks
        }
    }

    var displayMoreMenu by rememberSaveable {
        mutableStateOf(false)
    }

    val toggleMoreMenu: () -> Unit = {
        displayMoreMenu = !displayMoreMenu
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(325.dp),
                drawerContainerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                SideMenuContent(user, userViewModel, routeToLogin)
            }
        }
    ) {
        Scaffold(topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Teal,
                    titleContentColor = MaterialTheme.colorScheme.secondary),
                title = { Text(if (isDeletionMode) "${selectedBookIds.size} Selected" else "CoReader Library") },
                navigationIcon = {
                    if (isDeletionMode) {
                        IconButton(onClick = { 
                            isDeletionMode = false 
                            selectedBookIds = emptySet()
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Exit Deletion Mode"
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
                                contentDescription = "Open Burger Menu"
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
                        }
                    } else {
                        IconButton(onClick = { displayMoreMenu = !displayMoreMenu }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Open More"
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
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Column(modifier = Modifier.fillMaxSize().align(Alignment.TopCenter).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Top) {
                    for (row in books){
                        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            for (book in row){
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
                                    }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            if (row.size < 3){
                                for (i in 0 until 3 - row.size) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Spacer(modifier = Modifier.weight(1f))
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                            }
                        }
                        HorizontalDivider(thickness = 10.dp, color = Teal)
                    }
                }
                if (displayMoreMenu) MoreMenu(Modifier.width(200.dp).height(150.dp).align(Alignment.TopEnd), toggleMoreMenu)
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
    onToggleSelection: () -> Unit
) {
    val database by lazy { AppRoomDatabase.getDatabase(context = context) }
    val scope = rememberCoroutineScope()
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
fun SideMenuContent(user: UserModel?, userViewModel: UserViewModel, routeToLogin: () -> Unit){
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }
    var newUsername by remember { mutableStateOf("") }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Update Username") },
            text = {
                TextField(
                    value = newUsername,
                    onValueChange = { newUsername = it },
                    label = { Text("New Username") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    userViewModel.updateUsername(newUsername) { success ->
                        if (success) {
                            showEditDialog = false
                        } else {
                            AppUtils.showToast(context, "Failed to update username")
                        }
                    }
                }) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
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
            val textButtonModifier = Modifier.fillMaxWidth().height(50.dp).align(Alignment.CenterHorizontally)
            Button(onClick = {}, modifier = textButtonModifier, shape = RectangleShape) { Text("Manage Group", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface) }
            Button(onClick = {}, modifier = textButtonModifier, shape = RectangleShape) { Text("View Group Book Progress", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface) }
            Button(onClick = {}, modifier = textButtonModifier, shape = RectangleShape) { Text("View To Read List", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()){
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = {
                    auth.signOut()
                    try {
                        val credentialManager = CredentialManager.create(context)
                        val clearRequest = ClearCredentialStateRequest()
                        CoroutineScope(Dispatchers.Main).launch { credentialManager.clearCredentialState(clearRequest) }
                    } catch (e: ClearCredentialException) {
                        Log.e(TAG, "Couldn't clear user credentials: ${e.localizedMessage}")
                    }
                    routeToLogin()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Logout",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text("Logout", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(5.dp))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { (context as? Activity)?.finishAndRemoveTask() }) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = "Exit",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text("Exit", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(5.dp))
            }
        }
    }
}

@Composable
fun MoreMenu(modifier: Modifier, toggle: () -> Unit){
    var context = LocalContext.current
    val database by lazy { AppRoomDatabase.getDatabase(context = context) }

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
                            database.bookDao().insert(RoomBook(
                                title = publication.metadata.title ?: "Untitled Book",
                                author = publication.metadata.authors.firstOrNull()?.name ?: "Unknown Author",
                                cover = publication.cover()?.let { AppUtils.saveBitmapToInternalStorage(context, it, publication.metadata.title ?: "Untitled Book") },
                                filePath = destFile.absolutePath,
                                isFavourite = false,
                                uri = uri.toString()
                            ))
                        }
                    }
                }
                toggle()
            }
        }
    )

    Column(modifier = modifier.fillMaxSize().background(color = MaterialTheme.colorScheme.primaryContainer)) {
        val textButtonModifier = Modifier.fillMaxWidth().height(50.dp).align(Alignment.CenterHorizontally)
        Button(onClick =
            {
                launcher.launch(arrayOf("application/epub+zip"))
            },
            modifier = textButtonModifier, shape = RectangleShape) { Text("Import Books", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface) }
        Button(onClick = {}, modifier = textButtonModifier, shape = RectangleShape) { Text("Search", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface) }
        Button(onClick = {}, modifier = textButtonModifier, shape = RectangleShape) { Text("Filter", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface) }
    }
}

@Preview
@Composable
fun LibraryScreenPreview(){
    LibraryScreen({}, {})
}