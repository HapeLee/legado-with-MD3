package io.legado.app.core.rules

import android.app.Application
import io.legado.app.data.repository.UploadRepository
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.importComponents.ImportStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
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
import org.robolectric.annotation.Config

/**
 * [RuleTransferUseCase] 的编排基线。
 *
 * M1-3b 把导入/导出/上传流程从 `BaseRuleViewModel` 抽到这里，判据是**行为逐字不变**：
 * 分类（New/Update/Existing）、默认勾选、空选择只提示不写、上传参数与事件文案、
 * 落库范围、完成后回 `Idle`。这些用例是抽取的等价性证据，也是新 Feature 复用本类时的契约。
 *
 * 断言一律用状态/事件等待（`first { }` + `withTimeout`），不推进虚拟时钟：本类内部硬编码了
 * `Dispatchers.IO` / `Dispatchers.Main`，与迁移前的实现一致。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class RuleTransferUseCaseTest {

    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        // `saveImportedRules` 会 `withContext(Dispatchers.Main)`；Robolectric 环境里默认没有 Main。
        Dispatchers.setMain(Dispatchers.Unconfined)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---------- 导入 ----------

    @Test
    fun `导入时按 hasChanged 把条目分为 新增 更新 已有 并默认只勾选非已有`() = runBlocking {
        val useCase = newUseCase(stored = listOf(TestRule("a", "a"), TestRule("b", "旧标题")))

        useCase.importSource("a,b,c")

        val state = awaitImportResult(useCase) as BaseImportUiState.Success
        val byId = state.items.associateBy { it.data.id }
        assertEquals(ImportStatus.Existing, byId.getValue("a").status)
        assertEquals(ImportStatus.Update, byId.getValue("b").status)
        assertEquals(ImportStatus.New, byId.getValue("c").status)
        assertEquals(listOf(false, true, true), state.items.map { it.isSelected })
        // oldData 带上库中旧值，供 UI 显示差异
        assertEquals("旧标题", byId.getValue("b").oldData?.title)
    }

    @Test
    fun `导入前把文本 trim 后再交给平台读取`() = runBlocking {
        val transfer = FakePlatform()
        val useCase = newUseCase(transfer = transfer)

        useCase.importSource("  a,b  ")

        awaitImportResult(useCase)
        assertEquals(listOf("a,b"), transfer.readCalls)
    }

    @Test
    fun `解析失败时进入 Error 状态且保留原因`() = runBlocking {
        val useCase = newUseCase()

        useCase.importSource("bad")

        val state = awaitImportResult(useCase)
        assertTrue(state is BaseImportUiState.Error)
        assertEquals("无法解析", (state as BaseImportUiState.Error).msg)
    }

    // ---------- 导入条目的选择状态 ----------

    @Test
    fun `取消导入回到 Idle 全选与单选与改条目都只动导入状态`() = runBlocking {
        val useCase = newUseCase()

        useCase.importSource("a,b,c")
        awaitImportResult(useCase)

        useCase.toggleImportAll(false)
        assertEquals(listOf(false, false, false), currentItems(useCase).map { it.isSelected })

        useCase.toggleImportSelection(1)
        assertEquals(listOf(false, true, false), currentItems(useCase).map { it.isSelected })

        useCase.updateImportItem(2, TestRule("c", "改过的标题"))
        val updated = currentItems(useCase)
        assertEquals("改过的标题", updated[2].data.title)
        // 改条目会推动 version，让 UI 能识别内容变化
        assertEquals(1, (useCase.importState.value as BaseImportUiState.Success).version)

        useCase.cancelImport()
        assertEquals(BaseImportUiState.Idle, useCase.importState.value)
    }

    // ---------- 导出 ----------

    @Test
    fun `导出空列表时只提示 不写文件`() = runBlocking {
        val transfer = FakePlatform()
        val useCase = newUseCase(transfer = transfer)

        val event = async { useCase.events.first() }
        useCase.export(EXPORT_URI, emptyList())

        assertEquals(
            "没有选中的规则可导出",
            (withTimeout(5_000) { event.await() } as RuleTransferEvent.ShowSnackbar).message,
        )
        assertTrue(transfer.writes.isEmpty())
    }

    @Test
    fun `导出时把实体序列化后写入目标`() = runBlocking {
        val transfer = FakePlatform()
        val useCase = newUseCase(transfer = transfer, stored = listOf(TestRule("a", "a")))

        val event = async { useCase.events.first() }
        useCase.export(EXPORT_URI, listOf(TestRule("b", "b")))

        assertEquals(
            "导出成功",
            (withTimeout(5_000) { event.await() } as RuleTransferEvent.ShowSnackbar).message,
        )
        assertEquals(listOf(EXPORT_URI to "[b]"), transfer.writes)
    }

    @Test
    fun `导出写入失败时上报失败原因`() = runBlocking {
        val transfer = FakePlatform(writeError = IllegalStateException("磁盘满"))
        val useCase = newUseCase(transfer = transfer)

        val event = async { useCase.events.first() }
        useCase.export(EXPORT_URI, listOf(TestRule("a", "a")))

        assertEquals(
            "导出失败: 磁盘满",
            (withTimeout(5_000) { event.await() } as RuleTransferEvent.ShowSnackbar).message,
        )
    }

    // ---------- 上传 ----------

    @Test
    fun `上传空列表时不调用上传`() = runBlocking {
        val uploader = FakeUploader()
        val useCase = newUseCase(uploader = uploader)

        useCase.upload(emptyList())

        Thread.sleep(200)
        assertTrue(uploader.calls.isEmpty())
    }

    @Test
    fun `未配置上传能力时静默返回`() = runBlocking {
        val useCase = newUseCase(uploader = null)

        useCase.upload(listOf(TestRule("a", "a")))

        Thread.sleep(200)
        assertEquals(false, useCase.isUploading.value)
    }

    @Test
    fun `上传成功后事件带链接与复制动作`() = runBlocking {
        val uploader = FakeUploader(result = "https://example.com/rules.json")
        val useCase = newUseCase(uploader = uploader)

        val event = async { useCase.events.first() }
        useCase.upload(listOf(TestRule("a", "a")))

        val snackbar = withTimeout(5_000) { event.await() } as RuleTransferEvent.ShowSnackbar
        assertEquals("https://example.com/rules.json", snackbar.url)
        assertEquals("复制链接", snackbar.actionLabel)
        assertEquals("上传成功: https://example.com/rules.json", snackbar.message)
        assertEquals(listOf("export_rules.json"), uploader.calls)
        assertEquals(false, useCase.isUploading.value)
    }

    // ---------- 落库 ----------

    @Test
    fun `保存导入时只把勾选项交给 persist 并回到 Idle`() = runBlocking {
        val persisted = mutableListOf<List<TestRule>>()
        val useCase = newUseCase(persisted = persisted)

        useCase.importSource("a,b")
        awaitImportResult(useCase)
        useCase.toggleImportAll(false)
        useCase.toggleImportSelection(1)
        useCase.saveImportedRules()

        withTimeout(5_000) { useCase.importState.first { it is BaseImportUiState.Idle } }
        assertEquals(listOf(listOf(TestRule("b", "b"))), persisted)
    }

    // ---------- 夹具 ----------

    private fun newUseCase(
        transfer: RuleTransferPlatform = FakePlatform(),
        uploader: UploadRepository? = FakeUploader(),
        persisted: MutableList<List<TestRule>> = mutableListOf(),
        stored: List<TestRule> = emptyList(),
    ) = RuleTransferUseCase(
        scope = scope,
        spec = TestSpec(persisted, stored),
        transferPlatform = transfer,
        uploadRepository = uploader,
    )

    private suspend fun awaitImportResult(
        useCase: RuleTransferUseCase<TestRule>,
    ): BaseImportUiState<TestRule> = withTimeout(5_000) {
        useCase.importState.first {
            it is BaseImportUiState.Success || it is BaseImportUiState.Error
        }
    }

    private fun currentItems(
        useCase: RuleTransferUseCase<TestRule>,
    ) = (useCase.importState.value as BaseImportUiState.Success).items

    private data class TestRule(val id: String, val title: String)

    /**
     * 玩具实体语义：JSON 是 `[id,id,...]` 的简化形式。只用来驱动编排，不测真实序列化
     * ——那属于各 Feature 的 `RuleEntitySpec` 实现。
     */
    private class TestSpec(
        private val persisted: MutableList<List<TestRule>>,
        private val stored: List<TestRule>,
    ) : RuleEntitySpec<TestRule> {

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

        override suspend fun persist(entities: List<TestRule>) {
            persisted += entities
        }
    }

    private class FakePlatform(
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

    private class FakeUploader(
        private val result: String = "https://example.com/rules.json",
    ) : UploadRepository {
        val calls = mutableListOf<String>()

        override suspend fun upload(fileName: String, file: Any, contentType: String): String {
            calls += fileName
            return result
        }
    }

    private companion object {
        const val EXPORT_URI = "content://test/export_rules.json"
    }
}
