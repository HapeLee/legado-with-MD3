package io.legado.app.ui.book.readRecord

import android.os.Bundle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import io.legado.app.data.appDb
import io.legado.app.utils.startActivityForBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.legado.app.base.BaseComposeActivity
import io.legado.app.data.entities.readRecord.ReadRecordSession
import org.koin.androidx.compose.koinViewModel

data class TimelineItem(
    val session: ReadRecordSession,
    val showHeader: Boolean
)

class ReadRecordActivity : BaseComposeActivity() {

    @Composable
    override fun Content() {
        MaterialTheme {
            val viewModel: ReadRecordViewModel = koinViewModel()
            ReadRecordScreen(
                viewModel = viewModel,
                onBackClick = { finish() },
                onBookClick = { bookName ->
                    lifecycleScope.launch {
                        val book = withContext(Dispatchers.IO) {
                            appDb.bookDao.findByName(bookName).firstOrNull()
                        }
                        if (book != null) startActivityForBook(book)
                    }
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

}