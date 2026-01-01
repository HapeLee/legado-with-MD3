package io.legado.app.ui.book.searchContent

import android.os.Bundle
import androidx.compose.runtime.Composable
import io.legado.app.ui.theme.AppTheme
import io.legado.app.base.BaseComposeActivity

class SearchContentActivity : BaseComposeActivity() {

    private var bookUrl: String? = null
    private var searchWord: String? = null
    private var searchResultIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookUrl = intent.getStringExtra("bookUrl")
        searchWord = intent.getStringExtra("searchWord")
        searchResultIndex = intent.getIntExtra("searchResultIndex", 0)
    }

    @Composable
    override fun Content() {
        AppTheme {
            bookUrl?.let {
                SearchContentScreen(
                    bookUrl = it,
                    searchWord = searchWord,
                    onBack = { finish() }
                )
            }
        }
    }

}
