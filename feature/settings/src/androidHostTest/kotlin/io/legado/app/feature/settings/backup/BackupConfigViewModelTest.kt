package io.legado.app.feature.settings.backup

import android.app.Application
import android.os.Looper
import io.legado.app.domain.gateway.BackupRestoreGateway
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.domain.gateway.WebDavBackupGateway
import io.legado.app.domain.model.WebDavBackup
import io.legado.app.domain.model.settings.BackupSettings
import io.legado.app.domain.usecase.BackupRestoreUseCase
import io.legado.app.domain.usecase.WebDavBackupUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * `BackupConfigViewModel` 的行为基线（M5-9a 新增；迁移前零测试）。
 *
 * ⚠️ **本文件只钉同步可观测的行为**。VM 里凡 `viewModelScope.launch(Dispatchers.IO)` 之后再
 * `withContext(Dispatchers.Main)` 的尾巴（`performBackup` / `restoreLocal` / `testWebDav` 的
 * 结果与终态），在 Robolectric 下要跨真实线程池 + 主 looper 才能观测，测试会变成靠
 * `idle()` 轮询的脆弱写法 ⇒ 这里只断言**进入 IO 之前**就已完成的那一步
 * （Loading 对话框、权限 Effect），结果分支留给真机冒烟。这一条限制本身就是本片的
 * "未验证"清单里的一项。
 *
 * 用例重心是本片的实质改动 —— 四组「忽略集」从 `:app` 全局换成 [BackupIgnoreStore] 契约后：
 *  - [BackupIgnoreKind] 的**配对**（keys/titles/isIgnored 是否来自同一组）；
 *  - 四个 `saveXxx` 里那处**不对称**（两个写两组并关弹层，两个只写一组且**不关**弹层）
 *    —— 合并它们会静默改掉弹层关闭时机，是这片最容易被"顺手统一"掉的地方。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BackupConfigViewModelTest {

    @Test
    fun `init从store按组读取且配对正确`() {
        val store = FakeIgnoreStore(
            keyTitles = mapOf(
                BackupIgnoreKind.RestoreConfig to listOf("readConfig" to "阅读界面"),
                BackupIgnoreKind.BackupConfig to listOf("themeMode" to "主题"),
                BackupIgnoreKind.RestoreDb to listOf("bookmark" to "书签"),
                BackupIgnoreKind.BackupDb to listOf("server" to "服务"),
            ),
            ignored = mapOf(
                (BackupIgnoreKind.RestoreConfig to "readConfig") to true,
                (BackupIgnoreKind.BackupDb to "server") to false,
            ),
        )
        val viewModel = createViewModel(store)
        idle()

        val state = viewModel.uiState.value
        assertEquals(listOf("readConfig"), state.ignoreItems.map { it.key })
        assertEquals(listOf("阅读界面"), state.ignoreItems.map { it.title })
        assertEquals("该组里被标忽略的要读出来", true, state.ignoreItems.single().checked)

        assertEquals("换一组必须换一批 key/title，不能串组", listOf("bookmark"), state.dbIgnoreItems.map { it.key })
        assertEquals(listOf("书签"), state.dbIgnoreItems.map { it.title })

        assertEquals(listOf("themeMode"), state.backupIgnoreItems.map { it.key })
        assertEquals(listOf("server"), state.backupDbIgnoreItems.map { it.key })
        assertEquals("未记录过的 key 默认不忽略", false, state.backupDbIgnoreItems.single().checked)
    }

    @Test
    fun `saveIgnoreItems写两组并关弹层`() {
        val store = FakeIgnoreStore(oneKeyEachKind())
        val viewModel = createViewModel(store)
        idle()
        viewModel.onIntent(BackupConfigIntent.OpenIgnoreDialog)

        viewModel.onIntent(BackupConfigIntent.SaveIgnoreItems)
        idle()

        assertEquals(
            "写的是「恢复」的两组（配置项 + 数据库表）",
            listOf(BackupIgnoreKind.RestoreConfig, BackupIgnoreKind.RestoreDb),
            store.savedKinds,
        )
        assertEquals(
            listOf(BackupIgnoreKind.RestoreConfig, BackupIgnoreKind.RestoreDb),
            store.setKinds(),
        )
        assertEquals("这条路径会关弹层", null, viewModel.uiState.value.activeSheet)
    }

    @Test
    fun `saveDbIgnoreItems只写一组且不关弹层`() {
        val store = FakeIgnoreStore(oneKeyEachKind())
        val viewModel = createViewModel(store)
        idle()
        viewModel.onIntent(BackupConfigIntent.OpenIgnoreDialog)

        viewModel.onIntent(BackupConfigIntent.SaveDbIgnoreItems)
        idle()

        assertEquals(listOf(BackupIgnoreKind.RestoreDb), store.savedKinds)
        assertEquals(listOf(BackupIgnoreKind.RestoreDb), store.setKinds())
        assertEquals(
            "⚠️ 这条**不关**弹层 —— 迁移前就没有 `copy(activeSheet = null)`，别顺手统一",
            BackupConfigSheet.IgnoreRestoreItems,
            viewModel.uiState.value.activeSheet,
        )
    }

    @Test
    fun `saveBackupIgnoreItems写两组并关弹层`() {
        val store = FakeIgnoreStore(oneKeyEachKind())
        val viewModel = createViewModel(store)
        idle()
        viewModel.onIntent(BackupConfigIntent.OpenBackupIgnoreDialog)

        viewModel.onIntent(BackupConfigIntent.SaveBackupIgnoreItems)
        idle()

        assertEquals(
            listOf(BackupIgnoreKind.BackupConfig, BackupIgnoreKind.BackupDb),
            store.savedKinds,
        )
        assertEquals(null, viewModel.uiState.value.activeSheet)
    }

    @Test
    fun `saveBackupDbIgnoreItems只写一组且不关弹层`() {
        val store = FakeIgnoreStore(oneKeyEachKind())
        val viewModel = createViewModel(store)
        idle()
        viewModel.onIntent(BackupConfigIntent.OpenBackupIgnoreDialog)

        viewModel.onIntent(BackupConfigIntent.SaveBackupDbIgnoreItems)
        idle()

        assertEquals(listOf(BackupIgnoreKind.BackupDb), store.savedKinds)
        assertEquals(
            "同样不关弹层",
            BackupConfigSheet.IgnoreBackupItems,
            viewModel.uiState.value.activeSheet,
        )
    }

    @Test
    fun `勾选只改内存状态不碰store`() {
        val store = FakeIgnoreStore(oneKeyEachKind())
        val viewModel = createViewModel(store)
        idle()

        viewModel.onIntent(BackupConfigIntent.ToggleIgnoreItem("readConfig", true))
        idle()

        assertEquals("只有点保存才落 store", emptyList<BackupIgnoreKind>(), store.savedKinds)
        assertTrue("但 UI 状态要立刻跟上", viewModel.uiState.value.ignoreItems.single().checked)
    }

    @Test
    fun `普通路径备份要先申请存储权限`() {
        val store = FakeIgnoreStore(oneKeyEachKind())
        val viewModel = createViewModel(store, backupPath = "/sdcard/backup")
        idle()
        val effects = collect(viewModel)

        viewModel.onIntent(BackupConfigIntent.RequestBackup("both"))
        idle()

        assertEquals(
            "非 content:// 的本地路径要先走权限申请（宿主用 PermissionsCompat 处理这个 Effect）",
            listOf(BackupConfigEffect.RequestStoragePermission("/sdcard/backup", "both")),
            effects.toList(),
        )
        assertEquals(
            "权限还没给，不该已经出现「正在备份」对话框",
            null,
            viewModel.uiState.value.activeDialog,
        )
    }

    @Test
    fun `content路径备份跳过权限申请直接进对话框`() {
        val store = FakeIgnoreStore(oneKeyEachKind())
        val viewModel = createViewModel(store, backupPath = "content://tree/primary")
        idle()
        val effects = collect(viewModel)

        viewModel.onIntent(BackupConfigIntent.RequestBackup("both"))
        idle()

        assertTrue(
            "content:// 路径（已持久授权的 SAF 目录）不需要存储权限",
            effects.none { it is BackupConfigEffect.RequestStoragePermission },
        )
        assertEquals(
            "直接进「正在备份」对话框，枚举映射要正确",
            BackupConfigDialog.Loading(BackupConfigText.BackingUp),
            viewModel.uiState.value.activeDialog,
        )
    }

    @Test
    fun `请求本地恢复先清弹层再发选文件effect`() {
        val viewModel = createViewModel(FakeIgnoreStore(oneKeyEachKind()))
        idle()
        viewModel.onIntent(BackupConfigIntent.OpenSheet(BackupConfigSheet.RestoreOptions))
        val effects = collect(viewModel)

        viewModel.onIntent(BackupConfigIntent.RequestLocalRestore)
        idle()

        assertEquals(listOf(BackupConfigEffect.LaunchRestoreFilePicker), effects.toList())
        assertEquals("弹层要关掉", null, viewModel.uiState.value.activeSheet)
        assertEquals(
            "⚠️ `restoreLocal` 那条路径**不**清 activeSheet（只设 activeDialog），这里别弄反",
            null,
            viewModel.uiState.value.activeDialog,
        )
    }

    @Test
    fun `恢复网络备份与测试连接都先进对应对话框`() {
        val viewModel = createViewModel(FakeIgnoreStore(oneKeyEachKind()))
        idle()

        viewModel.onIntent(BackupConfigIntent.TestWebDav)
        idle()
        assertEquals(
            BackupConfigDialog.Loading(BackupConfigText.TestSyncLoading),
            viewModel.uiState.value.activeDialog,
        )

        viewModel.onIntent(BackupConfigIntent.DismissDialog)
        viewModel.onIntent(BackupConfigIntent.RequestNetworkRestore)
        idle()
        assertEquals(
            BackupConfigDialog.Loading(BackupConfigText.Loading),
            viewModel.uiState.value.activeDialog,
        )
        assertEquals("弹层要关掉", null, viewModel.uiState.value.activeSheet)
    }

    private fun createViewModel(
        store: BackupIgnoreStore,
        backupPath: String? = null,
    ) = BackupConfigViewModel(
        settingsGateway = FakeBackupSettingsGateway(BackupSettings(backupPath = backupPath)),
        webDavBackupUseCase = WebDavBackupUseCase(FakeWebDavGateway()),
        backupRestoreUseCase = BackupRestoreUseCase(FakeBackupRestoreGateway()),
        ignoreStore = store,
    )

    private fun collect(viewModel: BackupConfigViewModel): MutableList<BackupConfigEffect> {
        val out = mutableListOf<BackupConfigEffect>()
        CoroutineScope(Dispatchers.Unconfined).launch {
            viewModel.effects.collect { out += it }
        }
        return out
    }

    private fun idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun oneKeyEachKind() = mapOf(
        BackupIgnoreKind.RestoreConfig to listOf("readConfig" to "阅读界面"),
        BackupIgnoreKind.BackupConfig to listOf("themeMode" to "主题"),
        BackupIgnoreKind.RestoreDb to listOf("bookmark" to "书签"),
        BackupIgnoreKind.BackupDb to listOf("server" to "服务"),
    )
}

private class FakeIgnoreStore(
    keyTitles: Map<BackupIgnoreKind, List<Pair<String, String>>>,
    ignored: Map<Pair<BackupIgnoreKind, String>, Boolean> = emptyMap(),
) : BackupIgnoreStore {

    private val entries = keyTitles
    private val state = ignored.toMutableMap()
    val saveCalls = mutableListOf<BackupIgnoreKind>()
    private val writes = mutableListOf<Triple<BackupIgnoreKind, String, Boolean>>()

    val savedKinds: List<BackupIgnoreKind> get() = saveCalls

    fun setKinds(): List<BackupIgnoreKind> = writes.map { it.first }

    override fun keys(kind: BackupIgnoreKind): List<String> =
        entries[kind].orEmpty().map { it.first }

    override fun titles(kind: BackupIgnoreKind): List<String> =
        entries[kind].orEmpty().map { it.second }

    override fun isIgnored(kind: BackupIgnoreKind, key: String): Boolean =
        state[kind to key] ?: false

    override fun setIgnored(kind: BackupIgnoreKind, key: String, ignored: Boolean) {
        state[kind to key] = ignored
        writes += Triple(kind, key, ignored)
    }

    override fun save(kind: BackupIgnoreKind) {
        saveCalls += kind
    }
}

private class FakeBackupSettingsGateway(
    initial: BackupSettings = BackupSettings(),
) : BackupSettingsGateway {
    private val state = MutableStateFlow(initial)
    override val currentSettings: BackupSettings get() = state.value
    override val settings: Flow<BackupSettings> = state
    override suspend fun update(transform: (BackupSettings) -> BackupSettings) {
        state.value = transform(state.value)
    }
}

private class FakeBackupRestoreGateway : BackupRestoreGateway {
    override suspend fun backup(path: String?, mode: String) = Unit
    override suspend fun restoreLocal(uri: String) = Unit
}

private class FakeWebDavGateway : WebDavBackupGateway {
    override val isJianGuoYun: Boolean = false
    override suspend fun syncConfig() = Unit
    override suspend fun test(): Boolean = true
    override suspend fun backup() = Unit
    override suspend fun getBackupNames(): List<String> = emptyList()
    override suspend fun getLatestBackup(): WebDavBackup? = null
    override suspend fun restore(name: String) = Unit
}
