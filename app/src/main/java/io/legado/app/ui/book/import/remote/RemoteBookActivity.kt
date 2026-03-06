package io.legado.app.ui.book.import.remote

import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.theme.AppTheme
import io.legado.app.utils.startActivityForBook

/**
 * 展示远程书籍
 */
class RemoteBookActivity : BaseComposeActivity() {
    @Composable
    override fun Content() {
        AppTheme {
            RemoteBookScreen(
                onBackClick = { finish() },
                startReadBook = { book ->
                    startActivityForBook(book)
                },
                onArchiveFileClick = { },
                selectBookFolder = { }
            )
        }
    }

}
