package io.legado.app.model.localBook

import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LocalBookImportPlannerTest {

    @Test
    fun `same named record whose file is gone is reused instead of inserted`() {
        val existing = localBook(bookUrl = "/old-device/Books/book.txt")

        val reusable = planLocalBookImportReuse(
            existingBook = existing,
            existingFileReadable = false,
            importedFileName = "book.txt",
            importedBookUrl = "/current/Books/book.txt",
        )

        assertSame(existing, reusable)
    }

    @Test
    fun `same named record whose file is still readable stays a separate book`() {
        val existing = localBook(bookUrl = "/Books/edition-a/book.txt")

        val reusable = planLocalBookImportReuse(
            existingBook = existing,
            existingFileReadable = true,
            importedFileName = "book.txt",
            importedBookUrl = "/Books/edition-b/book.txt",
        )

        assertNull(reusable)
    }

    @Test
    fun `record already bound to the imported url needs no reuse`() {
        val existing = localBook(bookUrl = "/Books/book.txt")

        val reusable = planLocalBookImportReuse(
            existingBook = existing,
            existingFileReadable = false,
            importedFileName = "book.txt",
            importedBookUrl = "/Books/book.txt",
        )

        assertNull(reusable)
    }

    @Test
    fun `record with another file name is not reused`() {
        val existing = localBook(bookUrl = "/Books/other.txt", originName = "other.txt")

        val reusable = planLocalBookImportReuse(
            existingBook = existing,
            existingFileReadable = false,
            importedFileName = "book.txt",
            importedBookUrl = "/current/Books/book.txt",
        )

        assertNull(reusable)
    }

    @Test
    fun `archive extracted record is not reused because the archive can re-extract it`() {
        val existing = localBook(
            bookUrl = "/cache/book.txt",
            origin = "${BookType.localTag}::archive.rar",
            type = BookType.text or BookType.local or BookType.archive,
        )

        val reusable = planLocalBookImportReuse(
            existingBook = existing,
            existingFileReadable = false,
            importedFileName = "book.txt",
            importedBookUrl = "/current/Books/book.txt",
        )

        assertNull(reusable)
    }

    @Test
    fun `web dav record is not reused because the remote file can be downloaded again`() {
        val existing = localBook(
            bookUrl = "/Books/book.txt",
            origin = "${BookType.webDavTag}https://dav.example.com/book.txt",
        )

        val reusable = planLocalBookImportReuse(
            existingBook = existing,
            existingFileReadable = false,
            importedFileName = "book.txt",
            importedBookUrl = "/current/Books/book.txt",
        )

        assertNull(reusable)
    }

    @Test
    fun `online book with the same origin name is not reused`() {
        val existing = Book(
            bookUrl = "https://source/book",
            origin = "https://source",
            originName = "book.txt",
            name = "斗破苍穹",
            author = "天蚕土豆",
            type = BookType.text,
        )

        val reusable = planLocalBookImportReuse(
            existingBook = existing,
            existingFileReadable = false,
            importedFileName = "book.txt",
            importedBookUrl = "/current/Books/book.txt",
        )

        assertNull(reusable)
    }

    @Test
    fun `missing record is never reused`() {
        val reusable = planLocalBookImportReuse(
            existingBook = null,
            existingFileReadable = false,
            importedFileName = "book.txt",
            importedBookUrl = "/current/Books/book.txt",
        )

        assertNull(reusable)
    }

    private fun localBook(
        bookUrl: String,
        originName: String = "book.txt",
        origin: String = BookType.localTag,
        type: Int = BookType.text or BookType.local,
    ) = Book(
        bookUrl = bookUrl,
        origin = origin,
        originName = originName,
        name = "斗破苍穹",
        author = "天蚕土豆",
        type = type,
        durChapterIndex = 8,
    )
}
