package com.example.coreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.coreader.ui.theme.CoReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CoReaderTheme {
                AppNavigation()
            }
        }
    }
}


@Composable
fun AppNavigation(){
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "login") {
        composable("login") { LoginScreen(routeToLibrary = {navController.navigate("library")}) }
        composable("library") { LibraryScreen(routeToLogin = {navController.navigate("login")}, routeToBook = {navController.navigate("book")}) }
        composable("book") { BookScreen(routeToLibrary = {navController.navigate("library")}) }
    }
}

@Preview
@Composable
fun AppPreview(){
    AppNavigation()
}

