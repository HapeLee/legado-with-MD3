package io.legado.app.ui.book.source.manage

import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.ui.book.source.debug.BookSourceDebugActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.utils.startActivity

/** Legacy entry point retained for callers outside the Compose navigation graph. */
class BookSourceActivity : BaseComposeActivity() {
    @Composable
    override fun Content() {
        BookSourceRouteScreen(
            onBackClick = ::finish,
            onAddSource = { startActivity<BookSourceEditActivity>() },
            onEditSource = { sourceUrl ->
                startActivity<BookSourceEditActivity> {
                    putExtra("sourceUrl", sourceUrl)
                }
            },
            onLoginSource = { sourceUrl ->
                startActivity<SourceLoginActivity> {
                    putExtra("type", "bookSource")
                    putExtra("key", sourceUrl)
                }
            },
            onSearchSource = { sourceUrl ->
                SearchActivity.start(this, null, SearchScope(sourceUrl).toString())
            },
            onDebugSource = { sourceUrl ->
                startActivity<BookSourceDebugActivity> {
                    putExtra("key", sourceUrl)
                }
            },
        )
    }
}
