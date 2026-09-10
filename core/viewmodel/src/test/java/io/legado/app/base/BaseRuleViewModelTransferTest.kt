package io.legado.app.base

import android.app.Application
import android.net.Uri
import io.legado.app.core.rules.RuleTransferPlatform
import io.legado.app.data.repository.UploadRepository
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.importComponents.ImportStatus
import io.legado.app.ui.widget.components.list.ListUiState
import io.legado.app.ui.widget.components.list.SelectableItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `BaseRuleViewModel` 导入/导出/上传路径的行为基线。
 *
 * 这些用例在「平台能力抽成 [RuleTransferPlatform]」之前先立起来，用来证明抽取前后行为一致：
 * 分类结果（New/Update/Existing）、默认勾选、空选择提示、写入内容、上传入参与事件文案。
 *
 * 断言一律用 **状态/事件等待**（`first { ... }` + `withTimeout`）而不是调度器推进：
 * `BaseRuleViewModel` 内部硬编码了 `Dispatchers.IO` / `Dispatchers.Main`，推进虚拟时钟不可靠。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BaseRuleViewModelTransferTest {

    @Before
    fun setUp() {
        // viewModelScope 需要 Main；Robolectric 环境里默认没有。
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---------- 导入 ----------

    @Test
    fun `导入时按标题变化把条目分为 新增 更新 已有 并默认只勾选非已有`() = runBlocking {
        val transfer = FakeRuleTransferPlatform()
        val viewModel = newViewModel(
            stored = listOf(TestRule("a", "a"), TestRule("b", "旧标题")),
            transfer = transfer,
        )

        viewModel.importSource("a,b,c")

        val state = awaitImportResult(viewModel) as BaseImportUiState.Success
        val byId = state.items.associateBy { it.data.id }
        assertEquals(ImportStatus.Existing, byId.getValue("a").status)
        assertEquals(ImportStatus.Update, byId.getValue("b").status)
        assertEquals(ImportStatus.New, byId.getValue("c").status)
        assertEquals(
            listOf(false, true, true),
            state.items.map { it.isSelected },
        )
    }

    @Test
    fun `导入前把文本 trim 后再交给平台读取`() = runBlocking {
        val transfer = FakeRuleTransferPlatform()
        val viewModel = newViewModel(transfer = transfer)

        viewModel.importSource("  a,b  ")

        awaitImportResult(viewModel)
        assertEquals(listOf("a,b"), transfer.readCalls)
    }

    @Test
    fun `解析失败时进入 Error 状态且保留原因`() = runBlocking {
        val viewModel = newViewModel(transfer = FakeRuleTransferPlatform())

        viewModel.importSource("bad")

        val state = awaitImportResult(viewModel)
        assertTrue(state is BaseImportUiState.Error)
        assertEquals("无法解析", (state as BaseImportUiState.Error).msg)
    }

    // ---------- 导出 ----------

    @Test
    fun `导出时未选中任何规则只提示 不写文件`() = runBlocking {
        val transfer = FakeRuleTransferPlatform()
        val viewModel = newViewModel(stored = listOf(TestRule("a", "a")), transfer = transfer)

        val event = async { viewModel.events.first() }
        viewModel.exportToUri(EXPORT_URI, viewModel.items(), emptySet())

        assertEquals("没有选中的规则可导出", (withTimeout(5_000) { event.await() } as BaseRuleEvent.ShowSnackbar).message)
        assertTrue(transfer.writes.isEmpty())
    }

    @Test
    fun `导出时把选中规则序列化后写入目标 URI`() = runBlocking {
        val transfer = FakeRuleTransferPlatform()
        val viewModel = newViewModel(
            stored = listOf(TestRule("a", "a"), TestRule("b", "b")),
            transfer = transfer,
        )

        val event = async { viewModel.events.first() }
        viewModel.exportToUri(EXPORT_URI, viewModel.items(), setOf("b"))

        assertEquals("导出成功", (withTimeout(5_000) { event.await() } as BaseRuleEvent.ShowSnackbar).message)
        assertEquals(listOf(EXPORT_URI.toString() to "[b]"), transfer.writes)
    }

    @Test
    fun `导出写入失败时上报失败原因`() = runBlocking {
        val transfer = FakeRuleTransferPlatform(writeError = IllegalStateException("磁盘满"))
        val viewModel = newViewModel(stored = listOf(TestRule("a", "a")), transfer = transfer)

        val event = async { viewModel.events.first() }
        viewModel.exportToUri(EXPORT_URI, viewModel.items(), setOf("a"))

        assertEquals(
            "导出失败: 磁盘满",
            (withTimeout(5_000) { event.await() } as BaseRuleEvent.ShowSnackbar).message,
        )
    }

    // ---------- 上传 ----------

    @Test
    fun `上传时未选中任何规则不调用上传`() = runBlocking {
        val uploader = FakeUploadRepository()
        val viewModel = newViewModel(stored = listOf(TestRule("a", "a")), uploader = uploader)

        viewModel.uploadSelectedRules(emptySet(), viewModel.items())

        Thread.sleep(200)
        assertTrue(uploader.calls.isEmpty())
    }

    @Test
    fun `上传成功后事件带链接与复制动作`() = runBlocking {
        val uploader = FakeUploadRepository(result = "https://example.com/rules.json")
        val viewModel = newViewModel(stored = listOf(TestRule("a", "a")), uploader = uploader)

        val event = async { viewModel.events.first() }
        viewModel.uploadSelectedRules(setOf("a"), viewModel.items())

        val snackbar = withTimeout(5_000) { event.await() } as BaseRuleEvent.ShowSnackbar
        assertEquals("https://example.com/rules.json", snackbar.url)
        assertEquals("复制链接", snackbar.actionLabel)
        assertEquals("上传成功: https://example.com/rules.json", snackbar.message)
        assertEquals(listOf("export_rules.json"), uploader.calls)
    }

    // ---------- 夹具 ----------

    private fun newViewModel(
        stored: List<TestRule> = emptyList(),
        transfer: RuleTransferPlatform = FakeRuleTransferPlatform(),
        uploader: UploadRepository = FakeUploadRepository(),
    ) = TestRuleViewModel(
        RuntimeEnvironment.getApplication(),
        transfer,
        uploader,
        stored.toMutableList(),
    )

    private suspend fun awaitImportResult(
        viewModel: TestRuleViewModel,
    ): BaseImportUiState<TestRule> = withTimeout(5_000) {
        viewModel.importState.first {
            it is BaseImportUiState.Success || it is BaseImportUiState.Error
        }
    }

    private data class TestRule(val id: String, val title: String)

    private data class TestItem(override val id: String, val rule: TestRule) : SelectableItem<String>

    private data class TestUiState(
        override val items: List<TestItem> = emptyList(),
        override val selectedIds: Set<Any> = emptySet(),
        override val searchKey: String = "",
        override val isSearch: Boolean = false,
        override val isLoading: Boolean = false,
    ) : ListUiState<TestItem>

    private class FakeRuleTransferPlatform(
        private val writeError: Throwable? = null,
    ) : RuleTransferPlatform {
        val readCalls = mutableListOf<String>()
        val writes = mutableListOf<Pair<String, String>>()

        override suspend fun readImportSource(text: String): String {
            readCalls += text
            return text
        }

        override suspend fun writeExport(targetUri: String, content: String) {
            writeError?.let { throw it }
            writes += targetUri to content
        }
    }

    private class FakeUploadRepository(
        private val result: String = "https://example.com/rules.json",
    ) : UploadRepository {
        val calls = mutableListOf<String>()

        override suspend fun upload(fileName: String, file: Any, contentType: String): String {
            calls += fileName
            return result
        }
    }

    /**
     * 玩具实现：JSON 是 `[id,id,...]` 的简化形式，标题取自 id 本身。
     * 只用来驱动 `BaseRuleViewModel` 的编排逻辑，不测真实 Gson 行为。
     */
    private class TestRuleViewModel(
        application: Application,
        transfer: RuleTransferPlatform,
        uploader: UploadRepository,
        private val stored: MutableList<TestRule>,
    ) : BaseRuleViewModel<TestItem, TestRule, String, TestUiState>(
        application,
        TestUiState(),
        uploader,
        transfer,
    ) {
        override val rawDataFlow: Flow<List<TestRule>> = flowOf(stored.toList())

        fun items(): List<TestItem> = stored.map { TestItem(it.id, it) }

        override fun composeUiState(
            items: List<TestItem>,
            selectedIds: Set<String>,
            isSearch: Boolean,
            isUploading: Boolean,
            importState: BaseImportUiState<TestRule>,
        ) = TestUiState(items, selectedIds, _searchKey.value, isSearch, false)

        override fun TestRule.toUiItem() = TestItem(id, this)

        override suspend fun generateJson(entities: List<TestRule>): String =
            entities.joinToString(prefix = "[", postfix = "]") { it.id }

        override fun parseImportRules(text: String): List<TestRule> {
            if (text == "bad") throw IllegalArgumentException("无法解析")
            return text.split(",").filter { it.isNotBlank() }.map { TestRule(it, it) }
        }

        override fun hasChanged(newRule: TestRule, oldRule: TestRule): Boolean =
            newRule.title != oldRule.title

        override suspend fun findOldRule(newRule: TestRule): TestRule? =
            stored.find { it.id == newRule.id }

        override fun saveImportedRules() = Unit

        override fun ruleItemToEntity(item: TestItem): TestRule = item.rule
    }

    private companion object {
        val EXPORT_URI: Uri = Uri.parse("content://test/export_rules.json")
    }
}
