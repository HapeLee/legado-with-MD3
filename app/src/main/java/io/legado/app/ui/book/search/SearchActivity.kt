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
        // Only dispatch init on fresh launch, not on config change
        // (ViewModel is retained by Koin and already has correct state).
        if (savedInstanceState == null) {
            dispatchInit(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchInit(intent)
    }

    @Composable
    override fun Content() {
        SearchScreen(
            viewModel = viewModel,
            onBack = { finish() },
            onOpenBookInfo = { name, author, bookUrl, origin, coverPath, _ ->
                startActivity(
                    MainActivity.createBookInfoIntent(
                        context = this,
                        name = name,
                        author = author,
                        bookUrl = bookUrl,
                        origin = origin,
                        coverPath = coverPath
                    )
                )
            },
            onOpenSourceManage = {
                startActivity<BookSourceActivity>()
            },
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
                searchScope?.takeIf { it.isNotBlank() }?.let {
                    putExtra("searchScope", it)
                }
            }
        }
    }
}
