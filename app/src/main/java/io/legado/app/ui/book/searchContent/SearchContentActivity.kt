package io.legado.app.ui.book.searchContent

import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.theme.AppTheme

class SearchContentActivity : BaseComposeActivity() {
    @Composable
    override fun Content() {

        AppTheme {
            SearchContentScreen(
                onBack = { finish() }
            )
        }
    }
}
