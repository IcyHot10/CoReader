package com.indeavour.coreader

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.indeavour.coreader.screen.BookScreen
import com.indeavour.coreader.screen.GroupScreen
import com.indeavour.coreader.screen.GroupProgressScreen
import com.indeavour.coreader.screen.LibraryScreen
import com.indeavour.coreader.screen.LoginScreen
import com.indeavour.coreader.screen.ReadingInsightsScreen
import com.indeavour.coreader.screen.StatsScreen
import com.indeavour.coreader.screen.ReadingListScreen
import com.indeavour.coreader.ui.theme.CoReaderTheme
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
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
    val auth = FirebaseAuth.getInstance()

    NavHost(navController = navController, startDestination = if (auth.currentUser == null) "login" else "library") {
        composable("login") {
            LoginScreen(routeToLibrary = {
                navController.navigate("library") {
                    popUpTo("login") { inclusive = true }
                }
            })
        }
        composable("library") {
            LibraryScreen(
                routeToLogin = {
                    navController.navigate("login") {
                        popUpTo("library") { inclusive = true }
                    }
                },
                routeToBook = { navController.navigate("book") },
                routeToGroup = { navController.navigate("group") },
                routeToProgress = { navController.navigate("progress") },
                routeToReadingInsights = { navController.navigate("reading_insights") }
            )
        }
        composable("reading_insights") {
            ReadingInsightsScreen(
                onBack = { navController.popBackStack() },
                routeToReadingList = { navController.navigate("reading_list") },
                routeToStats = { navController.navigate("stats") }
            )
        }
        composable("group") {
            GroupScreen(onBack = { 
                navController.popBackStack("library", inclusive = false)
            })
        }
        composable("reading_list") {
            ReadingListScreen(onBack = {
                navController.popBackStack()
            })
        }
        composable("progress") {
            GroupProgressScreen(onBack = { 
                navController.popBackStack("library", inclusive = false)
            })
        }
        composable("stats") {
            StatsScreen(onBack = {
                navController.popBackStack()
            })
        }
        composable("book") {
            BookScreen(routeToLibrary = {
                navController.popBackStack("library", inclusive = false)
            })
        }
    }
}

@Preview
@Composable
fun AppPreview(){
    AppNavigation()
}

