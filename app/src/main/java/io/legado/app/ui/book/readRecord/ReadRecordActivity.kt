package io.legado.app.ui.book.readRecord

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import io.legado.app.base.BaseComposeActivity
import io.legado.app.data.appDb
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.theme.AppTheme
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TimelineItem(
    val session: ReadRecordSession,
    val showHeader: Boolean
)

class ReadRecordActivity : BaseComposeActivity() {

    @Composable
    override fun Content() {
        AppTheme {
            ReadRecordScreen(
                onBackClick = { finish() },
                onBookClick = { bookName, bookAuthor ->
                    lifecycleScope.launch {
                        val book = withContext(Dispatchers.IO) {
                            appDb.bookDao.getBook(bookName, bookAuthor)
                        }
                        if (book != null) startActivityForBook(book)
                        else {
                            startActivity<SearchActivity> {
                                putExtra("key", bookName)
                            }
                        }
                    }
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

}