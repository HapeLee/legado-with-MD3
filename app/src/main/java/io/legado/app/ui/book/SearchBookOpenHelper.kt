package io.legado.app.ui.book

import android.content.Context
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.utils.startActivity

/**
 * Helper to open a [SearchBook] from AI tool results.
 * Opens the book info screen when bookUrl/origin are available.
 */
object SearchBookOpenHelper {

    fun open(context: Context, book: SearchBook, asVideo: Boolean = false) {
        if (book.bookUrl.isBlank() || book.origin.isBlank()) return
        context.startActivity<BookInfoActivity> {
            putExtra("bookUrl", book.bookUrl)
            putExtra("name", book.name)
            putExtra("author", book.author)
            putExtra("origin", book.origin)
            putExtra("originName", book.originName)
            if (asVideo) putExtra("target", "video")
        }
    }
}
