package io.legado.app.data.repository

import android.app.Application
import io.legado.app.data.entities.Book
import io.legado.app.domain.model.DictPair
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class DictionaryRepositoryImplTest {

    private lateinit var repository: DictionaryRepositoryImpl
    private lateinit var book: Book

    @Before
    fun setUp() {
        repository = DictionaryRepositoryImpl()
        book = Book(
            bookUrl = "dictionary-test-${UUID.randomUUID()}",
            name = "Dictionary test",
            author = "Codex",
        )
        repository.clearBookDictionary(book)
    }

    @After
    fun tearDown() {
        repository.clearBookDictionary(book)
    }

    @Test
    fun `concurrent incremental merges retain every distinct term`() {
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val futures = (1..24).map { index ->
                executor.submit {
                    start.await()
                    repository.mergeDiscoveredPairs(
                        book,
                        listOf(DictPair("Name$index", "名字$index")),
                    )
                }
            }
            start.countDown()
            futures.forEach { it.get() }

            val dictionary = repository.getBookDictionaries(book)
            assertEquals(24, dictionary.pairs.size)
            assertEquals((1..24).map { "Name$it" }.toSet(), dictionary.pairs.map { it.original }.toSet())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `conflicting rediscovery keeps old value and timestamp`() {
        val first = repository.mergeDiscoveredPairs(book, listOf(DictPair("Jack", "杰克")))
        Thread.sleep(5)
        val second = repository.mergeDiscoveredPairs(book, listOf(DictPair("jack", "杰克逊")))

        assertEquals(first.updatedAt, second.updatedAt)
        assertEquals(listOf(DictPair("Jack", "杰克")), second.pairs)
    }
}
