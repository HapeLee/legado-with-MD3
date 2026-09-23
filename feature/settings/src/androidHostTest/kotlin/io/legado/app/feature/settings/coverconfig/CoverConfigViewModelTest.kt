package io.legado.app.feature.settings.coverconfig

import android.app.Application
import android.os.Looper
import io.legado.app.domain.gateway.CoverSettingsGateway
import io.legado.app.domain.model.settings.CoverSettings
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
 * `CoverConfigViewModel` 的行为基线（M5-12b 新增；迁移前零测试）。
 *
 * M5-12a 把这个 VM 迁进了共享层，实质改动是**两处平台直连各收成一个窄契约**
 * （`CoverRulePlatform` / `CoverAlbumProvider`）⇒ 用例重心在**契约交互**：
 * 断言「VM 确实调了平台的哪个方法、按什么参数」，而不只是「状态变了」。
 *
 * 几条特意钉死的：
 *
 * - **「与默认规则相同 ⇒ 删除配置」** —— 迁移前是
 *   `if (rule == DefaultData.coverRule) delCoverRule() else saveCoverRule(rule)`。
 *   这条分支很隐蔽：写反了表现为「用户存了一条与默认相同的规则、之后改默认不生效」。
 *   注意它**先**过「字段不能为空」那关，所以测试里必须给它**非空**且与默认相同的值。
 * - **保存前的空字段校验**：`searchUrl` / `coverRule` 任一为空 ⇒ 只提示、**不落盘**。
 * - **`ShowSheet(Rule)` 触发一次读取**：迁移前是 `loadRule()` 读 `BookCover`，
 *   现在读契约 ⇒ 不钉住就可能出现「打开弹层看到的是上次的值」。
 * - **`RestoreDefaultRule` 从平台取默认值**（不再是本地常量）。
 * - 图库流改由 `CoverAlbumProvider` 提供 ⇒ 钉住「列表进状态」与「切换选中走 provider」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CoverConfigViewModelTest {

    @Test
    fun `恢复默认规则从平台取默认值并提示`() {
        val platform = FakeCoverRulePlatform(
            defaultSpec = CoverRuleSpec(true, "https://default", "default-expr"),
        )
        val viewModel = createViewModel(rulePlatform = platform)
        idle()
        val effects = collect(viewModel)

        viewModel.onIntent(CoverConfigIntent.RestoreDefaultRule)
        idle()

        assertEquals(
            CoverRuleUiState(true, "https://default", "default-expr"),
            viewModel.uiState.value.rule,
        )
        assertEquals(
            listOf(CoverConfigEffect.ShowToast(CoverConfigToast.RestoredDefault)),
            effects.toList(),
        )
    }

    @Test
    fun `保存前字段为空只提示不落盘`() {
        val platform = FakeCoverRulePlatform()
        val viewModel = createViewModel(rulePlatform = platform)
        idle()
        val effects = collect(viewModel)

        // 真实流程：先打开规则弹层（平台返回的也是空字段），再直接保存
        viewModel.onIntent(CoverConfigIntent.ShowSheet(CoverConfigSheet.Rule))
        idle()
        viewModel.onIntent(CoverConfigIntent.SaveRule)
        idle()

        assertEquals(
            listOf(CoverConfigEffect.ShowToast(CoverConfigToast.RuleFieldsRequired)),
            effects.toList(),
        )
        assertEquals("空字段不该写盘", 0, platform.saved.size)
        assertEquals("空字段也不该删", 0, platform.deleteCount)
        assertEquals(
            "提示之后弹层不该关（否则用户看不到自己填的表单）",
            CoverConfigSheet.Rule,
            viewModel.uiState.value.activeSheet,
        )
    }

    @Test
    fun `与默认规则相同则删除自定义配置`() {
        val platform = FakeCoverRulePlatform(
            defaultSpec = CoverRuleSpec(true, "https://same", "same-expr"),
        )
        val viewModel = createViewModel(rulePlatform = platform)
        idle()

        // 必须**非空**才会走到比较默认那一步
        viewModel.onIntent(CoverConfigIntent.SetRuleEnabled(true))
        viewModel.onIntent(CoverConfigIntent.SetRuleSearchUrl("https://same"))
        viewModel.onIntent(CoverConfigIntent.SetRuleExpression("same-expr"))
        viewModel.onIntent(CoverConfigIntent.SaveRule)
        idle()

        assertEquals("与默认相同 ⇒ 删掉自定义配置", 1, platform.deleteCount)
        assertEquals("不该同时写入", 0, platform.saved.size)
        assertEquals("保存成功后关弹层", null, viewModel.uiState.value.activeSheet)
    }

    @Test
    fun `与默认规则不同则写入原始值`() {
        val platform = FakeCoverRulePlatform(
            defaultSpec = CoverRuleSpec(false, "", ""),
        )
        val viewModel = createViewModel(rulePlatform = platform)
        idle()

        viewModel.onIntent(CoverConfigIntent.SetRuleEnabled(true))
        viewModel.onIntent(CoverConfigIntent.SetRuleSearchUrl("https://mine"))
        viewModel.onIntent(CoverConfigIntent.SetRuleExpression("my-expr"))
        viewModel.onIntent(CoverConfigIntent.SaveRule)
        idle()

        assertEquals(
            "字段名映射别写错：UI 侧叫 coverRule，契约侧叫 expression",
            listOf(CoverRuleSpec(true, "https://mine", "my-expr")),
            platform.saved,
        )
        assertEquals(0, platform.deleteCount)
        assertEquals(null, viewModel.uiState.value.activeSheet)
    }

    @Test
    fun `打开规则弹层时从平台读一次当前规则`() {
        val platform = FakeCoverRulePlatform(
            currentSpec = CoverRuleSpec(true, "https://saved", "saved-expr"),
        )
        val viewModel = createViewModel(rulePlatform = platform)
        idle()

        viewModel.onIntent(CoverConfigIntent.ShowSheet(CoverConfigSheet.Rule))
        idle()

        assertEquals(1, platform.currentCount)
        assertEquals(
            CoverRuleUiState(true, "https://saved", "saved-expr"),
            viewModel.uiState.value.rule,
        )
        assertEquals(CoverConfigSheet.Rule, viewModel.uiState.value.activeSheet)
    }

    @Test
    fun `打开图库弹层不读规则`() {
        val platform = FakeCoverRulePlatform()
        val viewModel = createViewModel(rulePlatform = platform)
        idle()

        viewModel.onIntent(CoverConfigIntent.ShowSheet(CoverConfigSheet.Album))
        idle()

        assertEquals("只有 Rule 那个弹层需要读规则", 0, platform.currentCount)
        assertEquals(CoverConfigSheet.Album, viewModel.uiState.value.activeSheet)
    }

    @Test
    fun `关弹层`() {
        val viewModel = createViewModel()
        idle()
        viewModel.onIntent(CoverConfigIntent.ShowSheet(CoverConfigSheet.Album))

        viewModel.onIntent(CoverConfigIntent.DismissSheet)

        assertEquals(null, viewModel.uiState.value.activeSheet)
    }

    @Test
    fun `图库列表与选中项从契约流进状态`() {
        val provider = FakeCoverAlbumProvider(
            initial = CoverAlbumSelectionUiState(selectedAlbumId = "album-1"),
        )
        val viewModel = createViewModel(albumProvider = provider)
        idle()

        assertEquals("album-1", viewModel.uiState.value.albumSelection.selectedAlbumId)
    }

    @Test
    fun `切换图库选中走契约`() {
        val provider = FakeCoverAlbumProvider()
        val viewModel = createViewModel(albumProvider = provider)
        idle()

        viewModel.onIntent(CoverConfigIntent.SelectAlbum("album-2"))
        idle()

        assertEquals(listOf("album-2"), provider.selectedIds)
    }

    @Test
    fun `普通设置项写回网关`() {
        val gateway = FakeCoverSettingsGateway()
        val viewModel = createViewModel(gateway = gateway)
        idle()

        viewModel.onIntent(CoverConfigIntent.SetShowShadow(true))
        viewModel.onIntent(CoverConfigIntent.SetShowNameDark(true))
        idle()

        assertTrue(gateway.currentSettings.showShadow)
        assertTrue(gateway.currentSettings.showNameDark)
    }

    private fun createViewModel(
        gateway: FakeCoverSettingsGateway = FakeCoverSettingsGateway(),
        rulePlatform: FakeCoverRulePlatform = FakeCoverRulePlatform(),
        albumProvider: FakeCoverAlbumProvider = FakeCoverAlbumProvider(),
    ) = CoverConfigViewModel(
        coverAlbumProvider = albumProvider,
        settingsGateway = gateway,
        coverRulePlatform = rulePlatform,
    )

    private fun collect(viewModel: CoverConfigViewModel): MutableList<CoverConfigEffect> {
        val out = mutableListOf<CoverConfigEffect>()
        CoroutineScope(Dispatchers.Unconfined).launch {
            viewModel.effects.collect { out += it }
        }
        return out
    }

    private fun idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
}

private class FakeCoverSettingsGateway : CoverSettingsGateway {
    private val state = MutableStateFlow(CoverSettings())
    override val currentSettings: CoverSettings get() = state.value
    override val settings: Flow<CoverSettings> = state
    override suspend fun update(transform: (CoverSettings) -> CoverSettings) {
        state.value = transform(state.value)
    }
}

private class FakeCoverRulePlatform(
    var currentSpec: CoverRuleSpec = CoverRuleSpec(false, "", ""),
    var defaultSpec: CoverRuleSpec = CoverRuleSpec(false, "", ""),
) : CoverRulePlatform {

    var currentCount = 0
    val saved = mutableListOf<CoverRuleSpec>()
    var deleteCount = 0

    override suspend fun current(): CoverRuleSpec {
        currentCount++
        return currentSpec
    }

    override suspend fun default(): CoverRuleSpec = defaultSpec

    override suspend fun save(spec: CoverRuleSpec) {
        saved += spec
    }

    override suspend fun delete() {
        deleteCount++
    }
}

private class FakeCoverAlbumProvider(
    initial: CoverAlbumSelectionUiState = CoverAlbumSelectionUiState(),
) : CoverAlbumProvider {

    private val state = MutableStateFlow(initial)
    override val selection: Flow<CoverAlbumSelectionUiState> = state
    val selectedIds = mutableListOf<String?>()

    override suspend fun selectAlbum(albumId: String?) {
        selectedIds += albumId
    }
}
