package com.indeavour.coreader

import android.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.indeavour.coreader.ui.theme.CoReaderTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(routeToLogin: () -> Unit, routeToBook: () -> Unit){

    var books by remember {
        mutableStateOf(mutableListOf<String>())
    }

    var displaySideMenu by rememberSaveable {
        mutableStateOf(false)
    }

    var displayMoreMenu by rememberSaveable {
        mutableStateOf(false)
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
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Book1")
                    Text("Book2")
                    Text("Book3")
                }
                HorizontalDivider(thickness = 10.dp, color = Teal)
            }
            if (displaySideMenu) SideMenu(Modifier.width(325.dp))
            if (displayMoreMenu) MoreMenu(Modifier.width(200.dp).height(150.dp).align(Alignment.TopEnd))
        }
    }
}

@Composable
fun SideMenu(modifier: Modifier){
    Column(modifier = modifier.fillMaxSize().background(color = MaterialTheme.colorScheme.primaryContainer), verticalArrangement = Arrangement.SpaceBetween) {
        Column() {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()){
                Row(verticalAlignment = Alignment.CenterVertically){
                    IconButton(onClick = {  }, modifier = Modifier.clip(CircleShape)) {
                        Image(
                            painter = painterResource(com.indeavour.coreader.R.drawable.logo),
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
            Image(painter = painterResource(com.indeavour.coreader.R.drawable.library_image), contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
            val textButtonModifier = Modifier.fillMaxWidth().height(50.dp).align(Alignment.CenterHorizontally)
            Button(onClick = {}, modifier = textButtonModifier, shape = RectangleShape) { Text("Manage Group", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface) }
            Button(onClick = {}, modifier = textButtonModifier, shape = RectangleShape) { Text("View Group Book Progress", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface) }
            Button(onClick = {}, modifier = textButtonModifier, shape = RectangleShape) { Text("View To Read List", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()){
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { }) {
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
fun MoreMenu(modifier: Modifier){
    Column(modifier = modifier.fillMaxSize().background(color = MaterialTheme.colorScheme.primaryContainer)) {
        val textButtonModifier = Modifier.fillMaxWidth().height(50.dp).align(Alignment.CenterHorizontally)
        Button(onClick = {}, modifier = textButtonModifier, shape = RectangleShape) { Text("Import Books", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface) }
        Button(onClick = {}, modifier = textButtonModifier, shape = RectangleShape) { Text("Search", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface) }
        Button(onClick = {}, modifier = textButtonModifier, shape = RectangleShape) { Text("Filter", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface) }
    }
}

@Preview
@Composable
fun LibraryScreenPreview(){
    LibraryScreen({}, {})
}