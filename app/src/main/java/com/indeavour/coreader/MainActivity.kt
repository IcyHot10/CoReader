package com.indeavour.coreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.indeavour.coreader.ui.theme.CoReaderTheme

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
        composable("login") { LoginScreen(routeToLibrary = {navController.navigate("library"){
            popUpTo("login") { inclusive = true }
        } }) }
        composable("library") { LibraryScreen(routeToLogin = {navController.navigate("login"){
            popUpTo("library") { inclusive = true }
        } }, routeToBook = {navController.navigate("book")}) }
        composable("book") { BookScreen(routeToLibrary = {navController.navigate("library")}) }
    }
}

@Preview
@Composable
fun AppPreview(){
    AppNavigation()
}

