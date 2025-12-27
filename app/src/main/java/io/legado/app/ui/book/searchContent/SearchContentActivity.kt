package io.legado.app.ui.book.searchContent

import android.os.Bundle
import androidx.compose.runtime.Composable
import io.legado.app.base.AppTheme
import io.legado.app.base.BaseComposeActivity

class SearchContentActivity : BaseComposeActivity() {

    private var bookUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookUrl = intent.getStringExtra("bookUrl")
    }

    @Composable
    override fun Content() {
        AppTheme {
            bookUrl?.let {
                SearchContentScreen(
                    bookUrl = it,
                    onBack = { finish() }
                )
            }
        }
    }

}
