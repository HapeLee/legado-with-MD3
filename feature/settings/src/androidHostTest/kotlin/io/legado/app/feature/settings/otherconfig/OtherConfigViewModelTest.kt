package io.legado.app.feature.settings.otherconfig

import android.app.Application
import android.os.Looper
import io.legado.app.core.platform.AppLogStore
import io.legado.app.domain.gateway.AppLocaleGateway
import io.legado.app.domain.gateway.DirectLinkRule
import io.legado.app.domain.gateway.DirectLinkSettingsGateway
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.gateway.LocalPasswordGateway
import io.legado.app.domain.gateway.OtherConfigSystemGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.model.settings.DownloadCacheSettings
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.domain.model.settings.ReadAloudSettings
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
 * `OtherConfigViewModel` 的行为基线。
 *
 * ⚠️ **本文件是两批用例的合并**，不是全新增：迁移前 `:app` 已有一份
 * `app/src/test/.../ui/config/otherConfig/OtherConfigViewModelTest.kt`（5 例），
 * 本片把 VM 迁进 `:feature:settings` 后它编译不过 ⇒ 整体搬来并合并（原文件已删）。
 * 保留原有用例的独有断言（语言变更同时刷新 uiState、多条消息**排队**与逐条确认、
 * 直接链接规则**成功**路径），本片新加的用例补上改动点与几条易错分支。
 *
 * 本片（M5-8a）真正改动的两处：
 *
 * 1. **消息从 `@StringRes Int` 换成 [OtherConfigMessageRes] 枚举** —— 用例断言拿到的是
 *    **枚举**而不是资源 id。「三条分支各映射到哪个枚举」写反了编译器看不出来。
 * 2. **`AppLog.put` → `AppLogStore.put`** —— 顺带钉住日志条目确实写进去了。
 *
 * 另有三条钉典型易错分支：`processText` 设置失败要**回滚**（`previous != enable` 那个判断）、
 * 改端口成功后要**要求重启 web 服务**、直接链接规则必填缺失时**不能写 gateway**。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class OtherConfigViewModelTest {

    @Test
    fun `init 合并三项设置且processText以系统侧为准`() {
        val other = FakeOtherSettingsGateway(OtherSettings(webServiceAutoStart = true))
        val readAloud = FakeReadAloudSettingsGateway(
            ReadAloudSettings(mediaButtonOnExit = false, ignoreAudioFocus = true)
        )
        val system = FakeSystemGateway(processText = false)
        val viewModel = createViewModel(other = other, readAloud = readAloud, system = system)
        idle()

        val state = viewModel.uiState.value
        assertTrue("otherSettings 的值要进来", state.webServiceAutoStart)
        assertEquals("readAloud 的三项单独覆盖", false, state.mediaButtonOnExit)
        assertTrue(state.ignoreAudioFocus)
        assertEquals(
            "processText 的初值来自 systemGateway，不是 otherSettings",
            false,
            state.processText,
        )
    }

    @Test
    fun `语言变更同时交给gateway并刷新uiState`() {
        val locale = FakeAppLocaleGateway()
        val viewModel = createViewModel(locale = locale)
        idle()

        viewModel.onIntent(OtherConfigIntent.LanguageChanged("en"))
        idle()

        assertEquals(listOf("en"), locale.setLanguageCalls)
        assertEquals("uiState 也要跟上", "en", viewModel.uiState.value.language)
    }

    @Test
    fun `设置失败的消息会排队直到UI确认`() {
        val other = FakeOtherSettingsGateway()
        val viewModel = createViewModel(other = other)
        idle()
        other.failure = IllegalStateException("boom")

        viewModel.onIntent(OtherConfigIntent.AutoRefreshChanged(true))
        viewModel.onIntent(OtherConfigIntent.DefaultToReadChanged(true))
        idle()

        val messages = viewModel.uiState.value.pendingMessages
        assertEquals("两次失败各一条，不互相覆盖", 2, messages.size)
        assertEquals("boom", messages.first().text)

        viewModel.onIntent(OtherConfigIntent.MessageShown(messages.first().id))
        idle()

        assertEquals("确认一条只出队一条", 1, viewModel.uiState.value.pendingMessages.size)
    }

    @Test
    fun `改端口成功后要求重启web服务`() {
        val other = FakeOtherSettingsGateway()
        val viewModel = createViewModel(other = other)
        idle()
        val effects = collect(viewModel)

        viewModel.onIntent(OtherConfigIntent.WebPortChanged(8080))
        idle()

        assertEquals(8080, other.currentSettings.webPort)
        assertEquals(
            "端口变了但服务没重启 = 用户以为没生效",
            listOf(OtherConfigEffect.RestartWebService),
            effects.toList(),
        )
    }

    @Test
    fun `processText设置失败会回滚到原值`() {
        val other = FakeOtherSettingsGateway()
        val system = FakeSystemGateway(processText = true, failFirstSet = true)
        val viewModel = createViewModel(other = other, system = system)
        idle()

        viewModel.onIntent(OtherConfigIntent.ProcessTextChanged(false))
        idle()

        assertEquals(
            "先试新值（失败）再回滚旧值",
            listOf(false, true),
            system.setProcessTextCalls,
        )
        assertTrue("回滚后仍是原值", system.processText)
        assertEquals(
            "要发一条消息告诉用户失败",
            1,
            viewModel.uiState.value.pendingMessages.count { it.text != null },
        )
    }

    @Test
    fun `清webview成功发成功枚举并要求重启`() {
        val viewModel = createViewModel()
        idle()
        val effects = collect(viewModel)

        viewModel.onIntent(OtherConfigIntent.ConfirmClearWebViewData)
        idle()

        assertEquals(
            "迁移前这里是 `resId == R.string.clear_webview_data_success`",
            listOf(OtherConfigMessageRes.ClearWebViewDataSuccess),
            viewModel.uiState.value.pendingMessages.mapNotNull { it.res },
        )
        assertEquals(listOf(OtherConfigEffect.RestartApp), effects.toList())
        assertEquals("确认后弹窗要关掉", null, viewModel.uiState.value.activeOverlay)
    }

    @Test
    fun `清webview失败发失败枚举并记录日志`() {
        AppLogStore.clear()
        val system = FakeSystemGateway(clearWebViewError = RuntimeException("boom"))
        val viewModel = createViewModel(system = system)
        idle()

        viewModel.onIntent(OtherConfigIntent.ConfirmClearWebViewData)
        idle()

        assertEquals(
            listOf(OtherConfigMessageRes.ClearWebViewDataFailed),
            viewModel.uiState.value.pendingMessages.mapNotNull { it.res },
        )
        assertTrue(
            "失败要进共享日志（迁移前是 AppLog.put）",
            AppLogStore.logs.any { it.second == "清除 WebView 数据失败" },
        )
    }

    @Test
    fun `直接链接规则成功时写gateway并关弹层`() {
        val directLink = FakeDirectLinkSettingsGateway(validRule)
        val localPassword = FakeLocalPasswordGateway()
        val viewModel = createViewModel(directLink = directLink, localPassword = localPassword)
        idle()

        viewModel.onIntent(
            OtherConfigIntent.ShowOverlay(OtherConfigOverlay.DirectLinkUpload)
        )
        viewModel.onIntent(OtherConfigIntent.ConfirmDirectLinkRule)
        viewModel.onIntent(OtherConfigIntent.SaveLocalPassword("secret"))
        idle()

        assertEquals(validRule, directLink.savedRules.single())
        assertEquals("secret", localPassword.lastPassword)
        assertEquals("成功后弹层要关", null, viewModel.uiState.value.activeOverlay)
    }

    @Test
    fun `直接链接规则必填缺失时不写gateway且弹层保持打开`() {
        val directLink = FakeDirectLinkSettingsGateway()
        val viewModel = createViewModel(directLink = directLink)
        idle()

        // 先真的把弹层打开 —— 否则「弹层还开着」这条断言是假的通过。
        viewModel.onIntent(
            OtherConfigIntent.ShowOverlay(OtherConfigOverlay.DirectLinkUpload)
        )
        idle()
        viewModel.onIntent(OtherConfigIntent.ConfirmDirectLinkRule)
        idle()

        assertEquals(
            listOf(OtherConfigMessageRes.CompleteRequiredInformation),
            viewModel.uiState.value.pendingMessages.mapNotNull { it.res },
        )
        assertTrue("校验没过就不该调用保存", directLink.savedRules.isEmpty())
        assertEquals(
            "校验没过时弹层要保持打开让用户补（成功路径才会 copy(activeOverlay = null)）",
            OtherConfigOverlay.DirectLinkUpload,
            viewModel.uiState.value.activeOverlay,
        )
    }

    private fun createViewModel(
        locale: AppLocaleGateway = FakeAppLocaleGateway(),
        readAloud: ReadAloudSettingsGateway = FakeReadAloudSettingsGateway(),
        other: OtherSettingsGateway = FakeOtherSettingsGateway(),
        downloadCache: DownloadCacheSettingsGateway = FakeDownloadCacheSettingsGateway(),
        directLink: DirectLinkSettingsGateway = FakeDirectLinkSettingsGateway(),
        localPassword: LocalPasswordGateway = FakeLocalPasswordGateway(),
        system: OtherConfigSystemGateway = FakeSystemGateway(),
    ) = OtherConfigViewModel(
        appLocaleGateway = locale,
        readAloudSettingsGateway = readAloud,
        otherSettingsGateway = other,
        downloadCacheSettingsGateway = downloadCache,
        directLinkSettingsGateway = directLink,
        localPasswordGateway = localPassword,
        systemGateway = system,
    )

    private fun collect(viewModel: OtherConfigViewModel): MutableList<OtherConfigEffect> {
        val out = mutableListOf<OtherConfigEffect>()
        CoroutineScope(Dispatchers.Unconfined).launch { viewModel.effects.collect { out += it } }
        return out
    }

    private fun idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private companion object {
        /** 「必填都填了」的规则；`FakeDirectLinkSettingsGateway()` 的默认值则是全空（触发校验失败）。 */
        val validRule = DirectLinkRule(
            uploadUrl = "https://example.com",
            downloadUrlRule = "$.url",
            summary = "Example",
        )
    }
}

private class FakeAppLocaleGateway : AppLocaleGateway {
    private val state = MutableStateFlow("auto")
    val setLanguageCalls = mutableListOf<String>()

    override val currentLanguage: String get() = state.value
    override val language: MutableStateFlow<String> get() = state

    override fun setLanguage(language: String) {
        setLanguageCalls += language
        state.value = language
    }

    override fun synchronizeFromPlatform() = Unit
    override fun migrateLegacyLanguage(language: String) = Unit
}

private class FakeReadAloudSettingsGateway(
    initial: ReadAloudSettings = ReadAloudSettings(),
) : ReadAloudSettingsGateway {
    private val state = MutableStateFlow(initial)
    override val currentSettings: ReadAloudSettings get() = state.value
    override val settings: Flow<ReadAloudSettings> = state
    override suspend fun update(transform: (ReadAloudSettings) -> ReadAloudSettings) {
        state.value = transform(state.value)
    }
}

private class FakeOtherSettingsGateway(
    initial: OtherSettings = OtherSettings(),
) : OtherSettingsGateway {
    private val state = MutableStateFlow(initial)
    var failure: Throwable? = null
    override val currentSettings: OtherSettings get() = state.value
    override val settings: Flow<OtherSettings> = state
    override suspend fun update(transform: (OtherSettings) -> OtherSettings) {
        failure?.let { throw it }
        state.value = transform(state.value)
    }
}

private class FakeDownloadCacheSettingsGateway : DownloadCacheSettingsGateway {
    private val state = MutableStateFlow(DownloadCacheSettings())
    override val currentSettings: DownloadCacheSettings get() = state.value
    override val settings: Flow<DownloadCacheSettings> = state
    override suspend fun update(transform: (DownloadCacheSettings) -> DownloadCacheSettings) {
        state.value = transform(state.value)
    }
}

private class FakeDirectLinkSettingsGateway(
    private val rule: DirectLinkRule = DirectLinkRule("", "", "", false),
) : DirectLinkSettingsGateway {
    val savedRules = mutableListOf<DirectLinkRule>()

    override suspend fun loadRule(): DirectLinkRule = rule
    override suspend fun loadDefaultRules(): List<DirectLinkRule> =
        if (rule.summary.isBlank()) emptyList() else listOf(rule)

    override suspend fun saveRule(rule: DirectLinkRule) {
        savedRules += rule
    }

    override suspend fun testRule(rule: DirectLinkRule): String = "ok"
}

private class FakeLocalPasswordGateway : LocalPasswordGateway {
    var lastPassword: String? = null
    override suspend fun setPassword(password: String?) {
        lastPassword = password
    }
}

private class FakeSystemGateway(
    var processText: Boolean = true,
    var failFirstSet: Boolean = false,
    private val clearWebViewError: Throwable? = null,
) : OtherConfigSystemGateway {
    val setProcessTextCalls = mutableListOf<Boolean>()

    override fun isProcessTextEnabled(): Boolean = processText

    override suspend fun setProcessTextEnabled(enabled: Boolean) {
        setProcessTextCalls += enabled
        if (failFirstSet) {
            failFirstSet = false
            throw RuntimeException("processText 写入失败")
        }
        processText = enabled
    }

    override suspend fun clearWebViewData() {
        clearWebViewError?.let { throw it }
    }
}
