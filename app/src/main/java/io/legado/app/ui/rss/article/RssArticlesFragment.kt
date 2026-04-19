package io.legado.app.ui.rss.article

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import io.legado.app.data.entities.RssArticle
import io.legado.app.ui.rss.navigation.RssMainNavContract
import io.legado.app.ui.theme.AppTheme
import io.legado.app.utils.startActivity
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class RssArticlesFragment() : Fragment() {

    constructor(sortName: String, sortUrl: String) : this() {
        arguments = Bundle().apply {
            putString("sortName", sortName)
            putString("sortUrl", sortUrl)
        }
    }

    private val activityViewModel: RssSortViewModel by activityViewModel()
    private val viewModel: RssArticlesViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    Content()
                }
            }
        }
    }

    @Composable
    private fun Content() {
        val sortName = arguments?.getString("sortName").orEmpty()
        val sortUrl = arguments?.getString("sortUrl").orEmpty()

        RssArticlesPage(
            sortName = sortName,
            sortUrl = sortUrl,
            articleStyle = activityViewModel.currentArticleStyle(),
            rssUrl = activityViewModel.url,
            rssSource = activityViewModel.rssSource,
            viewModel = viewModel,
            onRead = ::readRss
        )
    }

    private fun readRss(rssArticle: RssArticle) {
        activityViewModel.read(rssArticle)
        requireContext().startActivity(
            RssMainNavContract.createRssReadIntent(
                context = requireContext(),
                title = rssArticle.title,
                origin = rssArticle.origin,
                link = rssArticle.link
            )
        )
    }
}
