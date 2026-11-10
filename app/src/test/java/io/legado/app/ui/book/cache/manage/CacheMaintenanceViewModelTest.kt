package io.legado.app.ui.book.cache.manage

import androidx.lifecycle.ViewModelStore
import io.legado.app.domain.gateway.BookCacheMaintenanceGateway
import io.legado.app.domain.model.DeletedChapterCache
import io.legado.app.domain.model.DeletionProgress
import io.legado.app.domain.model.OrphanDeleteRequest
import io.legado.app.domain.model.OrphanedChapterGroup
import io.legado.app.domain.model.RecountOutcome
import io.legado.app.domain.model.WordCountRecountTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 缓存维护的状态机回归：三条最容易静默出错的路径 —— 切 Tab、取消、部分提交。
 *
 * 用可控挂起的 Gateway 而不是真实文件/数据库：这里要验证的是 ViewModel 何时写 UiState，
 * 不是 Repository 的 IO 行为。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CacheMaintenanceViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var gateway: FakeGateway

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        gateway = FakeGateway()
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun model(): CacheMaintenanceViewModel =
        CacheMaintenanceViewModel(gateway).also { store.put("cache-maintenance", it) }

    @Test
    fun `selecting a tab during scan clears busy and discards the stale result`() =
        runTest(dispatcher) {
            gateway.scanGate = CompletableDeferred()
            gateway.orphanGroups = listOf(group("book-a"))
            val vm = model()

            vm.onIntent(CacheMaintenanceIntent.Scan)
            runCurrent()
            assertEquals(CacheMaintenanceBusyKind.Scan, vm.uiState.value.busyKind)

            vm.onIntent(CacheMaintenanceIntent.SelectTab(CacheMaintenanceTab.WordCount))
            // F5：必须同步清 busy，否则新 Tab 头一次点「立即扫描」会被 isBusy 守卫吞掉。
            assertNull(vm.uiState.value.busyKind)
            assertEquals(CacheMaintenanceTab.WordCount, vm.uiState.value.tab)

            // Gateway 恰好在被取消之后返回结果：旧 Tab 的数据不能污染新 Tab。
            gateway.scanGate!!.complete(Unit)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(CacheMaintenanceTab.WordCount, state.tab)
            assertTrue(state.orphanGroups.isEmpty())
            assertFalse(state.hasScanned)
            assertNull(state.busyKind)
        }

    @Test
    fun `cancelling deletion keeps the committed partial result and the untouched book`() =
        runTest(dispatcher) {
            gateway.orphanGroups = listOf(group("book-a"), group("book-b"))
            gateway.deleteGate = CompletableDeferred()
            val vm = model()

            vm.onIntent(CacheMaintenanceIntent.Scan)
            advanceUntilIdle()
            vm.onIntent(CacheMaintenanceIntent.ToggleAllOrphanBooks)
            vm.onIntent(CacheMaintenanceIntent.ConfirmDelete)
            runCurrent()
            // 第一本已删完，第二本卡在 Gateway 里。
            assertEquals(2, gateway.deleteCalls)

            vm.onIntent(CacheMaintenanceIntent.Cancel)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.wasCancelled)
            assertNull(state.busyKind)
            // F2：取消不能把已提交的结果丢掉。
            assertEquals(1, state.lastDeletion?.deletedEntries)
            assertEquals(10L, state.lastDeletion?.freedBytes)
            assertEquals(listOf("book-b"), state.orphanGroups.map { it.bookUrl })
            assertTrue(state.selectedOrphanBookUrls.isEmpty())
        }

    @Test
    fun `confirming leave marks the task cancelled and closes the dialog`() =
        runTest(dispatcher) {
            gateway.scanGate = CompletableDeferred()
            val vm = model()

            vm.onIntent(CacheMaintenanceIntent.Scan)
            runCurrent()
            vm.onIntent(CacheMaintenanceIntent.RequestLeave)
            assertTrue(vm.uiState.value.showLeaveConfirm)

            vm.onIntent(CacheMaintenanceIntent.ConfirmLeave)
            advanceUntilIdle()

            assertFalse(vm.uiState.value.showLeaveConfirm)
            assertTrue(vm.uiState.value.wasCancelled)
            assertNull(vm.uiState.value.busyKind)
            assertFalse(vm.uiState.value.hasScanned)
        }

    @Test
    fun `book with lease-skipped entries stays in the list`() = runTest(dispatcher) {
        gateway.orphanGroups = listOf(group("book-a"), group("book-b"))
        // 第一本有条目被图片租约占用：整本必须留在列表里，不能显示成「已删除」。
        gateway.deleteOutcome = { call ->
            if (call == 1) DeletedChapterCache(1, 10L, skippedEntries = 1)
            else DeletedChapterCache(1, 10L)
        }
        val vm = model()

        vm.onIntent(CacheMaintenanceIntent.Scan)
        advanceUntilIdle()
        vm.onIntent(CacheMaintenanceIntent.ToggleAllOrphanBooks)
        vm.onIntent(CacheMaintenanceIntent.ConfirmDelete)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf("book-a"), state.orphanGroups.map { it.bookUrl })
        assertEquals(1, state.lastDeletion?.skippedEntries)
        assertEquals(2, state.scannedBooks)
    }

    /**
     * 正在下载/阅读的书在执行阶段被跳过：不能像处理成功的书一样从列表移除，
     * 否则用户会看到「勾了、执行了、书消失却没补字数」，且无从重试。
     */
    @Test
    fun `books skipped during recount stay in the list and remain selected`() =
        runTest(dispatcher) {
            gateway.recountTargets = listOf(target("book-a"), target("book-b"))
            gateway.recountHandled = setOf("book-a")
            val vm = model()

            vm.onIntent(CacheMaintenanceIntent.SelectTab(CacheMaintenanceTab.WordCount))
            vm.onIntent(CacheMaintenanceIntent.Scan)
            advanceUntilIdle()
            vm.onIntent(CacheMaintenanceIntent.ToggleAllRecountBooks)
            vm.onIntent(CacheMaintenanceIntent.RecountSelected)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(listOf("book-b"), state.recountTargets.map { it.bookUrl })
            assertEquals(setOf("book-b"), state.selectedRecountBookUrls)
            assertNull(state.busyKind)
        }

    private fun group(bookUrl: String) = OrphanedChapterGroup(
        bookUrl = bookUrl,
        bookName = bookUrl,
        entryNames = listOf("00001-abc"),
        bytes = 10L,
    )

    private fun target(bookUrl: String) = WordCountRecountTarget(
        bookUrl = bookUrl,
        bookName = bookUrl,
        recountableChapters = 1,
        unsolvedChapters = 0,
    )

    private class FakeGateway : BookCacheMaintenanceGateway {
        var orphanGroups: List<OrphanedChapterGroup> = emptyList()
        var recountTargets: List<WordCountRecountTarget> = emptyList()
        var scanGate: CompletableDeferred<Unit>? = null
        var deleteGate: CompletableDeferred<Unit>? = null
        var deleteOutcome: (Int) -> DeletedChapterCache = { DeletedChapterCache(1, 10L) }
        var deleteCalls = 0
        var recountHandled: Set<String>? = null

        override suspend fun scanOrphanedChapters(
            onProgress: (Int, Int) -> Unit,
        ): List<OrphanedChapterGroup> {
            scanGate?.await()
            onProgress(orphanGroups.size, orphanGroups.size)
            return orphanGroups
        }

        override suspend fun scanRecountTargets(
            onProgress: (Int, Int) -> Unit,
        ): List<WordCountRecountTarget> {
            scanGate?.await()
            onProgress(recountTargets.size, recountTargets.size)
            return recountTargets
        }

        override suspend fun deleteOrphanedChapters(
            requests: List<OrphanDeleteRequest>,
            onProgress: (DeletionProgress) -> Unit,
        ): DeletedChapterCache {
            var deletedEntries = 0
            var freedBytes = 0L
            var skippedEntries = 0
            requests.forEachIndexed { index, request ->
                val call = ++deleteCalls
                if (call == 2) deleteGate?.await()
                val result = deleteOutcome(call)
                deletedEntries += result.deletedEntries
                freedBytes += result.freedBytes
                skippedEntries += result.skippedEntries
                val removed = result.skippedEntries == 0 && result.deletedEntries > 0
                onProgress(
                    DeletionProgress(
                        handled = index + 1,
                        total = requests.size,
                        accumulated = DeletedChapterCache(
                            deletedEntries, freedBytes, 0, skippedEntries,
                        ),
                        removedBookUrl = if (removed) request.bookUrl else null,
                    )
                )
            }
            return DeletedChapterCache(deletedEntries, freedBytes, 0, skippedEntries)
        }

        override suspend fun recountWordCounts(
            bookUrls: Set<String>,
            onProgress: (Int, Int) -> Unit,
        ): RecountOutcome = RecountOutcome(
            updatedChapters = bookUrls.size,
            updatedBooks = 1,
            handledBookUrls = recountHandled ?: bookUrls,
        )
    }
}
