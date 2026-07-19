package io.legado.app.help.storage

import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookRestorePlannerTest {

    @Test
    fun `same normalized local file is restored into existing record`() {
        val existing = localBook("/storage/emulated/0/Books/book.txt", progress = 1)
        val restored = localBook("/sdcard/Books/book.txt", progress = 8)

        val plan = plan(
            restoredBooks = listOf(restored),
            existingBooks = listOf(existing),
            existingLocations = setOf(existing.bookUrl, restored.bookUrl),
            normalizedLocations = mapOf(
                existing.bookUrl to "/storage/emulated/0/Books/book.txt",
                restored.bookUrl to "/storage/emulated/0/Books/book.txt",
            ),
        )

        assertEquals(listOf(existing.bookUrl), plan.booksToUpsert.map { it.bookUrl })
        assertEquals(8, plan.booksToUpsert.single().durChapterIndex)
        assertTrue(plan.booksToDelete.isEmpty())
    }

    @Test
    fun `invalid backup path is rebound to sole valid matching local book`() {
        val existing = localBook("/current/Books/book.txt", progress = 1)
        val restored = localBook("/old-device/Books/book.txt", progress = 8)

        val plan = plan(
            restoredBooks = listOf(restored),
            existingBooks = listOf(existing),
            existingLocations = setOf(existing.bookUrl),
        )

        assertEquals(listOf(existing.bookUrl), plan.booksToUpsert.map { it.bookUrl })
        assertEquals(8, plan.booksToUpsert.single().durChapterIndex)
        assertTrue(plan.booksToDelete.isEmpty())
    }

    @Test
    fun `existing stale duplicate is removed when backup resolves to current file`() {
        val stale = localBook("/old-device/Books/book.txt", progress = 1)
        val current = localBook("/current/Books/book.txt", progress = 2)
        val restored = localBook(stale.bookUrl, progress = 8)

        val plan = plan(
            restoredBooks = listOf(restored),
            existingBooks = listOf(stale, current),
            existingLocations = setOf(current.bookUrl),
        )

        assertEquals(listOf(current.bookUrl), plan.booksToUpsert.map { it.bookUrl })
        assertEquals(listOf(stale.bookUrl), plan.booksToDelete.map { it.bookUrl })
    }

    @Test
    fun `duplicate paths inside backup collapse to the sole valid file`() {
        val stale = localBook("/old-device/Books/book.txt", progress = 1)
        val current = localBook("/current/Books/book.txt", progress = 8)

        val plan = plan(
            restoredBooks = listOf(stale, current),
            existingBooks = emptyList(),
            existingLocations = setOf(current.bookUrl),
        )

        assertEquals(listOf(current.bookUrl), plan.booksToInsert.map { it.bookUrl })
        assertTrue(plan.booksToUpdate.isEmpty())
        assertTrue(plan.booksToDelete.isEmpty())
    }

    @Test
    fun `two valid files with same metadata remain separate books`() {
        val existing = localBook("/Books/edition-a/book.txt", progress = 1)
        val restored = localBook("/Books/edition-b/book.txt", progress = 8)

        val plan = plan(
            restoredBooks = listOf(restored),
            existingBooks = listOf(existing),
            existingLocations = setOf(existing.bookUrl, restored.bookUrl),
        )

        assertEquals(listOf(restored.bookUrl), plan.booksToUpsert.map { it.bookUrl })
        assertTrue(plan.booksToDelete.isEmpty())
    }

    @Test
    fun `online books are still matched only by book url`() {
        val existing = Book(bookUrl = "https://source-a/book", name = "斗破苍穹", author = "天蚕土豆")
        val restored = Book(bookUrl = "https://source-b/book", name = "斗破苍穹", author = "天蚕土豆")

        val plan = planBookRestore(
            restoredBooks = listOf(restored),
            existingBooks = listOf(existing),
            ignoreLocalBook = false,
            locationStatus = { LocalBookLocationStatus.Missing },
        )

        assertEquals(listOf(restored.bookUrl), plan.booksToUpsert.map { it.bookUrl })
        assertTrue(plan.booksToDelete.isEmpty())
    }

    @Test
    fun `ignored local books do not overwrite existing records`() {
        val existing = localBook("/current/Books/book.txt", progress = 1)
        val restored = localBook("/old-device/Books/book.txt", progress = 8)

        val plan = planBookRestore(
            restoredBooks = listOf(restored),
            existingBooks = listOf(existing),
            ignoreLocalBook = true,
            locationStatus = { LocalBookLocationStatus.Available },
        )

        assertTrue(plan.booksToUpsert.isEmpty())
        assertTrue(plan.booksToDelete.isEmpty())
    }

    @Test
    fun `temporarily unreadable content location is never rebound or deleted`() {
        val offline = localBook("content://cloud/books/book.txt", progress = 1)
        val available = localBook("content://local/books/book.txt", progress = 2)
        val restored = localBook(offline.bookUrl, progress = 8)

        val plan = planBookRestore(
            restoredBooks = listOf(restored),
            existingBooks = listOf(offline, available),
            ignoreLocalBook = false,
            locationStatus = {
                when (it) {
                    available.bookUrl -> LocalBookLocationStatus.Available
                    else -> LocalBookLocationStatus.Unknown
                }
            },
            normalizeLocation = { it },
        )

        assertEquals(listOf(offline.bookUrl), plan.booksToUpdate.map { it.bookUrl })
        assertTrue(plan.booksToDelete.isEmpty())
    }

    private fun plan(
        restoredBooks: List<Book>,
        existingBooks: List<Book>,
        existingLocations: Set<String>,
        normalizedLocations: Map<String, String> = emptyMap(),
    ): BookRestorePlan {
        return planBookRestore(
            restoredBooks = restoredBooks,
            existingBooks = existingBooks,
            ignoreLocalBook = false,
            locationStatus = {
                if (it in existingLocations) {
                    LocalBookLocationStatus.Available
                } else {
                    LocalBookLocationStatus.Missing
                }
            },
            normalizeLocation = { normalizedLocations[it] ?: it },
        )
    }

    private fun localBook(
        bookUrl: String,
        progress: Int,
    ) = Book(
        bookUrl = bookUrl,
        origin = BookType.localTag,
        originName = "book.txt",
        name = "斗破苍穹",
        author = "天蚕土豆",
        type = BookType.text or BookType.local,
        durChapterIndex = progress,
    )
}
