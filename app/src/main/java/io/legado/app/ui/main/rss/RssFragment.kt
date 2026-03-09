package io.legado.app.ui.main.rss

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.data.entities.RssSource
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.rss.article.RssSortActivity
import io.legado.app.ui.rss.favorites.RssFavoritesActivity
import io.legado.app.ui.rss.read.ReadRssActivity
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.ui.rss.source.manage.RssSourceActivity
import io.legado.app.ui.rss.subscription.RuleSubActivity
import io.legado.app.ui.theme.AppTheme
import io.legado.app.utils.openUrl
import io.legado.app.utils.startActivity

/**
 * 订阅界面
 */
class RssFragment() : VMBaseFragment<RssViewModel>(R.layout.fragment_rss),
    MainFragmentInterface {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    override val position: Int? get() = arguments?.getInt("position")

    override val viewModel by viewModels<RssViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                AppTheme {
                    RssScreen(
                        viewModel = viewModel,
                        onOpenRss = { openRss(it) },
                        onEdit = { edit(it) },
                        onOpenStar = { startActivity<RssFavoritesActivity>() },
                        onOpenConfig = { startActivity<RssSourceActivity>() },
                        onOpenRuleSub = { startActivity<RuleSubActivity>() },
                        onDelete = { del(it) },
                        onLogin = { login(it) }
                    )
                }
            }
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        // Compose handles UI
    }

    private fun openRss(rssSource: RssSource) {
        if (rssSource.singleUrl) {
            viewModel.getSingleUrl(rssSource) { url ->
                if (url.startsWith("http", true)) {
                    startActivity<ReadRssActivity> {
                        putExtra("title", rssSource.sourceName)
                        putExtra("origin", url)
                    }
                } else {
                    context?.openUrl(url)
                }
            }
        } else {
            startActivity<RssSortActivity> {
                putExtra("url", rssSource.sourceUrl)
            }
        }
    }

    private fun edit(rssSource: RssSource) {
        startActivity<RssSourceEditActivity> {
            putExtra("sourceUrl", rssSource.sourceUrl)
        }
    }

    private fun login(rssSource: RssSource) {
        startActivity<SourceLoginActivity> {
            putExtra("type", "rssSource")
            putExtra("key", rssSource.sourceUrl)
        }
    }

    private fun del(rssSource: RssSource) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + rssSource.sourceName)
            noButton()
            yesButton {
                viewModel.del(rssSource)
            }
        }
    }
}
