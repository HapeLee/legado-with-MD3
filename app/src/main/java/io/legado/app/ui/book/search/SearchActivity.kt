package io.legado.app.ui.book.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.startActivity
import org.koin.androidx.viewmodel.ext.android.viewModel

class SearchActivity : BaseComposeActivity() {

    private val viewModel by viewModel<SearchViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dispatchInit(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchInit(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onIntent(SearchIntent.ResumeEngine)
    }

    override fun onPause() {
        viewModel.onIntent(SearchIntent.PauseEngine)
        super.onPause()
    }

    @Composable
    override fun Content() {
        SearchScreen(
            viewModel = viewModel,
            onBack = { finish() },
            onOpenBookInfo = { name, author, bookUrl ->
                startActivity(
                    MainActivity.createBookInfoIntent(
                        context = this,
                        name = name,
                        author = author,
                        bookUrl = bookUrl
                    )
                )
            },
            onOpenSourceManage = {
                startActivity<BookSourceActivity>()
            }
        )
    }

    private fun dispatchInit(intent: Intent?) {
        viewModel.onIntent(
            SearchIntent.Initialize(
                key = intent?.getStringExtra("key"),
                scopeRaw = intent?.getStringExtra("searchScope"),
            )
        )
    }

    companion object {
        fun start(context: Context, key: String?, searchScope: String? = null) {
            context.startActivity<SearchActivity> {
                putExtra("key", key)
                putExtra("searchScope", searchScope)
            }
        }
    }
}
