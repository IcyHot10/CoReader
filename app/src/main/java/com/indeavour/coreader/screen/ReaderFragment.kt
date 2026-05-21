package com.indeavour.coreader.screen

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.util.Log
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.indeavour.coreader.R
import com.indeavour.coreader.ui.theme.CoReaderTheme
import com.indeavour.coreader.ui.theme.Teal
import com.indeavour.coreader.viewmodel.ReaderViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.css.Color as ReadiumColor
import org.readium.r2.navigator.epub.css.RsProperties
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Color as PreferenceColor
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl

@OptIn(ExperimentalReadiumApi::class)
class ReaderFragment : Fragment(), EpubNavigatorFragment.Listener, InputListener, EpubNavigatorFragment.PaginationListener {

    private lateinit var viewModel: ReaderViewModel

    private var currentPreferences: EpubPreferences? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Set a fallback factory to prevent InstantiationException during restoration
        // for fragments that require special factories (like EpubNavigatorFragment).
        val defaultFactory = childFragmentManager.fragmentFactory
        childFragmentManager.fragmentFactory = object : FragmentFactory() {
            override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
                return try {
                    defaultFactory.instantiate(classLoader, className)
                } catch (e: Exception) {
                    if (className.contains("EpubNavigatorFragment")) {
                        Fragment() // Placeholder to be removed immediately
                    } else {
                        throw e
                    }
                }
            }
        }

        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity(), ReaderViewModel.Factory)[ReaderViewModel::class.java]

        // Clear any restored navigator fragments to avoid "Fragment does not have a view" errors
        // because the container (R.id.reader_container) is not available during restoration.
        // We will re-add it in showPublication when the Compose UI is ready.
        childFragmentManager.findFragmentByTag("navigator")?.let {
            childFragmentManager.beginTransaction().remove(it).commitNow()
        }

        childFragmentManager.addFragmentOnAttachListener { _, fragment ->
            if (fragment is EpubNavigatorFragment) {
                fragment.addInputListener(this)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                CoReaderTheme {
                    ReaderScreen()
                }
            }
        }
    }

    @Composable
    private fun ReaderScreen() {
        val publication by viewModel.publication.collectAsState()
        val progress by viewModel.progress.collectAsState()
        val isBookReady by viewModel.isBookReady.collectAsState()
        var isInterfaceVisible by remember { mutableStateOf(false) }
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        var isContainerReady by remember { mutableStateOf(false) }

        val colorScheme = MaterialTheme.colorScheme
        
        // Update Readium configuration when publication changes, but only if navigator is missing
        LaunchedEffect(publication, isContainerReady) {
            if (isContainerReady && publication != null) {
                showPublication(publication!!, colorScheme, viewModel.initialLocator.value)
            }
        }
        
        // Handle theme changes separately to avoid recreating the navigator
        LaunchedEffect(colorScheme) {
            val navigator = childFragmentManager.findFragmentByTag("navigator") as? EpubNavigatorFragment
            if (navigator != null && colorScheme != null) {
                val theme = if (colorScheme.background.toArgb() == Color.BLACK || 
                    colorScheme.background.toArgb() < 0xFF444444.toInt()) {
                    Theme.DARK
                } else {
                    Theme.LIGHT
                }
                
                val preferences = EpubPreferences(
                    theme = theme,
                    backgroundColor = PreferenceColor(colorScheme.background.toArgb()),
                    textColor = PreferenceColor(colorScheme.onBackground.toArgb())
                )
                currentPreferences = preferences
                navigator.submitPreferences(preferences)
                
                // Update background of the container view as well
                view?.findViewById<View>(R.id.reader_container)?.setBackgroundColor(colorScheme.background.toArgb())
            }
        }
        
        // Separately handle the toggle callback update
        LaunchedEffect(isInterfaceVisible) {
            onToggleInterface = {
                isInterfaceVisible = !isInterfaceVisible
            }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen && isBookReady,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.fillMaxWidth(0.8f),
                    drawerContainerColor = Teal,
                    drawerContentColor = colorScheme.secondary
                ) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Table of Contents",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleLarge,
                        color = colorScheme.secondary
                    )
                    HorizontalDivider(color = colorScheme.secondary.copy(alpha = 0.2f))
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        publication?.tableOfContents?.let { toc ->
                            items(toc) { link ->
                                Text(
                                    text = link.title ?: "Untitled",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val navigator = childFragmentManager.findFragmentByTag("navigator") as? EpubNavigatorFragment
                                            navigator?.go(link, animated = true)
                                            scope.launch { drawerState.close() }
                                            isInterfaceVisible = false
                                        }
                                        .padding(16.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.background)
            ) {
                // We keep the main content structure but wrap it in an alpha/visibility layer
                // so that the background remains "blank" (showing only the Box background)
                // until isBookReady is true.
                
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        // Navigator Container
                        AndroidViewBinding(
                            factory = { inflater, parent, attachToParent ->
                                FrameLayout(inflater.context).apply {
                                    id = R.id.reader_container
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    post { isContainerReady = true }
                                }
                            },
                            update = { },
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )

                        // Permanent Footer (Page/Chapter Info)
                        val footerText = remember(progress) {
                            if (progress.chapterLabel.isNotEmpty()) {
                                "${progress.chapterLabel} • ${progress.pageLabel}"
                            } else {
                                progress.pageLabel
                            }
                        }

                        if (isBookReady && footerText.isNotEmpty()) {
                            Text(
                                text = footerText,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .padding(vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onBackground.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // UI Overlays (Top/Bottom bars) - also hidden until ready
                    if (isBookReady) {
                        // Bottom Progress Bar
                        AnimatedVisibility(
                            visible = isInterfaceVisible,
                            enter = slideInVertically(initialOffsetY = { it }),
                            exit = slideOutVertically(targetOffsetY = { it }),
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            Surface(
                                color = Teal,
                                tonalElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .windowInsetsPadding(WindowInsets.navigationBars)
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(colorScheme.secondary.copy(alpha = 0.2f))
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (progress.chapterLabel.isNotEmpty()) {
                                                Text(
                                                    text = progress.chapterLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colorScheme.secondary
                                                )
                                                Text(
                                                    text = " • ",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colorScheme.secondary.copy(alpha = 0.6f)
                                                )
                                            }
                                            Text(
                                                text = progress.pageLabel,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colorScheme.secondary
                                            )
                                        }
                                        Text(
                                            text = progress.percentageLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.secondary
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    LinearProgressIndicator(
                                        progress = { progress.value },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = colorScheme.secondary,
                                        trackColor = colorScheme.secondary.copy(alpha = 0.2f),
                                    )
                                }
                            }
                        }

                        // Top Bar
                        AnimatedVisibility(
                            visible = isInterfaceVisible,
                            enter = slideInVertically(initialOffsetY = { -it }),
                            exit = slideOutVertically(targetOffsetY = { -it }),
                            modifier = Modifier.align(Alignment.TopCenter)
                        ) {
                            Surface(
                                color = Teal,
                                tonalElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .windowInsetsPadding(WindowInsets.statusBars)
                                        .height(56.dp)
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { 
                                        scope.launch { drawerState.open() }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Menu,
                                            contentDescription = "Menu",
                                            tint = colorScheme.secondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    publication?.metadata?.title?.let { title ->
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                fontSize = 20.sp
                                            ),
                                            color = colorScheme.secondary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Helper to embed legacy Android View in Compose
    @Composable
    private fun AndroidViewBinding(
        factory: (LayoutInflater, ViewGroup, Boolean) -> View,
        update: (View) -> Unit,
        modifier: Modifier = Modifier
    ) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { context ->
                factory(LayoutInflater.from(context), FrameLayout(context), false)
            },
            update = update,
            modifier = modifier
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.publication.collectLatest { publication ->
                    // Initial load handled by ReaderScreen's LaunchedEffect
                }
            }
        }
    }

    private var onToggleInterface: (() -> Unit)? = null

    private fun showPublication(
        publication: Publication,
        colorScheme: androidx.compose.material3.ColorScheme? = null,
        initialLocator: org.readium.r2.shared.publication.Locator? = null
    ) {
        if (!viewModel.hasEverLoaded) {
            viewModel.setBookReady(false)
        }
        hasInitialRecalculationDone = false
        val container = view?.findViewById<View>(R.id.reader_container)
        if (container == null) return

        val theme = if (colorScheme?.background?.toArgb() == Color.BLACK || 
            (colorScheme?.background?.toArgb() ?: 0) < 0xFF444444.toInt()) {
            Theme.DARK
        } else {
            Theme.LIGHT
        }

        val initialPreferences = EpubPreferences(
            theme = theme,
            backgroundColor = colorScheme?.let { PreferenceColor(it.background.toArgb()) },
            textColor = colorScheme?.let { PreferenceColor(it.onBackground.toArgb()) }
        )
        currentPreferences = initialPreferences

        val config = EpubNavigatorFragment.Configuration().apply {
            colorScheme?.let { scheme ->
                readiumCssRsProperties = RsProperties(
                    backgroundColor = ReadiumColor.Int(scheme.background.toArgb()),
                    textColor = ReadiumColor.Int(scheme.onBackground.toArgb())
                )
            }
        }

        val factory = EpubNavigatorFactory(publication).createFragmentFactory(
            initialLocator = initialLocator,
            initialPreferences = initialPreferences,
            listener = this,
            paginationListener = this,
            configuration = config
        )

        childFragmentManager.fragmentFactory = factory

        val transaction = childFragmentManager.beginTransaction()
            .replace(R.id.reader_container, EpubNavigatorFragment::class.java, null, "navigator")

        transaction.runOnCommit {
            colorScheme?.let { scheme ->
                val navigatorView = childFragmentManager.findFragmentByTag("navigator")?.view
                navigatorView?.setBackgroundColor(scheme.background.toArgb())
            }
        }

        transaction.commit()
    }

    override fun onTap(event: TapEvent): Boolean {
        // Only process taps if the fragment is at least STARTED to avoid processing clicks 
        // intended for other screens (like the Library) when this fragment is in the backstack.
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return false

        val point = event.point
        val navigator = childFragmentManager.findFragmentByTag("navigator") as? EpubNavigatorFragment ?: return false
        val width = view?.width ?: return false
        
        return when {
            point.x < width * 0.3 -> {
                navigator.goBackward(animated = true)
                true
            }
            point.x > width * 0.7 -> {
                navigator.goForward(animated = true)
                true
            }
            else -> {
                onToggleInterface?.invoke()
                true
            }
        }
    }

    override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: Locator) {
        viewModel.updateProgress(pageIndex, totalPages, locator)
        
        // Safety fallback: If we have meaningful pagination information, the book is definitely "ready"
        // even if onPageLoaded hasn't finished its jump-refresh yet.
        if (totalPages > 1 && !viewModel.isBookReady.value) {
            Log.d("ReaderFragment", "Setting book ready via onPageChanged fallback (totalPages: $totalPages)")
            viewModel.setBookReady(true)
        }
    }

    private var hasInitialRecalculationDone = false

    override fun onPageLoaded() {
        super.onPageLoaded()
        
        Log.d("ReaderFragment", "onPageLoaded called. hasInitialRecalculationDone: $hasInitialRecalculationDone")

        // Only run this once per "open" to avoid infinite loops during page turns
        if (hasInitialRecalculationDone) return
        
        val navigator = childFragmentManager.findFragmentByTag("navigator") as? EpubNavigatorFragment
        val preferences = currentPreferences
        
        if (navigator != null && preferences != null) {
            navigator.lifecycleScope.launch {
                try {
                    // Wait for the WebView to be fully interactive
                    kotlinx.coroutines.delay(500)
                    
                    Log.d("ReaderFragment", "Forcing initial pagination recalculation via jump-refresh")
                    
                    // 1. Get current position
                    val currentLocator = navigator.currentLocator.value
                    
                    // 2. Force a jump to the exact same position. 
                    navigator.go(currentLocator, animated = false)
                    
                    // 3. Re-submit preferences to trigger a layout pass
                    navigator.submitPreferences(preferences)
                    
                    hasInitialRecalculationDone = true
                    // Small delay to let the navigator process the jump before showing the UI
                    kotlinx.coroutines.delay(200)
                    viewModel.setBookReady(true)
                } catch (e: Exception) {
                    Log.e("ReaderFragment", "Error during initial pagination refresh", e)
                    hasInitialRecalculationDone = true
                    viewModel.setBookReady(true)
                }
            }
        } else {
            Log.d("ReaderFragment", "Navigator or preferences missing in onPageLoaded, using fallback")
            hasInitialRecalculationDone = true
            viewModel.setBookReady(true)
        }
        
        // Universal safety: if we still aren't ready after 3 seconds, just show it
        viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.delay(3000)
            if (!viewModel.isBookReady.value) {
                Log.d("ReaderFragment", "Ready-state timeout reached, forcing ready")
                viewModel.setBookReady(true)
            }
        }
    }

    override fun onExternalLinkActivated(url: AbsoluteUrl) {
        // Handle external links
    }
}
