package com.indeavour.coreader.screen

import android.content.ContentValues.TAG
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.indeavour.coreader.ui.theme.Teal
import kotlin.collections.mutableListOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.sp
import androidx.core.net.toFile
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.getValue
import com.indeavour.coreader.AppRoomDatabase
import com.indeavour.coreader.AppUtils
import com.indeavour.coreader.R
import com.indeavour.coreader.model.room.RoomBook
import kotlinx.coroutines.withContext
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.publication.Publication
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(routeToLogin: () -> Unit, routeToBook: () -> Unit){
    val context = LocalContext.current
    val database by lazy { AppRoomDatabase.getDatabase(context = context) }

    var books by remember {
        mutableStateOf(mutableListOf<MutableList<RoomBook>>())
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

    var displaySideMenu by rememberSaveable {
        mutableStateOf(false)
    }

    var displayMoreMenu by rememberSaveable {
        mutableStateOf(false)
    }

    val toggleMoreMenu: () -> Unit = {
        displayMoreMenu = !displayMoreMenu
    }

    Scaffold(topBar = {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Teal,
                titleContentColor = MaterialTheme.colorScheme.secondary),
            title = { Text("CoReader Library") },
            navigationIcon = {
                IconButton(onClick = { displaySideMenu = !displaySideMenu }) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "Open Burger Menu"
                    )
                }
            },
            actions = {
                IconButton(onClick = { displayMoreMenu = !displayMoreMenu }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Open More"
                    )
                }
            }
        )
    }) {
        innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.fillMaxSize().align(Alignment.TopCenter), verticalArrangement = Arrangement.Top) {
                for (row in books){
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        for (book in row){
                            Spacer(modifier = Modifier.width(4.dp))
                            BookCard(book, routeToBook, Modifier.weight(1f))
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
            if (displaySideMenu) SideMenu(Modifier.width(325.dp), routeToLogin)
            if (displayMoreMenu) MoreMenu(Modifier.width(200.dp).height(150.dp).align(Alignment.TopEnd), toggleMoreMenu)
        }
    }
}

@Composable
fun BookCard(book: RoomBook, routeToBook: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clickable( onClick = {routeToBook()}), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(10.dp))
        Image(
            bitmap = book.cover?.let { BitmapFactory.decodeFile(it) }?.asImageBitmap() ?: ImageBitmap.imageResource(R.drawable.logo),
            contentDescription = book.title,
            modifier = Modifier.fillMaxWidth().height(175.dp),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun SideMenu(modifier: Modifier, routeToLogin: () -> Unit){
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxSize().background(color = MaterialTheme.colorScheme.primaryContainer), verticalArrangement = Arrangement.SpaceBetween) {
        Column() {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()){
                Row(verticalAlignment = Alignment.CenterVertically){
                    IconButton(onClick = {  }, modifier = Modifier.clip(CircleShape)) {
                        Image(
                            painter = painterResource(R.drawable.logo),
                            contentDescription = "Open Burger Menu",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.clip(CircleShape).fillMaxSize()
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Username", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { }) {
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
                IconButton(onClick = { }) {
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
                                author = publication.metadata.authors[0].name,
                                cover = if (publication.cover() != null ) AppUtils.saveBitmapToInternalStorage(context, publication.cover()!!, publication.metadata.title ?: "Untitled Book") else null,
                                filePath = uri.path.toString(),
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