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
        var isInterfaceVisible by remember { mutableStateOf(false) }
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        var isContainerReady by remember { mutableStateOf(false) }

        val colorScheme = MaterialTheme.colorScheme
        
        // Update Readium configuration when theme changes
        LaunchedEffect(colorScheme, publication, isContainerReady) {
            if (isContainerReady) {
                publication?.let {
                    showPublication(it, colorScheme)
                }
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
                Column(modifier = Modifier.fillMaxSize()) {
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

                    if (footerText.isNotEmpty()) {
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
        colorScheme: androidx.compose.material3.ColorScheme? = null
    ) {
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

        val config = EpubNavigatorFragment.Configuration().apply {
            colorScheme?.let { scheme ->
                readiumCssRsProperties = RsProperties(
                    backgroundColor = ReadiumColor.Int(scheme.background.toArgb()),
                    textColor = ReadiumColor.Int(scheme.onBackground.toArgb())
                )
            }
        }

        val factory = EpubNavigatorFactory(publication).createFragmentFactory(
            initialLocator = null,
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
    }

    override fun onExternalLinkActivated(url: AbsoluteUrl) {
        // Handle external links
    }
}
