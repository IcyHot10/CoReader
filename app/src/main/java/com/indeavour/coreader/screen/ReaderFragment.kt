package com.indeavour.coreader.screen

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.util.Log
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.indeavour.coreader.R
import com.indeavour.coreader.ui.theme.CoReaderTheme
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars

@OptIn(ExperimentalReadiumApi::class)
class ReaderFragment : Fragment(), EpubNavigatorFragment.Listener, InputListener, EpubNavigatorFragment.PaginationListener {

    private lateinit var viewModel: ReaderViewModel
    private var containerId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity(), ReaderViewModel.Factory)[ReaderViewModel::class.java]

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

        val colorScheme = MaterialTheme.colorScheme
        
        // Update Readium configuration when theme changes
        LaunchedEffect(colorScheme, publication) {
            publication?.let {
                showPublication(it, colorScheme)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
        ) {
            // Navigator Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // FragmentContainerView for Readium Navigator
                AndroidViewBinding(
                    factory = { inflater, parent, attachToParent ->
                        val root = FrameLayout(inflater.context).apply {
                            id = View.generateViewId()
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                        containerId = root.id
                        root
                    },
                    update = {
                        // This will be called when showPublication is triggered from onViewCreated
                    }
                )
            }

            // Bottom Progress Bar
            Surface(
                color = colorScheme.surface,
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
                            .background(colorScheme.outlineVariant)
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
                                    color = colorScheme.outline
                                )
                            }
                            Text(
                                text = progress.pageLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurface
                            )
                        }
                        Text(
                            text = progress.percentageLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurface
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    LinearProgressIndicator(
                        progress = { progress.value },
                        modifier = Modifier.fillMaxWidth(),
                        color = colorScheme.primary,
                        trackColor = colorScheme.surfaceVariant,
                    )
                }
            }
        }
    }

    // Helper to embed legacy Android View in Compose
    @Composable
    private fun AndroidViewBinding(
        factory: (LayoutInflater, ViewGroup, Boolean) -> View,
        update: (View) -> Unit
    ) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { context ->
                factory(LayoutInflater.from(context), FrameLayout(context), false)
            },
            update = update,
            modifier = Modifier.fillMaxSize()
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

    private fun showPublication(
        publication: Publication,
        colorScheme: androidx.compose.material3.ColorScheme? = null
    ) {
        if (containerId == -1) return

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
            .replace(containerId, EpubNavigatorFragment::class.java, null, "navigator")
        
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
            else -> false
        }
    }

    override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: Locator) {
        viewModel.updateProgress(pageIndex, totalPages, locator)
    }

    override fun onExternalLinkActivated(url: AbsoluteUrl) {}
}
