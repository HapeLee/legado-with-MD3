package io.legado.app.ui.book.explore

import android.os.Bundle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.utils.startActivity

/**
 * 发现列表
 */
class ExploreShowActivity : BaseComposeActivity() {

    private lateinit var screenTitle: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screenTitle = intent.getStringExtra("exploreName") ?: "探索"
    }

    @Composable
    override fun Content() {
        MaterialTheme {
            ExploreShowScreen(
                title = screenTitle,
                intent = intent,
                onBack = { finish() },
                onBookClick = { book ->
                    startActivity<BookInfoActivity> {
                        putExtra("name", book.name)
                        putExtra("author", book.author)
                        putExtra("bookUrl", book.bookUrl)
                    }
                }
            )
        }
    }
}