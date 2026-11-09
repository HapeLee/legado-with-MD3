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
    fun `newer backup at a different url preserves existing shelf progress`() {
        val existing = onlineBook("https://source-a/book", durChapterIndex = 120, durChapterTime = 1_000)
        val restored = onlineBook("https://source-b/book", durChapterIndex = 3, durChapterTime = 2_000)

        val plan = planBookRestore(
            restoredBooks = listOf(restored),
            existingBooks = listOf(existing),
            ignoreLocalBook = false,
            locationStatus = { LocalBookLocationStatus.Missing },
        )

        assertTrue(plan.booksToUpsert.isEmpty())
        assertTrue(plan.booksToDelete.isEmpty())
    }

    @Test
    fun `cloud snapshot still carrying local source skips backup-only sibling`() {
        val existing = onlineBook("https://source-a/book")
        val sameAgain = onlineBook("https://source-a/book", durChapterIndex = 9)
        val sibling = onlineBook("https://source-b/book")

        val plan = planBookRestore(
            restoredBooks = listOf(sameAgain, sibling),
            existingBooks = listOf(existing),
            ignoreLocalBook = false,
            locationStatus = { LocalBookLocationStatus.Missing },
        )

        assertEquals(listOf(existing.bookUrl), plan.booksToUpdate.map { it.bookUrl })
        assertEquals(9, plan.booksToUpdate.single().durChapterIndex)
        assertTrue(plan.booksToInsert.isEmpty())
        assertTrue(plan.booksToDelete.isEmpty())
    }

    @Test
    fun `multi-row cloud group without local overlap is not imported`() {
        val existing = onlineBook("https://source-a/book")
        val first = onlineBook("https://source-b/book")
        val second = onlineBook("https://source-c/book")

        val plan = planBookRestore(
            restoredBooks = listOf(first, second),
            existingBooks = listOf(existing),
            ignoreLocalBook = false,
            locationStatus = { LocalBookLocationStatus.Missing },
        )

        assertTrue(plan.booksToUpsert.isEmpty())
        assertTrue(plan.booksToDelete.isEmpty())
    }

    @Test
    fun `multiple existing shelf copies are kept when backup offers a third`() {
        val existingA = onlineBook("https://source-a/book", durChapterTime = 1_000)
        val existingB = onlineBook("https://source-b/book", durChapterTime = 1_000)
        val restored = onlineBook("https://source-c/book", durChapterIndex = 9, durChapterTime = 2_000)

        val plan = planBookRestore(
            restoredBooks = listOf(restored),
            existingBooks = listOf(existingA, existingB),
            ignoreLocalBook = false,
            locationStatus = { LocalBookLocationStatus.Missing },
        )

        assertTrue(plan.booksToUpsert.isEmpty())
        assertTrue(plan.booksToDelete.isEmpty())
    }

    @Test
    fun `blank author disables online grouping`() {
        val existing = onlineBook("https://source-a/book", author = "天蚕土豆")
        val restored = onlineBook("https://source-b/book", author = "")

        val plan = planBookRestore(
            restoredBooks = listOf(restored),
            existingBooks = listOf(existing),
            ignoreLocalBook = false,
            locationStatus = { LocalBookLocationStatus.Missing },
        )

        assertEquals(listOf(restored.bookUrl), plan.booksToInsert.map { it.bookUrl })
        assertTrue(plan.booksToDelete.isEmpty())
    }

    @Test
    fun `different work authors are not grouped`() {
        val existing = onlineBook("https://source-a/book", author = "甲")
        val restored = onlineBook("https://source-b/book", author = "乙")

        val plan = planBookRestore(
            restoredBooks = listOf(restored),
            existingBooks = listOf(existing),
            ignoreLocalBook = false,
            locationStatus = { LocalBookLocationStatus.Missing },
        )

        assertEquals(listOf(restored.bookUrl), plan.booksToInsert.map { it.bookUrl })
        assertTrue(plan.booksToDelete.isEmpty())
    }

    @Test
    fun `text and audio editions of same work are not grouped`() {
        val existing = onlineBook("https://source-a/book", type = BookType.text)
        val restored = onlineBook("https://source-b/book", type = BookType.audio)

        val plan = planBookRestore(
            restoredBooks = listOf(restored),
            existingBooks = listOf(existing),
            ignoreLocalBook = false,
            locationStatus = { LocalBookLocationStatus.Missing },
        )

        assertEquals(listOf(restored.bookUrl), plan.booksToInsert.map { it.bookUrl })
        assertTrue(plan.booksToDelete.isEmpty())
    }

    @Test
    fun `temporary backup rows do not remove an existing shelf entry`() {
        val existing = onlineBook("https://source-a/book")
        val sameUrl = onlineBook(
            existing.bookUrl,
            type = BookType.text or BookType.notShelf,
        )
        val otherUrl = onlineBook(
            "https://source-b/book",
            type = BookType.text or BookType.notShelf,
        )

        val plan = planBookRestore(
            restoredBooks = listOf(sameUrl, otherUrl),
            existingBooks = listOf(existing),
            ignoreLocalBook = false,
            locationStatus = { LocalBookLocationStatus.Missing },
        )

        assertTrue(plan.booksToUpsert.isEmpty())
        assertTrue(plan.booksToDelete.isEmpty())
    }

    @Test
    fun `sole backup shelf copy is imported without deleting other temporary records`() {
        val existing = onlineBook(
            "https://source-a/book",
            type = BookType.text or BookType.notShelf,
            durChapterIndex = 9,
        )
        val restored = onlineBook("https://source-b/book")

        val plan = planBookRestore(
            restoredBooks = listOf(restored),
            existingBooks = listOf(existing),
            ignoreLocalBook = false,
            locationStatus = { LocalBookLocationStatus.Missing },
        )

        assertEquals(listOf(restored.bookUrl), plan.booksToInsert.map { it.bookUrl })
        assertTrue(plan.booksToDelete.isEmpty())
    }

    @Test
    fun `local temporary url does not authorize restoring a second shelf copy`() {
        val existing = onlineBook("https://source-a/book", durChapterTime = 1_000)
        val temporary = onlineBook(
            "https://source-b/book",
            type = BookType.text or BookType.notShelf,
        )
        val restored = onlineBook(temporary.bookUrl, durChapterIndex = 3, durChapterTime = 2_000)

        val plan = plan(
            restoredBooks = listOf(restored),
            existingBooks = listOf(existing, temporary),
            existingLocations = emptySet(),
        )

        assertTrue(plan.booksToUpsert.isEmpty())
        assertTrue(plan.booksToDelete.isEmpty())
    }

    @Test
    fun `sole backup shelf copy can promote the matching temporary url`() {
        val temporary = onlineBook(
            "https://source-a/book",
            type = BookType.text or BookType.notShelf,
        )
        val restored = onlineBook(temporary.bookUrl)

        val plan = plan(
            restoredBooks = listOf(restored),
            existingBooks = listOf(temporary),
            existingLocations = emptySet(),
        )

        assertEquals(restored, plan.booksToUpdate.single())
        assertTrue(plan.booksToInsert.isEmpty())
        assertTrue(plan.booksToDelete.isEmpty())
    }

    @Test
    fun `unused backup row never deletes locally newer progress`() {
        val existing = onlineBook("https://source-a/book", durChapterIndex = 100, durChapterTime = 2_000)
        val restored = onlineBook("https://source-b/book", durChapterTime = 1_000)

        val plan = planBookRestore(
            restoredBooks = listOf(restored),
            existingBooks = listOf(existing),
            ignoreLocalBook = false,
            locationStatus = { LocalBookLocationStatus.Missing },
        )

        assertTrue(plan.booksToInsert.isEmpty())
        assertTrue(plan.booksToUpdate.isEmpty())
        assertTrue(plan.booksToDelete.isEmpty())
    }

    @Test
    fun `used but older backup row does not delete locally newer progress`() {
        val existing = onlineBook("https://source-a/book", durChapterIndex = 100, durChapterTime = 9_000)
        val restored = onlineBook("https://source-b/book", durChapterIndex = 30, durChapterTime = 2_000)

        val plan = planBookRestore(
            restoredBooks = listOf(restored),
            existingBooks = listOf(existing),
            ignoreLocalBook = false,
            locationStatus = { LocalBookLocationStatus.Missing },
        )

        assertTrue(plan.booksToInsert.isEmpty())
        assertTrue(plan.booksToDelete.isEmpty())
    }

    @Test
    fun `backup temp row intersection no longer masks shelved row`() {
        val existing = onlineBook(
            "https://source-a/book",
            type = BookType.text or BookType.notShelf,
            durChapterIndex = 9,
        )
        val tempAgain = onlineBook(
            "https://source-a/book",
            type = BookType.text or BookType.notShelf,
        )
        val shelved = onlineBook("https://source-b/book", durChapterIndex = 3, durChapterTime = 2_000)

        val plan = planBookRestore(
            restoredBooks = listOf(tempAgain, shelved),
            existingBooks = listOf(existing),
            ignoreLocalBook = false,
            locationStatus = { LocalBookLocationStatus.Missing },
        )

        assertEquals(listOf(shelved.bookUrl), plan.booksToInsert.map { it.bookUrl })
        assertTrue(plan.booksToUpdate.isEmpty())
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

    private fun onlineBook(
        bookUrl: String,
        name: String = "斗破苍穹",
        author: String = "天蚕土豆",
        type: Int = BookType.text,
        durChapterIndex: Int = 0,
        durChapterTime: Long = 0L,
    ) = Book(
        bookUrl = bookUrl,
        name = name,
        author = author,
        type = type,
        durChapterIndex = durChapterIndex,
        durChapterTime = durChapterTime,
    )
}
