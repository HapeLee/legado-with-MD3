package io.legado.app.feature.tagrules

import android.app.Application
import android.net.Uri
import androidx.room.Room
import io.legado.app.core.rules.RuleTransferEvent
import io.legado.app.core.rules.RuleTransferPlatform
import io.legado.app.core.platform.Clipboard
import io.legado.app.core.platform.JsonCodec
import io.legado.app.core.platform.Toaster
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.HighlightTagRule
import io.legado.app.data.entities.TagGroupRule
import io.legado.app.data.repository.HighlightTagRuleRepository
import io.legado.app.data.repository.TagGroupRuleRepository
import io.legado.app.data.repository.UploadRepository
import io.legado.app.domain.gateway.BookGroupMutationGateway
import io.legado.app.domain.model.BookGroupUpdate
import io.legado.app.domain.model.NewBookGroup
import io.legado.app.domain.model.TagGroupRuleUpdate
import io.legado.app.feature.tagrules.group.TagGroupRuleIntent as GroupIntent
import io.legado.app.feature.tagrules.group.TagGroupRuleViewModel
import io.legado.app.feature.tagrules.highlight.HighlightTagRuleIntent as HighlightIntent
import io.legado.app.feature.tagrules.highlight.HighlightTagRuleViewModel
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.importComponents.ImportStatus
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
 * `tagrules` 两个 ViewModel 的导入/导出/落库行为基线（M1-1）。
 *
 * 目的：M1-3 要消除 Provider/base 依赖并把 Feature 转成 CMP，届时这些语义必须逐字不变——
 * 导入分类（New/Update/Existing）、默认勾选、导出内容与失败文案、落库范围、
 * 搜索过滤与 `order` 排序、`hasChanged` 的字段集。
 *
 * 手段：**真实 GSON + 内存 Room**，不 mock 序列化与 SQL；平台 IO 走 [FakeTransferPlatform]，
 * 因为「规则文本怎么取到」（URL / URI / 纯文本）是平台能力，不是本 Feature 的语义。
 *
 * 断言一律用状态/事件等待（`first {}` + `withTimeout`），不推进虚拟时钟：
 * `RuleTransferUseCase` 内部硬编码 `Dispatchers.IO` / `Dispatchers.Main`。
 *
 * M1-3b：两个 VM 已不再继承 `BaseRuleViewModel`，构造参数去掉 `Application`——
 * 本文件因此也是「VM 层已无 Context 依赖」的回归证据：夹具里的 Repository / Gateway /
 * Platform / Clipboard 全部是纯 JVM 对象，没有一个需要 Android 上下文去构造。
 *
 * 注意：`uiState` 是 `stateIn(WhileSubscribed(5000))`，没有订阅者时 `uiState.value` 停留在初始值；
 * 而 `ExportSelection` / `UploadSelection` 恰恰读的是 `uiState.value`。所以凡是要用到列表与选择的
 * 用例都必须先起一个持续收集者（[collectUiState]），这与真实 UI 的 `collectAsStateWithLifecycle`
 * 行为一致。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class TagRulesImportExportCharacterizationTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    // ---------- 导入：分类与解析 ----------

    @Test
    fun `导入分组规则时按 groupName 与 pattern 变化分为 新增 更新 已有`() = runBlocking {
        val repository = TagGroupRuleRepository(db)
        repository.insert(
            TagGroupRule(id = 1, pattern = "a", groupName = "A"),
            TagGroupRule(id = 2, pattern = "b", groupName = "旧分组"),
        )
        val viewModel = newGroupViewModel(repository)
        val json = GSON.toJson(
            listOf(
                TagGroupRule(id = 1, pattern = "a", groupName = "A"),        // 完全相同
                TagGroupRule(id = 2, pattern = "b", groupName = "新分组"),    // 分组名变了
                TagGroupRule(id = 3, pattern = "c", groupName = "C"),        // 新规则
            )
        )

        viewModel.onIntent(GroupIntent.ImportSource(json))

        val state = awaitImportResult(viewModel) as BaseImportUiState.Success
        val byId = state.items.associateBy { it.data.id }
        assertEquals(ImportStatus.Existing, byId.getValue(1L).status)
        assertEquals(ImportStatus.Update, byId.getValue(2L).status)
        assertEquals(ImportStatus.New, byId.getValue(3L).status)
        assertEquals(listOf(false, true, true), state.items.map { it.isSelected })
    }

    @Test
    fun `导入分组规则时只改 order 不算变化`() = runBlocking {
        val repository = TagGroupRuleRepository(db)
        repository.insert(TagGroupRule(id = 1, pattern = "a", groupName = "A", order = 0))
        val viewModel = newGroupViewModel(repository)

        viewModel.onIntent(
            GroupIntent.ImportSource(
                GSON.toJson(TagGroupRule(id = 1, pattern = "a", groupName = "A", order = 9))
            )
        )

        val state = awaitImportResult(viewModel) as BaseImportUiState.Success
        assertEquals(ImportStatus.Existing, state.items.single().status)
        assertEquals(false, state.items.single().isSelected)
    }

    @Test
    fun `导入单条对象时得到一条规则`() = runBlocking {
        val viewModel = newGroupViewModel(TagGroupRuleRepository(db))

        viewModel.onIntent(
            GroupIntent.ImportSource(GSON.toJson(TagGroupRule(id = 7, pattern = "x", groupName = "X")))
        )

        val state = awaitImportResult(viewModel) as BaseImportUiState.Success
        assertEquals(1, state.items.size)
        assertEquals(ImportStatus.New, state.items.single().status)
    }

    @Test
    fun `导入非法文本时进入 Error 并保留原因`() = runBlocking {
        val viewModel = newGroupViewModel(TagGroupRuleRepository(db))

        viewModel.onIntent(GroupIntent.ImportSource("not json"))

        val state = awaitImportResult(viewModel)
        assertTrue(state is BaseImportUiState.Error)
        assertEquals("格式不正确", (state as BaseImportUiState.Error).msg)
    }

    @Test
    fun `导入文本先 trim 再交给平台读取`() = runBlocking {
        val transfer = FakeTransferPlatform()
        val viewModel = newGroupViewModel(TagGroupRuleRepository(db), transfer = transfer)

        viewModel.onIntent(GroupIntent.ImportSource("  []  "))

        awaitImportResult(viewModel)
        assertEquals(listOf("[]"), transfer.readCalls)
    }

    // ---------- 导出 ----------

    @Test
    fun `导出未选中任何规则时只提示不写入`() = runBlocking {
        val transfer = FakeTransferPlatform()
        val viewModel = newGroupViewModel(TagGroupRuleRepository(db), transfer = transfer)

        val event = async { viewModel.events.first() }
        viewModel.onIntent(GroupIntent.ExportSelection(EXPORT_URI.toString()))

        assertEquals(
            "没有选中的规则可导出",
            (withTimeout(5_000) { event.await() } as RuleTransferEvent.ShowSnackbar).message,
        )
        assertTrue(transfer.writes.isEmpty())
    }

    @Test
    fun `导出选中规则时写入等价 JSON 并提示成功`() = runBlocking {
        val transfer = FakeTransferPlatform()
        val repository = TagGroupRuleRepository(db)
        val viewModel = newGroupViewModel(repository, transfer = transfer)
        val collector = collectUiState(viewModel)
        try {
            repository.insert(
                TagGroupRule(id = 1, pattern = "a", groupName = "A"),
                TagGroupRule(id = 2, pattern = "b", groupName = "B"),
            )
            withTimeout(5_000) { viewModel.uiState.first { it.items.size == 2 } }
            viewModel.onIntent(GroupIntent.SetSelection(setOf(2L)))
            withTimeout(5_000) { viewModel.uiState.first { it.selectedIds == setOf(2L) } }

            val event = async { viewModel.events.first() }
            viewModel.onIntent(GroupIntent.ExportSelection(EXPORT_URI.toString()))

            assertEquals(
                "导出成功",
                (withTimeout(5_000) { event.await() } as RuleTransferEvent.ShowSnackbar).message,
            )
            val written = awaitWrite(transfer)
            // 只导出选中项，且能被同一套 Gson 解析回等价实体
            assertEquals(
                listOf(TagGroupRule(id = 2, pattern = "b", groupName = "B")),
                GSON.fromJsonArray<TagGroupRule>(written).getOrThrow(),
            )
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun `导出写入失败时上报原因`() = runBlocking {
        val transfer = FakeTransferPlatform(writeError = IllegalStateException("磁盘满"))
        val repository = TagGroupRuleRepository(db)
        val viewModel = newGroupViewModel(repository, transfer = transfer)
        val collector = collectUiState(viewModel)
        try {
            repository.insert(TagGroupRule(id = 1, pattern = "a", groupName = "A"))
            withTimeout(5_000) { viewModel.uiState.first { it.items.size == 1 } }
            viewModel.onIntent(GroupIntent.SetSelection(setOf(1L)))
            withTimeout(5_000) { viewModel.uiState.first { it.selectedIds == setOf(1L) } }

            val event = async { viewModel.events.first() }
            viewModel.onIntent(GroupIntent.ExportSelection(EXPORT_URI.toString()))

            assertEquals(
                "导出失败: 磁盘满",
                (withTimeout(5_000) { event.await() } as RuleTransferEvent.ShowSnackbar).message,
            )
        } finally {
            collector.cancel()
        }
    }

    // ---------- 落库 ----------

    @Test
    fun `保存导入时只落勾选项 并在分组侧应用到所有书`() = runBlocking {
        val gateway = FakeBookGroupMutationGateway()
        val repository = TagGroupRuleRepository(db)
        val viewModel = newGroupViewModel(repository, gateway = gateway)

        viewModel.onIntent(
            GroupIntent.ImportSource(
                GSON.toJson(
                    listOf(
                        TagGroupRule(id = 1, pattern = "a", groupName = "A"),
                        TagGroupRule(id = 2, pattern = "b", groupName = "B"),
                    )
                )
            )
        )
        awaitImportResult(viewModel)
        viewModel.onIntent(GroupIntent.ToggleImportAll(false))
        viewModel.onIntent(GroupIntent.ToggleImportSelection(0))
        viewModel.onIntent(GroupIntent.SaveImportedRules)

        assertEquals(
            listOf(1L),
            withTimeout(5_000) { repository.flowAll().first { it.isNotEmpty() } }.map { it.id },
        )
        assertTrue(gateway.applied)
        assertEquals(
            BaseImportUiState.Idle,
            withTimeout(5_000) { viewModel.importState.first { it is BaseImportUiState.Idle } },
        )
    }

    // ---------- reducer：过滤 / 排序 / 展示名 ----------

    @Test
    fun `列表按 order 升序且搜索命中 groupName 与 pattern`() = runBlocking {
        val repository = TagGroupRuleRepository(db)
        repository.insert(
            TagGroupRule(id = 1, pattern = "alpha", groupName = "分组一", order = 2),
            TagGroupRule(id = 2, pattern = "beta", groupName = "分组二", order = 1),
            TagGroupRule(id = 3, pattern = "gamma", groupName = "其他", order = 0),
        )
        val viewModel = newGroupViewModel(repository)

        val all = withTimeout(5_000) { viewModel.uiState.first { it.items.size == 3 } }
        assertEquals(listOf(3L, 2L, 1L), all.items.map { it.id })

        viewModel.onIntent(GroupIntent.UpdateSearchQuery("分组"))
        val filtered = withTimeout(5_000) { viewModel.uiState.first { it.items.size == 2 } }
        assertEquals(listOf(2L, 1L), filtered.items.map { it.id })

        viewModel.onIntent(GroupIntent.UpdateSearchQuery("beta"))
        val byPattern = withTimeout(5_000) { viewModel.uiState.first { it.items.size == 1 } }
        assertEquals(2L, byPattern.items.single().id)
    }

    @Test
    fun `分组名为空时展示名回退到 pattern`() = runBlocking {
        val repository = TagGroupRuleRepository(db)
        repository.insert(TagGroupRule(id = 1, pattern = "p1", groupName = ""))
        val viewModel = newGroupViewModel(repository)

        val state = withTimeout(5_000) { viewModel.uiState.first { it.items.isNotEmpty() } }
        assertEquals("p1", state.items.single().displayName)
    }

    // ---------- highlight 侧差异 ----------

    @Test
    fun `高亮规则的 hasChanged 包含 enabled 字段`() = runBlocking {
        val repository = HighlightTagRuleRepository(db)
        repository.insert(HighlightTagRule(id = 1, title = "标题", pattern = "p", enabled = true))
        val viewModel = HighlightTagRuleViewModel(
            FakeUploadRepository(),
            FakeTransferPlatform(),
            FakeClipboard(),
            repository,
        )

        viewModel.onIntent(
            HighlightIntent.ImportSource(
                GSON.toJson(
                    listOf(
                        HighlightTagRule(id = 1, title = "标题", pattern = "p", enabled = false),
                        HighlightTagRule(id = 2, title = "T2", pattern = "p2", enabled = true),
                    )
                )
            )
        )

        val state = withTimeout(5_000) {
            viewModel.importState.first {
                it is BaseImportUiState.Success || it is BaseImportUiState.Error
            }
        } as BaseImportUiState.Success
        val byId = state.items.associateBy { it.data.id }
        // 只改 enabled 也算更新——这是 highlight 与 group 的差异（group 只看名称与 pattern）
        assertEquals(ImportStatus.Update, byId.getValue(1L).status)
        assertEquals(ImportStatus.New, byId.getValue(2L).status)
    }

    // ---------- M1-3：平台能力改构造注入 ----------

    /**
     * 本用例在**没有安装任何全局 Provider** 的 JVM 里跑：只要实现里还有
     * `ClipboardProvider.current` / `ToasterProvider.current`，这里就会因
     * `IllegalStateException` 直接失败。剪贴板内容与提示文案由夹具观察。
     */
    @Test
    fun `复制与粘贴走注入的剪贴板与提示器 不依赖全局 Provider`() {
        val clipboard = FakeClipboard()
        val toaster = FakeToaster()
        val viewModel = newGroupViewModel(
            TagGroupRuleRepository(db), clipboard = clipboard, toaster = toaster,
        )
        val rule = TagGroupRule(id = 3, pattern = "p3", groupName = "G3")

        viewModel.copyRule(rule)
        // 写进剪贴板的 JSON 能被同一套 Gson 解析回等价实体（复制的是单个对象，不是数组）
        assertEquals(
            rule,
            GSON.fromJsonObject<TagGroupRule>(clipboard.content).getOrThrow(),
        )

        // 剪贴板有内容 → 粘贴成功
        assertEquals(rule, viewModel.pasteRule())

        clipboard.content = "   "
        assertEquals(null, viewModel.pasteRule())
        clipboard.content = "not json"
        assertEquals(null, viewModel.pasteRule())
        assertEquals(listOf("剪贴板没有内容", "格式不对"), toaster.messages)
    }

    /**
     * 导出文件是用户可见产物，格式漂移会让老文件读不回来。`JsonCodec` 的实现在
     * android/desktop 上都委托与 `INITIAL_GSON` 同配置的 Gson 实例，这里对两个
     * 规则实体锁字节级等值。
     */
    @Test
    fun `JsonCodec 与 GSON 对规则实体的序列化输出一致`() {
        val groupRules = listOf(
            TagGroupRule(id = 1, pattern = "p", groupName = "G", order = 2),
            TagGroupRule(id = 2, pattern = "q", groupName = ""),
        )
        assertEquals(GSON.toJson(groupRules), JsonCodec.toJson(groupRules))

        val highlightRules = listOf(
            HighlightTagRule(id = 5, title = "标题", pattern = "p", enabled = true),
        )
        assertEquals(GSON.toJson(highlightRules), JsonCodec.toJson(highlightRules))
    }

    // ---------- 夹具 ----------

    private fun newGroupViewModel(
        repository: TagGroupRuleRepository,
        transfer: RuleTransferPlatform = FakeTransferPlatform(),
        gateway: BookGroupMutationGateway = FakeBookGroupMutationGateway(),
        clipboard: Clipboard = FakeClipboard(),
        toaster: Toaster = FakeToaster(),
    ) = TagGroupRuleViewModel(
        FakeUploadRepository(),
        transfer,
        clipboard,
        toaster,
        gateway,
        repository,
    )

    /** 持续订阅 `uiState`，让 `uiState.value` 反映最新状态（真实 UI 的等价行为）。 */
    private fun CoroutineScope.collectUiState(viewModel: TagGroupRuleViewModel): Job =
        launch(Dispatchers.Unconfined) { viewModel.uiState.collect { } }

    private suspend fun awaitImportResult(
        viewModel: TagGroupRuleViewModel,
    ): BaseImportUiState<TagGroupRule> = withTimeout(5_000) {
        viewModel.importState.first {
            it is BaseImportUiState.Success || it is BaseImportUiState.Error
        }
    }

    private suspend fun awaitWrite(transfer: FakeTransferPlatform): String = withTimeout(5_000) {
        while (transfer.writes.isEmpty()) {
            delay(10)
        }
        transfer.writes.single().second
    }

    private class FakeTransferPlatform(
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

    private class FakeUploadRepository : UploadRepository {
        override suspend fun upload(fileName: String, file: Any, contentType: String): String =
            "https://example.com/$fileName"
    }

    private class FakeClipboard(var content: String? = null) : Clipboard {
        override fun getText(): String? = content

        override fun setText(text: String) {
            content = text
        }
    }

    private class FakeToaster : Toaster {
        val messages = mutableListOf<String>()

        override fun toast(message: String) {
            messages += message
        }

        override fun longToast(message: String) {
            messages += message
        }
    }

    /** 只记录 `applyTagGroupRulesToAllBooks`——其余方法是分组管理页的契约，本 Feature 不用。 */
    private class FakeBookGroupMutationGateway : BookGroupMutationGateway {
        var applied = false

        override suspend fun addGroup(group: NewBookGroup) = Unit

        override suspend fun saveGroup(
            bookGroup: BookGroupUpdate,
            ruleToSave: TagGroupRuleUpdate?,
            ruleIdToDelete: Long?,
        ) = Unit

        override suspend fun saveTagGroupRule(rule: TagGroupRuleUpdate) = Unit

        override suspend fun deleteTagGroupRule(ruleId: Long) = Unit

        override suspend fun deleteGroup(groupId: Long) = Unit

        override suspend fun applyTagGroupRulesToAllBooks() {
            applied = true
        }
    }

    private companion object {
        val EXPORT_URI: Uri = Uri.parse("content://test/export_rules.json")
    }
}
