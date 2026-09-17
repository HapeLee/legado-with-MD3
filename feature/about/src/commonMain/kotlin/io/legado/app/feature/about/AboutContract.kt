package io.legado.app.feature.about

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * 「关于」页的 UI 状态（M5-1c 搬进共享层，字段与迁移前逐字一致）。
 *
 * 两处**有意**的类型变化：
 *  1. `crashLogFiles` 从 `List<FileDoc>` 换成 `ImmutableList<CrashLogEntry>`。前者暴露了
 *     Android 的 `Uri`/SAF 类型（AGENTS.md 禁止共享契约暴露 `Uri`），后者只带「id + 展示名」；
 *     集合本身按 AGENTS.md「Compose 渲染边界中的集合使用 kotlinx.collections.immutable」。
 *  2. `AboutSheet.Update` 的 `updateInfo` 从 `AppUpdate.UpdateInfo` 换成共享层同名字段集的数据类
 *     （`UpdateInfo`，定义在 `AppUpdateChecker.kt`）。
 */
@Stable
data class AboutUiState(
    val updateToVariant: String = "official_version",
    val sheet: AboutSheet = AboutSheet.None,
    val dialog: AboutDialog? = null,
    val crashLogFiles: ImmutableList<CrashLogEntry> = persistentListOf(),
)

sealed interface AboutSheet {
    data object None : AboutSheet
    data class Markdown(val title: String, val content: String) : AboutSheet
    data object CrashLogs : AboutSheet
    data class Update(
        val updateInfo: UpdateInfo,
        val mode: UpdateMode = UpdateMode.UPDATE,
    ) : AboutSheet
}

enum class UpdateMode { UPDATE, VIEW_LOG }

sealed interface AboutDialog {
    data object CheckingUpdate : AboutDialog
}

sealed interface AboutIntent {
    data object DismissSheet : AboutIntent
    data object DismissDialog : AboutIntent
    data object CheckUpdate : AboutIntent
    data class ShowMdFile(val title: String, val fileName: String) : AboutIntent
    data object ShowCrashLogs : AboutIntent
    data object SaveLog : AboutIntent
    data object CreateHeapDump : AboutIntent
    data class OpenUrl(val url: String) : AboutIntent
    data object ClearCrashLogs : AboutIntent
    data class ReadCrashFile(val entry: CrashLogEntry) : AboutIntent
    data object StartDownload : AboutIntent
}

/**
 * 需要**由 UI 侧本地化**的提示。
 *
 * 迁移前这些文案是 VM 里 `context.getString(R.string.x)` 就地取好的，`AboutEffect.ShowToast`
 * 直接携带成品字符串。共享层的 VM 没有 `Context`，而 CMP 的 `stringResource` 是
 * `@Composable`、`getString` 是 `suspend`，都不适合在共享 VM 里逐条调用 ⇒ 改成**枚举 + 由
 * Route/Screen 查表**。
 *
 * 为什么不用 `StringResource` 直接进 Effect：那会把 `org.jetbrains.compose.resources` 变成
 * 本模块的 `api` 依赖（消费方编译 public 签名时要看得见），而枚举是零依赖的契约。
 */
enum class AboutMessage {
    /** 检查更新失败；`detail` 放异常信息，UI 侧按 `文案\n异常信息` 拼接（迁移前的形态）。 */
    CheckUpdateFailed,

    /** 未设置备份目录。 */
    BackupDirNotSet,

    /** 「记录日志」开关未打开。 */
    LogRecordingDisabled,

    /** 已保存到备份目录。 */
    SavedToBackupDir,

    /** 「记录堆转储」开关未打开。 */
    HeapDumpRecordingDisabled,

    /** 正在生成堆转储。 */
    HeapDumpCreating,

    /** 没找到堆转储文件。 */
    HeapDumpNotFound,

    /** 更新包的下载信息不完整。 */
    DownloadInfoIncomplete,
}

sealed interface AboutEffect {
    data class OpenUrl(val url: String) : AboutEffect

    /** 本地化提示。[detail] 非空时由 UI 侧按 `"$message\n$detail"` 拼接。 */
    data class ShowMessage(
        val message: AboutMessage,
        val detail: String? = null,
    ) : AboutEffect

    /** 只有运行期文本、没有固定文案的提示（迁移前就是 `it.localizedMessage ?: ""`）。 */
    data class ShowText(val text: String) : AboutEffect

    data class StartDownload(val url: String, val fileName: String) : AboutEffect
}
