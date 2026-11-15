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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.doOnLayout
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
import com.google.firebase.auth.FirebaseAuth
import com.indeavour.coreader.domain.QuestionStage
import com.indeavour.coreader.model.firebase.MemberColour
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
                val themePreference by viewModel.theme.collectAsState()
                val isDark = when(themePreference) {
                    "light" -> false
                    "dark" -> true
                    "sepia" -> false
                    else -> isSystemInDarkTheme()
                }
                CoReaderTheme(darkTheme = isDark) {
                    val colorScheme = if (themePreference == "sepia") {
                        MaterialTheme.colorScheme.copy(
                            background = androidx.compose.ui.graphics.Color(0xFFF5ECDB),
                            onBackground = androidx.compose.ui.graphics.Color.Black,
                            surface = androidx.compose.ui.graphics.Color(0xFFF5ECDB),
                            onSurface = androidx.compose.ui.graphics.Color.Black,
                            secondary = androidx.compose.ui.graphics.Color(0xFF093624) // BottleGreen
                        )
                    } else {
                        MaterialTheme.colorScheme
                    }

                    MaterialTheme(colorScheme = colorScheme) {
                        ReaderScreen()
                    }
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
        val returnLocator by viewModel.returnLocator.collectAsState()
        val memberColours by viewModel.memberColours.collectAsState()
        val isGroupBook by viewModel.isGroupBook.collectAsState()
        val isBookReady by viewModel.isBookReady.collectAsState()
        val isAdmin by viewModel.isAdmin.collectAsState()
        val usernames by viewModel.usernames.collectAsState()
        var isInterfaceVisible by remember { mutableStateOf(false) }
        var isColorPickerExpanded by remember { mutableStateOf(false) }
        var showNotesSheet by remember { mutableStateOf(false) }
        var showAskSheet by remember { mutableStateOf(false) }
        val bookChat by viewModel.bookChat.collectAsState()
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        var isContainerReady by remember { mutableStateOf(false) }

        // Position of the progress slider while it is being dragged (and until the jump it
        // caused has landed); null when it simply mirrors the reading position.
        var sliderValue by remember { mutableStateOf<Float?>(null) }
        var isDraggingSlider by remember { mutableStateOf(false) }
        LaunchedEffect(progress.value) {
            if (!isDraggingSlider) sliderValue = null
        }

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

        var replyingToNote by remember { mutableStateOf<ReaderViewModel.NoteData?>(null) }
        LaunchedEffect(Unit) {
            onShowReplyDialog = { note ->
                replyingToNote = note
            }
        }

        if (replyingToNote != null) {
            var replyText by remember { mutableStateOf("") }
            val note = replyingToNote!!
            val username = viewModel.usernames.collectAsState().value[note.userId] ?: "Unknown User"

            AlertDialog(
                onDismissRequest = { replyingToNote = null },
                title = { Text("Note by $username", color = MaterialTheme.colorScheme.secondary) },
                text = {
                    Column {
                        Text(note.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)

                        if (note.replies.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Replies:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f))
                            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                items(note.replies) { reply ->
                                    val replierName = viewModel.usernames.collectAsState().value[reply.userId] ?: "Unknown"
                                    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                                    val isAdmin by viewModel.isAdmin.collectAsState()

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = replierName,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                                            )
                                            Text(
                                                text = reply.text,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                        if (reply.userId == currentUserId || isAdmin) {
                                            IconButton(
                                                onClick = { viewModel.deleteReply(note, reply) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Reply",
                                                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        TextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = { Text("Add a reply...") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = Teal,
                                cursorColor = Teal
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (replyText.isNotBlank()) {
                                viewModel.addReplyToNote(note, replyText)
                            }
                            replyingToNote = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Teal)
                    ) {
                        Text("Reply", color = MaterialTheme.colorScheme.secondary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { replyingToNote = null }) {
                        Text("Close", color = Teal)
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

        // Push every change straight to the navigator, including ones that arrive from other
        // group members while the book is open.
        LaunchedEffect(highlights, notes, isBookReady) {
            applyAnnotations(highlights, notes)
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
                val themePreference = viewModel.theme.value
                val theme = when (themePreference) {
                    "dark" -> Theme.DARK
                    "sepia" -> Theme.SEPIA
                    "light" -> Theme.LIGHT
                    else -> if (colorScheme.background.toArgb() == android.graphics.Color.BLACK) Theme.DARK else Theme.LIGHT
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

        var pageCurl by remember { mutableStateOf(pageCurlEnabled) }
        LaunchedEffect(pageCurl, colorScheme) {
            pageCurlEnabled = pageCurl
            pageTurnLayout?.curlEnabled = pageCurl
            pageTurnLayout?.paperColor = colorScheme.background.toArgb()
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
                                            viewModel.markReturnPoint()
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
                                                    val memberColour = androidx.compose.ui.graphics.Color(
                                                        memberColours[member.userId]
                                                            ?: MemberColour.resolve(member.userId, null)
                                                    )
                                                    Surface(
                                                        modifier = Modifier.size(24.dp),
                                                        shape = CircleShape,
                                                        color = memberColour
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = MemberColour.initials(member.username),
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontSize = 10.sp,
                                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                                                ),
                                                                color = readableOn(memberColour)
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
                                PageTurnLayout(inflater.context).apply {
                                    // The navigator lives in the inner container; this layer
                                    // draws the page-turn effect over it.
                                    content.id = R.id.reader_container
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    window = requireActivity().window
                                    curlEnabled = pageCurlEnabled
                                    paperColor = colorScheme.background.toArgb()
                                    onMovePage = { forward ->
                                        val navigator = childFragmentManager.findFragmentByTag("navigator") as? EpubNavigatorFragment
                                        when {
                                            navigator == null -> false
                                            forward -> navigator.goForward(animated = false)
                                            else -> navigator.goBackward(animated = false)
                                        }
                                    }
                                    pageTurnLayout = this
                                    // Create the navigator only once the container has its final
                                    // size, so the EPUB is paginated exactly once.
                                    doOnLayout { isContainerReady = true }
                                }
                            },
                            update = { },
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 5.dp)
                        )

                        // Permanent Footer (Page/Chapter Info).
                        // It is always laid out, even while empty and hidden behind the
                        // loader: it sits below the navigator container, so letting it
                        // appear later would shrink the WebView and force Readium to
                        // re-paginate the chapter on screen.
                        val footerText = remember(progress) {
                            if (progress.chapterLabel.isNotEmpty()) {
                                "${progress.chapterLabel} • ${progress.pageLabel}"
                            } else {
                                progress.pageLabel
                            }
                        }

                        Text(
                            text = footerText.ifEmpty { " " },
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onBackground.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                                        Row(
                                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // While dragging, preview where the slider would land.
                                            val previewValue = sliderValue
                                            val chapterText = if (previewValue != null) {
                                                remember(previewValue) { viewModel.chapterAt(previewValue) }
                                            } else {
                                                progress.chapterLabel
                                            }
                                            if (chapterText.isNotEmpty()) {
                                                Text(
                                                    text = chapterText,
                                                    modifier = Modifier.weight(1f, fill = false),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colorScheme.secondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (previewValue == null) {
                                                    Text(
                                                        text = " • ",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = colorScheme.secondary.copy(alpha = 0.6f)
                                                    )
                                                }
                                            }
                                            if (previewValue == null) {
                                                Text(
                                                    text = progress.pageLabel,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colorScheme.secondary,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                        Text(
                                            text = sliderValue
                                                ?.let { "${kotlin.math.round(it * 100).toInt()}%" }
                                                ?: progress.percentageLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.secondary
                                        )
                                    }

                                    // Where the other group members reading this book have got to,
                                    // lined up with the slider below.
                                    val ownUid = remember { FirebaseAuth.getInstance().currentUser?.uid }
                                    val otherReaders = remember(groupProgress, ownUid) {
                                        groupProgress.mapNotNull { member ->
                                            val position = member.locator.locations.totalProgression
                                            if (member.userId == ownUid || position == null) null
                                            else member to position.toFloat().coerceIn(0f, 1f)
                                        }
                                    }
                                    if (otherReaders.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(30.dp)) {
                                            val bubble = 24.dp
                                            // The slider's track stops half a thumb short of each edge.
                                            val trackInset = 2.dp
                                            val trackWidth = maxWidth - trackInset * 2
                                            otherReaders.forEach { (member, position) ->
                                                val name = member.username.ifBlank { "A group member" }
                                                val x = (trackInset + trackWidth * position - bubble / 2)
                                                    .coerceIn(0.dp, maxWidth - bubble)
                                                Column(
                                                    modifier = Modifier
                                                        .offset(x = x)
                                                        .width(bubble)
                                                        .clickable {
                                                            android.widget.Toast.makeText(
                                                                requireContext(),
                                                                "$name is at ${kotlin.math.round(position * 100).toInt()}%",
                                                                android.widget.Toast.LENGTH_SHORT
                                                            ).show()
                                                        },
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    val memberColour = androidx.compose.ui.graphics.Color(
                                                        memberColours[member.userId]
                                                            ?: MemberColour.resolve(member.userId, null)
                                                    )
                                                    Surface(
                                                        modifier = Modifier.size(bubble),
                                                        shape = CircleShape,
                                                        color = memberColour
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = MemberColour.initials(member.username),
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                ),
                                                                color = readableOn(memberColour)
                                                            )
                                                        }
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .width(2.dp)
                                                            .height(6.dp)
                                                            .background(memberColour)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Drag to move through the book; the jump happens on release.
                                    Slider(
                                        value = (sliderValue ?: progress.value).coerceIn(0f, 1f),
                                        onValueChange = {
                                            isDraggingSlider = true
                                            sliderValue = it
                                        },
                                        onValueChangeFinished = {
                                            isDraggingSlider = false
                                            val target = sliderValue
                                            val locator = target?.let { viewModel.locatorAt(it) }
                                            val navigator = childFragmentManager.findFragmentByTag("navigator") as? EpubNavigatorFragment
                                            if (locator != null && navigator != null) {
                                                viewModel.markReturnPoint(locator.locations.totalProgression)
                                                navigator.go(locator, animated = false)
                                                // The thumb stays put until the new page reports in;
                                                // this covers a jump that lands on the same page.
                                                scope.launch {
                                                    kotlinx.coroutines.delay(1500)
                                                    if (!isDraggingSlider) sliderValue = null
                                                }
                                            } else {
                                                sliderValue = null
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = SliderDefaults.colors(
                                            thumbColor = colorScheme.secondary,
                                            activeTrackColor = colorScheme.secondary,
                                            inactiveTrackColor = colorScheme.secondary.copy(alpha = 0.2f)
                                        )
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
                                            if (isGroupBook) {
                                                // The colour the rest of the group sees this
                                                // reader's initials on.
                                                val myUid = remember { FirebaseAuth.getInstance().currentUser?.uid }
                                                val myColour = myUid?.let { memberColours[it] }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "Your colour in this group",
                                                    modifier = Modifier.fillMaxWidth(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colorScheme.secondary.copy(alpha = 0.7f),
                                                    textAlign = TextAlign.Center
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    MemberColour.PALETTE.forEach { option ->
                                                        Box(
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .padding(4.dp)
                                                                .background(
                                                                    color = androidx.compose.ui.graphics.Color(option),
                                                                    shape = CircleShape
                                                                )
                                                                .clickable { viewModel.setMyMemberColour(option) }
                                                                .then(
                                                                    if (myColour == option) {
                                                                        Modifier.border(2.dp, colorScheme.secondary, CircleShape)
                                                                    } else Modifier
                                                                )
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val themePreference by viewModel.theme.collectAsState()
                                                val themeOptions = listOf(
                                                    Triple("system", Icons.Default.BrightnessAuto, "System"),
                                                    Triple("light", Icons.Default.BrightnessHigh, "Light"),
                                                    Triple("sepia", Icons.AutoMirrored.Filled.MenuBook, "Sepia"),
                                                    Triple("dark", Icons.Default.Brightness4, "Dark")
                                                )

                                                themeOptions.forEach { (theme, icon, label) ->
                                                    IconButton(
                                                        onClick = { viewModel.setTheme(theme) },
                                                        colors = IconButtonDefaults.iconButtonColors(
                                                            contentColor = if (themePreference == theme) colorScheme.secondary else colorScheme.secondary.copy(alpha = 0.5f)
                                                        )
                                                    ) {
                                                        Icon(icon, contentDescription = label)
                                                    }
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
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Page curl animation",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = colorScheme.secondary
                                                )
                                                Switch(
                                                    checked = pageCurl,
                                                    onCheckedChange = { pageCurl = it },
                                                    colors = SwitchDefaults.colors(
                                                        checkedThumbColor = Teal,
                                                        checkedTrackColor = colorScheme.secondary,
                                                        uncheckedThumbColor = colorScheme.secondary,
                                                        uncheckedTrackColor = Teal,
                                                        uncheckedBorderColor = colorScheme.secondary
                                                    )
                                                )
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
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                fontSize = 20.sp
                                            ),
                                            color = colorScheme.secondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = { showAskSheet = true }) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = "Ask about this book",
                                            tint = colorScheme.secondary
                                        )
                                    }
                                    IconButton(onClick = {
                                        showNotesSheet = true
                                    }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.StickyNote2,
                                            contentDescription = "Notes and Highlights",
                                            tint = colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Jump-back button. After jumping around the book it returns to the page the
                    // reader was on; otherwise it offers newer progress from another device.
                    val canReturn = returnLocator != null
                    AnimatedVisibility(
                        visible = (canReturn || showSyncButton) && isBookReady,
                        enter = slideInVertically(initialOffsetY = { -it }),
                        exit = slideOutVertically(targetOffsetY = { -it }),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 80.dp)
                    ) {
                        // Keyed so that swiping one button away does not leave the other hidden.
                        key(canReturn) {
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = {
                                    if (it != SwipeToDismissBoxValue.Settled) {
                                        if (canReturn) {
                                            viewModel.dismissReturnPoint()
                                        } else {
                                            isSyncDismissed = true
                                        }
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
                                            val navigator = childFragmentManager.findFragmentByTag("navigator") as? EpubNavigatorFragment
                                            val target = returnLocator ?: remoteProgression
                                            if (navigator != null && target != null) {
                                                if (canReturn) viewModel.clearReturnPoint()
                                                navigator.go(target, animated = true)
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
                                                imageVector = if (canReturn) {
                                                    Icons.AutoMirrored.Filled.Undo
                                                } else {
                                                    Icons.Default.CloudDownload
                                                },
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(if (canReturn) "Back to where you were" else "Jump to cloud progress")
                                        }
                                    }
                                }
                            )
                        }
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

        if (showAskSheet) {
            AskBookSheet(
                entries = bookChat,
                onAsk = { viewModel.askAboutBook(it) },
                onSpoilerChoice = { id, withSpoilers -> viewModel.chooseSpoilers(id, withSpoilers) },
                onClose = { showAskSheet = false },
                colorScheme = colorScheme
            )
        }

        if (showNotesSheet) {
            NotesAndHighlightsSheet(
                notes = notes,
                highlights = highlights,
                usernames = usernames,
                isAdmin = isAdmin,
                onClose = { showNotesSheet = false },
                onNavigate = { locator ->
                    val navigator = childFragmentManager.findFragmentByTag("navigator") as? EpubNavigatorFragment
                    viewModel.markReturnPoint(locator.locations.totalProgression)
                    navigator?.go(locator, animated = true)
                    showNotesSheet = false
                    isInterfaceVisible = false
                },
                onReply = { note ->
                    onShowReplyDialog?.invoke(note)
                },
                colorScheme = colorScheme
            )
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

    /** Questions to the assistant about the open book, and its spoiler-checked answers. */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AskBookSheet(
        entries: List<ReaderViewModel.BookChatEntry>,
        onAsk: (String) -> Unit,
        onSpoilerChoice: (Long, Boolean) -> Unit,
        onClose: () -> Unit,
        colorScheme: ColorScheme
    ) {
        var question by remember { mutableStateOf("") }
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        val isBusy = entries.lastOrNull()?.state is ReaderViewModel.BookChatState.Working

        // Keep the newest question and its answer in view.
        LaunchedEffect(entries.size, entries.lastOrNull()?.state) {
            if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
        }

        ModalBottomSheet(
            onDismissRequest = onClose,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Teal,
            contentColor = colorScheme.secondary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .imePadding()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    "Ask about this book",
                    style = MaterialTheme.typography.titleLarge,
                    color = colorScheme.secondary
                )
                Text(
                    "Answers are checked against the book's text, and so they don't give away anything past where you are.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.secondary.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (entries.isEmpty()) {
                        item {
                            Text(
                                "Try \"Who is this character again?\" or \"What does this word mean here?\"",
                                modifier = Modifier.padding(top = 24.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.secondary.copy(alpha = 0.6f)
                            )
                        }
                    }
                    items(entries, key = { it.id }) { entry ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // The question, set to the right like a sent message.
                            Surface(
                                modifier = Modifier.align(Alignment.End).padding(start = 48.dp),
                                shape = MaterialTheme.shapes.medium,
                                color = colorScheme.secondary.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    entry.question,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.secondary
                                )
                            }
                            Spacer(Modifier.height(6.dp))

                            when (val state = entry.state) {
                                is ReaderViewModel.BookChatState.Working -> Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = colorScheme.secondary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        when (state.stage) {
                                            QuestionStage.ANSWERING -> "Thinking…"
                                            QuestionStage.CHECKING -> "Checking the answer…"
                                            QuestionStage.REVISING -> "Improving the answer…"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.secondary.copy(alpha = 0.7f)
                                    )
                                }

                                is ReaderViewModel.BookChatState.Answered -> {
                                    Text(
                                        state.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colorScheme.secondary
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        buildString {
                                            append(if (state.containsSpoilers) "Contains spoilers" else "Spoiler-free")
                                            append(if (state.verified) " • checked" else " • couldn't be verified")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.secondary.copy(alpha = 0.6f)
                                    )
                                }

                                ReaderViewModel.BookChatState.SpoilerChoice -> {
                                    Text(
                                        "This can't be answered properly without giving away what happens later. Do you want the answer anyway?",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colorScheme.secondary
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { onSpoilerChoice(entry.id, false) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = colorScheme.secondary,
                                                contentColor = Teal
                                            )
                                        ) { Text("No spoilers") }
                                        OutlinedButton(
                                            onClick = { onSpoilerChoice(entry.id, true) },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = colorScheme.secondary
                                            )
                                        ) { Text("Show spoilers") }
                                    }
                                }

                                is ReaderViewModel.BookChatState.Failed -> Text(
                                    state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.secondary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = question,
                        onValueChange = { question = it },
                        placeholder = { Text("Ask a question…") },
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = Teal,
                            cursorColor = Teal
                        )
                    )
                    IconButton(
                        enabled = question.isNotBlank() && !isBusy,
                        onClick = {
                            onAsk(question)
                            question = ""
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Ask",
                            tint = colorScheme.secondary.copy(
                                alpha = if (question.isNotBlank() && !isBusy) 1f else 0.4f
                            )
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun NotesAndHighlightsSheet(
        notes: List<ReaderViewModel.NoteData>,
        highlights: List<ReaderViewModel.HighlightData>,
        usernames: Map<String, String>,
        isAdmin: Boolean,
        onClose: () -> Unit,
        onNavigate: (Locator) -> Unit,
        onReply: (ReaderViewModel.NoteData) -> Unit,
        colorScheme: ColorScheme
    ) {
        ModalBottomSheet(
            onDismissRequest = onClose,
            containerColor = Teal,
            contentColor = colorScheme.secondary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    "Notes & Highlights",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = colorScheme.secondary
                )

                val items = remember(notes, highlights) {
                    (notes.map { it as Any } + highlights.map { it as Any }).sortedBy {
                        (it as? ReaderViewModel.NoteData)?.locator?.locations?.totalProgression ?:
                        (it as? ReaderViewModel.HighlightData)?.locator?.locations?.totalProgression ?: 0.0
                    }
                }

                if (items.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No notes or highlights yet", color = colorScheme.secondary.copy(alpha = 0.6f))
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(items) { item ->
                            when (item) {
                                is ReaderViewModel.NoteData -> {
                                    NoteItem(
                                        note = item,
                                        username = usernames[item.userId] ?: "Unknown",
                                        usernames = usernames,
                                        isAdmin = isAdmin,
                                        onNavigate = onNavigate,
                                        onReply = onReply,
                                        onDelete = { viewModel.deleteNote(item) },
                                        colorScheme = colorScheme
                                    )
                                }
                                is ReaderViewModel.HighlightData -> {
                                    HighlightItem(
                                        highlight = item,
                                        username = usernames[item.userId] ?: "Unknown",
                                        isAdmin = isAdmin,
                                        onNavigate = onNavigate,
                                        onDelete = { viewModel.deleteHighlight(item) },
                                        colorScheme = colorScheme
                                    )
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = colorScheme.secondary.copy(alpha = 0.1f)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun NoteItem(
        note: ReaderViewModel.NoteData,
        username: String,
        usernames: Map<String, String>,
        isAdmin: Boolean,
        onNavigate: (Locator) -> Unit,
        onReply: (ReaderViewModel.NoteData) -> Unit,
        onDelete: () -> Unit,
        colorScheme: ColorScheme
    ) {
        val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigate(note.locator) }
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(androidx.compose.ui.graphics.Color(note.color).copy(alpha = 1f), CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$username's Note",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.secondary.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.secondary
                )

                note.locator.text.highlight?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"$it\"",
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                        color = colorScheme.secondary.copy(alpha = 0.6f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (note.replies.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    note.replies.forEach { reply ->
                        val replierName = usernames[reply.userId] ?: "Unknown"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = replierName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.secondary.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = reply.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.secondary.copy(alpha = 0.8f)
                                )
                            }
                            if (reply.userId == currentUserId || isAdmin) {
                                IconButton(
                                    onClick = { viewModel.deleteReply(note, reply) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Reply",
                                        tint = colorScheme.secondary.copy(alpha = 0.4f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row {
                IconButton(onClick = { onReply(note) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = "Reply",
                        tint = colorScheme.secondary.copy(alpha = 0.6f)
                    )
                }
                if (note.userId == currentUserId || isAdmin) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Note",
                            tint = colorScheme.secondary.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun HighlightItem(
        highlight: ReaderViewModel.HighlightData,
        username: String,
        isAdmin: Boolean,
        onNavigate: (Locator) -> Unit,
        onDelete: () -> Unit,
        colorScheme: ColorScheme
    ) {
        val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigate(highlight.locator) }
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(androidx.compose.ui.graphics.Color(highlight.color).copy(alpha = 1f), CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$username highlighted",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.secondary.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                highlight.locator.text.highlight?.let {
                    Text(
                        text = "\"$it\"",
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                        color = colorScheme.secondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                } ?: Text(
                    text = highlight.locator.title ?: "Highlighted section",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.secondary
                )
            }
            if (highlight.userId == currentUserId || isAdmin) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Highlight",
                        tint = colorScheme.secondary.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.endReadingSession()
        viewModel.saveProgressionToFirestore()
    }

    override fun onResume() {
        super.onResume()
        viewModel.resumeReadingSession()
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

    private companion object {
        const val READY_TIMEOUT_MS = 3000L
        const val READER_PREFS = "reader_prefs"
        const val PREF_PAGE_CURL = "page_curl"
    }

    /** Black or white, whichever reads better on [background]. */
    private fun readableOn(background: androidx.compose.ui.graphics.Color): androidx.compose.ui.graphics.Color =
        if (background.luminance() > 0.5f) {
            androidx.compose.ui.graphics.Color.Black
        } else {
            androidx.compose.ui.graphics.Color.White
        }

    private val decorationMutex = Mutex()

    private var onToggleInterface: (() -> Unit)? = null
    private var onShowNoteDialog: ((Locator) -> Unit)? = null
    private var onShowReplyDialog: ((ReaderViewModel.NoteData) -> Unit)? = null

    private fun showPublication(
        publication: Publication,
        colorScheme: androidx.compose.material3.ColorScheme? = null,
        initialLocator: org.readium.r2.shared.publication.Locator? = null
    ) {
        if (!viewModel.hasEverLoaded) {
            viewModel.setBookReady(false)
        }
        val container = view?.findViewById<View>(R.id.reader_container)
        if (container == null) return

        val theme = when (viewModel.theme.value) {
            "dark" -> Theme.DARK
            "sepia" -> Theme.SEPIA
            "light" -> Theme.LIGHT
            else -> if (colorScheme?.background?.toArgb() == Color.BLACK ||
                (colorScheme?.background?.toArgb() ?: 0) < 0xFF444444.toInt()) {
                Theme.DARK
            } else {
                Theme.LIGHT
            }
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
            // Hand the navigator the annotations before any chapter has loaded, so it draws
            // them itself as each chapter loads.
            applyAnnotations(viewModel.highlights.value, viewModel.notes.value)
        }

        transaction.commit()

        // Safety net: never leave the loader up if the navigator fails to report a page.
        viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.delay(READY_TIMEOUT_MS)
            if (!viewModel.isBookReady.value) {
                Log.w("ReaderFragment", "Ready-state timeout reached, forcing ready")
                viewModel.setBookReady(true)
            }
        }
    }

    private fun showNoteInputDialog(locator: Locator) {
        onShowNoteDialog?.invoke(locator)
    }

    private fun applyAnnotations(highlights: List<ReaderViewModel.HighlightData>, notes: List<ReaderViewModel.NoteData>) {
        val navigator = childFragmentManager.findFragmentByTag("navigator") as? DecorableNavigator ?: return
        Log.d("ReaderFragment", "Applying ${highlights.size} highlights and ${notes.size} notes to navigator")

        val highlightDecorations = highlights.map { data ->
            Decoration(
                id = "highlight-${data.id}",
                locator = data.locator,
                style = Decoration.Style.Highlight(tint = data.color, isActive = false),
                extras = mapOf("userId" to data.userId, "type" to "highlight")
            )
        }

        val noteDecorations = notes.map { data ->
            Decoration(
                id = "note-${data.id}",
                locator = data.locator,
                style = Decoration.Style.Highlight(tint = data.color, isActive = true),
                extras = mapOf("userId" to data.userId, "type" to "note", "content" to data.content)
            )
        }

        // Readium diffs each list against the previous one and the resulting changes must reach
        // the page in order, so updates are applied strictly one at a time.
        lifecycleScope.launch {
            decorationMutex.withLock {
                try {
                    navigator.applyDecorations(highlightDecorations + noteDecorations, "highlights")
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e("ReaderFragment", "Failed to apply annotations", e)
                }
            }
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

    private var pageTurnLayout: PageTurnLayout? = null

    /** Whether pages turn with the paper-curl effect instead of the reader's plain slide. */
    private var pageCurlEnabled: Boolean
        get() = requireContext()
            .getSharedPreferences(READER_PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(PREF_PAGE_CURL, true)
        set(value) {
            requireContext()
                .getSharedPreferences(READER_PREFS, android.content.Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_PAGE_CURL, value)
                .apply()
        }

    private fun turnPage(navigator: EpubNavigatorFragment, forward: Boolean) {
        val layout = pageTurnLayout
        when {
            layout != null && layout.curlEnabled -> layout.turn(forward)
            forward -> navigator.goForward(animated = true)
            else -> navigator.goBackward(animated = true)
        }
    }

    override fun onDestroyView() {
        pageTurnLayout = null
        super.onDestroyView()
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
                turnPage(navigator, forward = false)
                true
            }
            point.x > width * 0.7 -> {
                turnPage(navigator, forward = true)
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

        // Readium only reports a page once the current resource is fully laid out and has been
        // scrolled to the initial locator, so the first report means the page is settled.
        if (!viewModel.isBookReady.value) {
            viewModel.setBookReady(true)
        }
    }

    override fun onPageLoaded() {
        super.onPageLoaded()

        // Re-apply annotations whenever a resource is loaded to ensure they are visible
        applyAnnotations(viewModel.highlights.value, viewModel.notes.value)
    }

    override fun onExternalLinkActivated(url: AbsoluteUrl) {
        // Handle external links
    }
}
