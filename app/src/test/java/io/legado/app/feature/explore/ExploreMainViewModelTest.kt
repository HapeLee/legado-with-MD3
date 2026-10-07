package io.legado.app.feature.explore

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.repository.ExploreRepository
import io.legado.app.data.repository.SettingsRepository
import io.legado.app.help.config.AppConfigStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ExploreMainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repository = FakeExploreRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        AppConfigStore.init(RuntimeEnvironment.getApplication())
        AppConfigStore.putBoolean(PreferKey.exploreMainContent, false)
        AppConfigStore.putString(PreferKey.exploreMainSourceUrl, "a")
        // AppConfigStore 是进程级单例，逐个测试重置分类记忆避免互相污染。
        AppConfigStore.putString(PreferKey.exploreMainKind, "")
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `source list does not request categories until content mode opens`() = runTest(dispatcher) {
        val model = createModel()
        runCurrent()
        assertTrue(model.uiState.value.ready)
        assertTrue(repository.requests.isEmpty())

        model.onIntent(ExploreMainIntent.ShowContent(true))
        runCurrent()
        assertEquals(listOf("a"), repository.requests)
        repository.responses.getValue("a").complete(listOf(kind("a")))
        runCurrent()
        assertEquals(listOf(kind("a")), model.uiState.value.kinds)

        model.onIntent(ExploreMainIntent.ShowContent(false))
        model.onIntent(ExploreMainIntent.ShowContent(true))
        runCurrent()
        assertEquals(listOf("a"), repository.requests)
    }

    @Test
    fun `switching source clears category owners and rejects late categories`() =
        runTest(dispatcher) {
            val model = createModel()
            runCurrent()
            model.onIntent(ExploreMainIntent.ShowContent(true))
            runCurrent()
            var categoryCleared = false
            model.viewModelStore.put("old-category", object : ViewModel() {
                override fun onCleared() {
                    categoryCleared = true
                }
            })

            model.onIntent(ExploreMainIntent.SelectSource("b"))
            runCurrent()
            assertTrue(categoryCleared)
            assertTrue(model.uiState.value.kindsLoading)
            repository.responses.getValue("b").complete(listOf(kind("b")))
            runCurrent()
            repository.responses.getValue("a").complete(listOf(kind("a")))
            runCurrent()

            assertEquals("b", model.uiState.value.selectedSourceUrl)
            assertEquals(listOf(kind("b")), model.uiState.value.kinds)
            assertFalse(model.uiState.value.kindsLoading)
        }

    @Test
    fun `consecutive source switches keep the latest selection`() = runTest(dispatcher) {
        val model = createModel()
        runCurrent()
        model.onIntent(ExploreMainIntent.SelectSource("b"))
        runCurrent()
        model.onIntent(ExploreMainIntent.SelectSource("a"))
        runCurrent()
        assertEquals("a", model.uiState.value.selectedSourceUrl)
    }

    @Test
    fun `repeated identical source emissions keep category owners`() = runTest(dispatcher) {
        val model = createModel()
        runCurrent()
        model.onIntent(ExploreMainIntent.ShowContent(true))
        runCurrent()
        var categoryCleared = false
        model.viewModelStore.put("category", object : ViewModel() {
            override fun onCleared() {
                categoryCleared = true
            }
        })

        repository.sources.value = repository.sources.value.toList()
        runCurrent()

        assertFalse(categoryCleared)
        assertEquals("a", model.uiState.value.selectedSourceUrl)
    }

    @Test
    fun `selecting a category persists it for the current source`() = runTest(dispatcher) {
        val model = createModel()
        runCurrent()
        model.onIntent(ExploreMainIntent.ShowContent(true))
        runCurrent()

        val categoryKey = exploreKindKey(kind("a"))
        model.onIntent(ExploreMainIntent.SelectKind(categoryKey))
        runCurrent()

        assertEquals(categoryKey, model.uiState.value.selectedKindKey)
        assertEquals(
            categoryKey,
            parseExploreKindSelections(
                AppConfigStore.getString(PreferKey.exploreMainKind).orEmpty()
            )["a"],
        )
    }

    @Test
    fun `saved category is restored per source`() = runTest(dispatcher) {
        AppConfigStore.putString(
            PreferKey.exploreMainKind,
            encodeExploreKindSelections(
                mapOf(
                    "a" to exploreKindKey(kind("a")),
                    "b" to exploreKindKey(kind("b")),
                )
            ),
        )
        val model = createModel()
        runCurrent()
        assertEquals(exploreKindKey(kind("a")), model.uiState.value.selectedKindKey)

        model.onIntent(ExploreMainIntent.SelectSource("b"))
        runCurrent()
        assertEquals(exploreKindKey(kind("b")), model.uiState.value.selectedKindKey)
    }

    private fun createModel() = ExploreMainViewModel(repository, SettingsRepository())
        .also { store.put("main", it) }

    private fun kind(source: String) = ExploreKind(title = source, url = "https://$source/books")

    private class FakeExploreRepository : ExploreRepository {
        val requests = mutableListOf<String>()
        val responses = mapOf(
            "a" to CompletableDeferred<List<ExploreKind>>(),
            "b" to CompletableDeferred<List<ExploreKind>>(),
        )
        val sources = MutableStateFlow(
            listOf(
                BookSourcePart(bookSourceUrl = "a", bookSourceName = "A"),
                BookSourcePart(bookSourceUrl = "b", bookSourceName = "B"),
            )
        )

        override fun getExploreSources(query: String, selectedGroup: String) = sources
        override suspend fun getSourceExploreKinds(sourceUrl: String): List<ExploreKind> {
            requests += sourceUrl
            // Simulate a rule engine operation which cannot stop immediately on cancellation.
            return withContext(NonCancellable) { responses.getValue(sourceUrl).await() }
        }

        override fun getBookshelfItems() = flowOf(emptyList<SearchBook>())
        override fun getExploreGroups() = flowOf(emptyList<String>())
        override suspend fun getBookSource(sourceUrl: String): BookSource? = null
        override suspend fun topSource(bookSource: BookSourcePart) = Unit
        override suspend fun deleteSource(sourceUrl: String) = Unit
    }
}
