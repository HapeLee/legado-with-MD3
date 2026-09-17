package io.legado.app.feature.about

import android.app.Application
import android.os.Looper
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.model.settings.BackupSettings
import io.legado.app.domain.model.settings.OtherSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * `AboutViewModel` 的行为基线。
 *
 * 第一条用例**从 `:app/src/test/java/io/legado/app/ui/about/AboutViewModelTest.kt` 搬来**
 * （M5-1c：被测对象下沉到 `:feature:about`，用例跟着搬，属**有意的归属调整**、不是用例增减）。
 * 它守的是「更新渠道只经唯一 `uiState` 入口下发」——`init` 里那条
 * `settings.map{updateToVariant}.distinctUntilChanged().collect{}`。
 *
 * 搬来时**去掉**了原来那句 `application.injectAsAppCtx()`：旧 VM 继承 `BaseViewModel`、块里
 * 直连 `FileDoc`/`utils`（那些读 `appCtx`），而新 VM 一个都不碰——`appCtx` 依赖整体留在
 * `:app` 的 Android 实现侧（`AndroidAboutCapabilities.kt`）。Robolectric 仍然保留，因为
 * `viewModelScope` 需要真实的 `Dispatchers.Main`。
 *
 * 第二条是**本片新增**的：`saveLog` 里「备份目录未设置 ⇒ 只提示、不落盘」这条判定以前写在
 * `:app` 的 VM 里（用 `context` 顺手判），下沉后成了共享层自己的分支逻辑，必须被钉住——
 * 尤其要钉住「**没有**调用 `diagnostics.saveLogs()`」，否则提示与动作会同时发生。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AboutViewModelTest {

    @Test
    fun `更新渠道通过唯一UiState入口下发`() {
        val gateway = FakeOtherSettingsGateway("official_version")
        val viewModel = createViewModel(otherSettingsGateway = gateway)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        gateway.emit("beta")
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals("beta", viewModel.uiState.value.updateToVariant)
    }

    @Test
    fun `未设置备份目录时保存日志只提示不落盘`() {
        val diagnostics = RecordingAboutDiagnostics()
        val viewModel = createViewModel(
            backupSettingsGateway = FakeBackupSettingsGateway(backupPath = null),
            diagnostics = diagnostics,
        )
        val effects = mutableListOf<AboutEffect>()
        val collector = CoroutineScope(Dispatchers.Unconfined).launch {
            viewModel.effects.toList(effects)
        }

        viewModel.onIntent(AboutIntent.SaveLog)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        collector.cancel()

        assertEquals(
            listOf(AboutEffect.ShowMessage(AboutMessage.BackupDirNotSet)),
            effects,
        )
        assertEquals("不该落盘", 0, diagnostics.saveLogsCalls)
    }

    private fun createViewModel(
        otherSettingsGateway: OtherSettingsGateway = FakeOtherSettingsGateway("official_version"),
        backupSettingsGateway: BackupSettingsGateway = FakeBackupSettingsGateway(),
        diagnostics: AboutDiagnostics = RecordingAboutDiagnostics(),
    ): AboutViewModel = AboutViewModel(
        otherSettingsGateway = otherSettingsGateway,
        backupSettingsGateway = backupSettingsGateway,
        updateChecker = FakeAppUpdateChecker(),
        diagnostics = diagnostics,
        bundledTextReader = FakeBundledTextReader(),
    )

    /** 迁移前用例里的同名假实现，逐字保留。 */
    private class FakeOtherSettingsGateway(initialVariant: String) : OtherSettingsGateway {
        private val state = MutableStateFlow(OtherSettings(updateToVariant = initialVariant))

        override val currentSettings: OtherSettings get() = state.value
        override val settings: Flow<OtherSettings> = state

        fun emit(variant: String) {
            state.value = state.value.copy(updateToVariant = variant)
        }

        override suspend fun update(transform: (OtherSettings) -> OtherSettings) {
            state.value = transform(state.value)
        }
    }

    private class FakeBackupSettingsGateway(backupPath: String? = null) : BackupSettingsGateway {
        private val state = MutableStateFlow(BackupSettings(backupPath = backupPath))

        override val currentSettings: BackupSettings get() = state.value
        override val settings: Flow<BackupSettings> = state

        override suspend fun update(transform: (BackupSettings) -> BackupSettings) {
            state.value = transform(state.value)
        }
    }

    /** 记录调用次数——第二条用例靠它证明「提示了但没动作」。 */
    private class RecordingAboutDiagnostics : AboutDiagnostics {
        var saveLogsCalls = 0
            private set

        override suspend fun listCrashLogs(): List<CrashLogEntry> = emptyList()

        override suspend fun readCrashLog(entry: CrashLogEntry): Result<String> =
            Result.failure(IllegalStateException("用例未准备"))

        override suspend fun clearCrashLogs() = Unit

        override suspend fun saveLogs() {
            saveLogsCalls++
        }

        override suspend fun createHeapDump(): Boolean = false
    }

    private class FakeAppUpdateChecker : AppUpdateChecker {
        override suspend fun check(): Result<UpdateInfo> =
            Result.failure(IllegalStateException("用例未准备"))
    }

    private class FakeBundledTextReader : BundledTextReader {
        override suspend fun readText(fileName: String): String? = null
    }
}
