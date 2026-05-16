package com.indeavour.coreader.screen

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.util.Log
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.indeavour.coreader.viewmodel.ReaderViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.graphics.PointF
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl

@OptIn(ExperimentalReadiumApi::class)
class ReaderFragment : Fragment(), EpubNavigatorFragment.Listener, InputListener {

    private lateinit var viewModel: ReaderViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity(), ReaderViewModel.Factory)[ReaderViewModel::class.java]

        childFragmentManager.addFragmentOnAttachListener { _, fragment ->
            if (fragment is EpubNavigatorFragment) {
                fragment.addInputListener(this)
            }
        }
    }

    private var containerId: Int = -1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = FrameLayout(requireContext())
        root.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        
        val fragmentContainer = FrameLayout(requireContext()).apply {
            id = View.generateViewId()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(fragmentContainer)
        containerId = fragmentContainer.id
        
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("ReaderFragment", "onViewCreated")

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.publication.collectLatest { publication ->
                    Log.d("ReaderFragment", "Publication collected: $publication")
                    if (publication != null) {
                        showPublication(publication)
                    }
                }
            }
        }
    }

    private fun showPublication(publication: Publication) {
        Log.d("ReaderFragment", "showPublication")
        // We use the EpubNavigatorFactory provided by Readium to create the navigator fragment factory.
        val factory = EpubNavigatorFactory(publication).createFragmentFactory(initialLocator = null, listener = this)
        
        // Set the custom FragmentFactory for this FragmentManager
        childFragmentManager.fragmentFactory = factory
        
        childFragmentManager.beginTransaction()
            .replace(containerId, EpubNavigatorFragment::class.java, null, "navigator")
            .commit()
    }

    override fun onTap(event: TapEvent): Boolean {
        val point = event.point
        Log.d("ReaderFragment", "onTap at $point")
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

    override fun onExternalLinkActivated(url: AbsoluteUrl) {
        // Handle external links
    }
}
