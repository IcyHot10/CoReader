package com.indeavour.coreader.screen

import android.util.Log
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.indeavour.coreader.ui.theme.Teal
import com.indeavour.coreader.viewmodel.ReaderViewModel
import java.io.File

@Composable
fun BookScreen(routeToLibrary: () -> Unit) {
    val activity = LocalActivity.current as? FragmentActivity
    val viewModel: ReaderViewModel = if (activity != null) {
        viewModel(viewModelStoreOwner = activity, factory = ReaderViewModel.Factory)
    } else {
        viewModel(factory = ReaderViewModel.Factory)
    }
    val publication by viewModel.publication.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadActiveBook()
    }

    DisposableEffect(Unit) {
        onDispose {
            // Save progression to Firestore when leaving the screen
            viewModel.saveProgressionToFirestore()

            // Clean up the fragment when the screen is disposed (navigated away)
            val fragment = activity?.supportFragmentManager?.findFragmentById(com.indeavour.coreader.R.id.book_container)
            if (fragment != null) {
                activity.supportFragmentManager.beginTransaction()
                    .remove(fragment)
                    .commitAllowingStateLoss()
                Log.d("BookScreen", "ReaderFragment removed on disposal")
            }
        }
    }

    val fragmentFactory = remember {
        { context: android.content.Context ->
            Log.d("BookScreen", "AndroidView factory called")
            FragmentContainerView(context).apply {
                id = com.indeavour.coreader.R.id.book_container
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                val activity = context as? FragmentActivity
                activity?.supportFragmentManager?.beginTransaction()
                    ?.replace(id, ReaderFragment())
                    ?.commit()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (publication != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = fragmentFactory,
                update = { }
            )
        } else if (error != null) {
            Text(text = "Error: $error", modifier = Modifier.align(Alignment.Center))
        } else {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Teal)
        }
    }
}
