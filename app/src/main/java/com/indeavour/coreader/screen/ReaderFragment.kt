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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
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
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
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
class ReaderFragment : Fragment(), EpubNavigatorFragment.Listener, InputListener, EpubNavigatorFragment.PaginationListener, DecorableNavigator.Listener {

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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ReaderScreen() {
        val publication by viewModel.publication.collectAsState()
        val progress by viewModel.progress.collectAsState()
        val groupProgress by viewModel.groupProgress.collectAsState()
        val remoteProgression by viewModel.remoteProgression.collectAsState()
        val isBookReady by viewModel.isBookReady.collectAsState()
        var isInterfaceVisible by remember { mutableStateOf(false) }
        var isColorPickerExpanded by remember { mutableStateOf(false) }
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        var isContainerReady by remember { mutableStateOf(false) }

        var isSyncDismissed by remember { mutableStateOf(false) }
        LaunchedEffect(remoteProgression) {
            isSyncDismissed = false
        }

        val showSyncButton = remember(remoteProgression, progress, isSyncDismissed) {
            if (isSyncDismissed) return@remember false
            val remoteTotal = remoteProgression?.locations?.totalProgression ?: 0.0
            val currentTotal = progress.value.toDouble()
            // Show if remote is ahead by more than 0.5% to avoid jitter
            remoteTotal > (currentTotal + 0.005)
        }

        var noteLocator by remember { mutableStateOf<Locator?>(null) }
        LaunchedEffect(Unit) {
            onShowNoteDialog = { locator ->
                noteLocator = locator
            }
        }

        if (noteLocator != null) {
            var noteText by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { noteLocator = null },
                title = { Text("Add Note", color = MaterialTheme.colorScheme.secondary) },
                text = {
                    TextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = { Text("Enter your note...") },
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
                            if (noteText.isNotBlank()) {
                                viewModel.addNote(noteLocator!!, noteText)
                            }
                            noteLocator = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Teal)
                    ) {
                        Text("Save", color = MaterialTheme.colorScheme.secondary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { noteLocator = null }) {
                        Text("Cancel", color = Teal)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        val colorScheme = MaterialTheme.colorScheme
        
        // Update Readium configuration when publication changes, but only if navigator is missing
        LaunchedEffect(publication, isContainerReady) {
            if (isContainerReady && publication != null) {
                showPublication(publication!!, colorScheme, viewModel.initialLocator.value)
            }
        }

        val highlights by viewModel.highlights.collectAsState()
        val notes by viewModel.notes.collectAsState()
        val fontSize by viewModel.fontSize.collectAsState()

        LaunchedEffect(highlights, notes, isBookReady) {
            if (isBookReady) {
                applyAnnotations(highlights, notes)
            }
        }

        // Handle font size changes
        LaunchedEffect(fontSize) {
            val navigator = childFragmentManager.findFragmentByTag("navigator") as? EpubNavigatorFragment
            if (navigator != null) {
                val updatedPreferences = currentPreferences?.copy(
                    fontSize = fontSize / 100.0
                ) ?: EpubPreferences(fontSize = fontSize / 100.0)
                currentPreferences = updatedPreferences
                navigator.submitPreferences(updatedPreferences)
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
                
                val preferences = (currentPreferences ?: EpubPreferences()).copy(
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
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleLarge,
                        color = colorScheme.secondary
                    )
                    if (groupProgress.isNotEmpty()) {
                        Text(
                            "Group Activity: ${groupProgress.size} members",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.secondary.copy(alpha = 0.7f)
                        )
                    }
                    HorizontalDivider(color = colorScheme.secondary.copy(alpha = 0.2f))
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        publication?.tableOfContents?.let { toc ->
                            items(toc) { link ->
                                val membersInChapter = groupProgress.filter { 
                                    // link.url() and it.locator.href are both Readium Url objects
                                    it.locator.href == link.url() || 
                                    it.locator.href.toString().substringBefore("#") == link.url().toString().substringBefore("#")
                                }
                                
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val navigator = childFragmentManager.findFragmentByTag("navigator") as? EpubNavigatorFragment
                                            navigator?.go(link, animated = true)
                                            scope.launch { drawerState.close() }
                                            isInterfaceVisible = false
                                        }
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = link.title ?: "Untitled",
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = colorScheme.secondary
                                        )
                                        
                                        if (membersInChapter.isNotEmpty()) {
                                            Row(
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                membersInChapter.forEach { member ->
                                                    Surface(
                                                        modifier = Modifier.size(24.dp),
                                                        shape = CircleShape,
                                                        color = colorScheme.secondary
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = member.username.take(1).uppercase().ifEmpty { "?" },
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontSize = 12.sp,
                                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                                                ),
                                                                color = Teal // Contrasts with colorScheme.secondary which is likely light
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                }
                                            }
                                        }
                                    }
                                }
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
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (isBookReady) 1f else 0f)
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
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 5.dp)
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

                    // UI Overlays (Top/Bottom bars)
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

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        IconButton(onClick = { isColorPickerExpanded = !isColorPickerExpanded }) {
                                            Icon(
                                                imageVector = if (isColorPickerExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Toggle Color Picker",
                                                tint = colorScheme.secondary
                                            )
                                        }
                                    }

                                    AnimatedVisibility(visible = isColorPickerExpanded) {
                                        Column {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val highlightColors = listOf(
                                                    0x66FFFF00, // Yellow
                                                    0x6600FF00, // Green
                                                    0x6600FFFF, // Cyan
                                                    0x66FF00FF, // Pink
                                                    0x66FF0000  // Red
                                                )
                                                val selectedColor by viewModel.selectedHighlightColor.collectAsState()

                                                highlightColors.forEach { color ->
                                                    Box(
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .padding(4.dp)
                                                            .background(
                                                                color = androidx.compose.ui.graphics.Color(color)
                                                                    .copy(alpha = 1f),
                                                                shape = CircleShape
                                                            )
                                                            .clickable {
                                                                viewModel.setSelectedHighlightColor(
                                                                    color
                                                                )
                                                            }
                                                            .then(
                                                                if (selectedColor == color) {
                                                                    Modifier.border(
                                                                        2.dp,
                                                                        colorScheme.secondary,
                                                                        CircleShape
                                                                    )
                                                                } else Modifier
                                                            )
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(onClick = { viewModel.setFontSize((fontSize - 10f).coerceAtLeast(50f)) }) {
                                                    Icon(
                                                        imageVector = Icons.Default.TextDecrease,
                                                        contentDescription = "Decrease Font Size",
                                                        tint = colorScheme.secondary
                                                    )
                                                }
                                                Text(
                                                    text = "${fontSize.toInt()}%",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = colorScheme.secondary,
                                                    modifier = Modifier.padding(horizontal = 16.dp)
                                                )
                                                IconButton(onClick = { viewModel.setFontSize((fontSize + 10f).coerceAtMost(300f)) }) {
                                                    Icon(
                                                        imageVector = Icons.Default.TextIncrease,
                                                        contentDescription = "Increase Font Size",
                                                        tint = colorScheme.secondary
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
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

                    // Sync Progression Button
                    AnimatedVisibility(
                        visible = showSyncButton && isBookReady,
                        enter = slideInVertically(initialOffsetY = { -it }),
                        exit = slideOutVertically(targetOffsetY = { -it }),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 80.dp)
                    ) {
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = {
                                if (it != SwipeToDismissBoxValue.Settled) {
                                    isSyncDismissed = true
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {},
                            content = {
                                Button(
                                    onClick = {
                                        remoteProgression?.let { locator ->
                                            val navigator = childFragmentManager.findFragmentByTag("navigator") as? EpubNavigatorFragment
                                            navigator?.go(locator, animated = true)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Teal,
                                        contentColor = colorScheme.secondary
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDownload,
                                            contentDescription = "Sync",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Jump to cloud progress")
                                    }
                                }
                            }
                        )
                    }

                    // Loading Screen Overlay
                    if (!isBookReady) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = Teal,
                                    strokeWidth = 4.dp
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "Preparing your book...",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colorScheme.onBackground.copy(alpha = 0.7f)
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

    override fun onPause() {
        super.onPause()
        viewModel.saveProgressionToFirestore()
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
    private var onShowNoteDialog: ((Locator) -> Unit)? = null

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
            textColor = colorScheme?.let { PreferenceColor(it.onBackground.toArgb()) },
            fontSize = viewModel.fontSize.value / 100.0
        )
        currentPreferences = initialPreferences

        val config = EpubNavigatorFragment.Configuration().apply {
            colorScheme?.let { scheme ->
                readiumCssRsProperties = RsProperties(
                    backgroundColor = ReadiumColor.Int(scheme.background.toArgb()),
                    textColor = ReadiumColor.Int(scheme.onBackground.toArgb())
                )
            }
            selectionActionModeCallback = object : android.view.ActionMode.Callback {
                override fun onCreateActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
                    menu.add(0, 1001, 0, "Highlight")
                    menu.add(0, 1002, 0, "Note")
                    return true
                }

                override fun onPrepareActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean = false

                override fun onActionItemClicked(mode: android.view.ActionMode, item: android.view.MenuItem): Boolean {
                    when (item.itemId) {
                        1001 -> {
                            val navigator = childFragmentManager.findFragmentByTag("navigator") as? EpubNavigatorFragment
                            navigator?.lifecycleScope?.launch {
                                val selection = navigator.currentSelection()
                                selection?.locator?.let {
                                    viewModel.addHighlight(it)
                                }
                                mode.finish()
                            }
                            return true
                        }
                        1002 -> {
                            val navigator = childFragmentManager.findFragmentByTag("navigator") as? EpubNavigatorFragment
                            navigator?.lifecycleScope?.launch {
                                val selection = navigator.currentSelection()
                                selection?.locator?.let { locator ->
                                    showNoteInputDialog(locator)
                                }
                                mode.finish()
                            }
                            return true
                        }
                    }
                    return false
                }

                override fun onDestroyActionMode(mode: android.view.ActionMode) {}
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
            val navigator = childFragmentManager.findFragmentByTag("navigator") as? EpubNavigatorFragment
            colorScheme?.let { scheme ->
                navigator?.view?.setBackgroundColor(scheme.background.toArgb())
            }
            // Register decoration listener
            navigator?.addDecorationListener("highlights", this@ReaderFragment)
        }

        transaction.commit()
    }

    private fun showNoteInputDialog(locator: Locator) {
        onShowNoteDialog?.invoke(locator)
    }

    private fun applyAnnotations(highlights: List<ReaderViewModel.HighlightData>, notes: List<ReaderViewModel.NoteData>) {
        val navigator = childFragmentManager.findFragmentByTag("navigator") as? DecorableNavigator ?: return
        Log.d("ReaderFragment", "Applying ${highlights.size} highlights and ${notes.size} notes")
        
        val highlightDecorations = highlights.mapIndexed { index, data ->
            Decoration(
                id = "highlight-$index",
                locator = data.locator,
                style = Decoration.Style.Highlight(tint = data.color, isActive = false),
                extras = mapOf("userId" to data.userId, "type" to "highlight")
            )
        }

        val noteDecorations = notes.mapIndexed { index, data ->
            Decoration(
                id = "note-$index",
                locator = data.locator,
                style = Decoration.Style.Highlight(tint = data.color, isActive = true),
                extras = mapOf("userId" to data.userId, "type" to "note", "content" to data.content)
            )
        }

        lifecycleScope.launch {
            navigator.applyDecorations(highlightDecorations + noteDecorations, "highlights")
        }
    }

    override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
        if (event.group == "highlights") {
            val userId = event.decoration.extras["userId"] as? String
            val type = event.decoration.extras["type"] as? String
            val content = event.decoration.extras["content"] as? String
            
            if (userId != null) {
                val username = viewModel.usernames.value[userId] ?: "Unknown User"
                val message = if (type == "note" && content != null) {
                    "$username's Note: $content"
                } else {
                    "Highlighted by: $username"
                }
                android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_LONG).show()
                return true
            }
        }
        return false
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

        // Only hide the loading screen if we aren't ready yet.
        // We no longer wait for totalPages > 1 specifically here,
        // but we keep the delay to ensure the UI has updated its display.
        if (!viewModel.isBookReady.value) {
            viewLifecycleOwner.lifecycleScope.launch {
                kotlinx.coroutines.delay(200)
                Log.d("ReaderFragment", "onPageChanged: Revealing book")
                viewModel.setBookReady(true)
            }
        }
    }

    private var hasInitialRecalculationDone = false

    override fun onPageLoaded() {
        super.onPageLoaded()
        
        Log.d("ReaderFragment", "onPageLoaded called. hasInitialRecalculationDone: $hasInitialRecalculationDone")

        // Re-apply annotations whenever a page is loaded to ensure they are visible
        applyAnnotations(viewModel.highlights.value, viewModel.notes.value)

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
