package io.legado.app.feature.about

import io.legado.app.feature.about.res.Res
import io.legado.app.feature.about.res.about_backup_dir_not_set
import io.legado.app.feature.about.res.about_download_info_incomplete
import io.legado.app.feature.about.res.about_heap_dump_creating
import io.legado.app.feature.about.res.about_heap_dump_not_found
import io.legado.app.feature.about.res.about_heap_dump_recording_disabled
import io.legado.app.feature.about.res.about_log_recording_disabled
import io.legado.app.feature.about.res.about_saved_to_backup_dir
import io.legado.app.feature.about.res.check_update
import org.jetbrains.compose.resources.getString

/**
 * [AboutMessage] → 本地化文案（M5-1c-2）。
 *
 * ## 为什么需要这一层
 *
 * 迁移前 VM 用 `context.getString(R.string.x)` 就地取好文案，`AboutEffect.ShowToast` 直接携带
 * 成品字符串。共享层的 VM 没有 `Context`，于是契约改成**枚举 + 由 UI 侧查表**
 * （理由见 `AboutContract.kt` 的 `AboutMessage` 注释：让本模块的**公开契约**零资源依赖）。
 * 本文件就是那张表——只有它会 import `Res`。
 *
 * ## 为什么返回 `String` 而不是 `StringResource`
 *
 * 返回 `StringResource` 会让 `org.jetbrains.compose.resources` 出现在**公开 API 的签名里**，
 * 于是它必须从 `implementation` 升成 `api`（消费方编译时要看得见该类型）；返回 `String` 则不必，
 * 资源依赖完全封闭在本模块内部。`getString` 本来就是 `suspend`，调用点（宿主收集 Effect 的
 * 协程里）天然适合。
 *
 * ⚠️ 调用点必须保证在**协程**里（`LaunchedEffect` / `rememberCoroutineScope().launch`）。
 */
suspend fun AboutMessage.localizedText(): String = getString(
    when (this) {
        AboutMessage.CheckUpdateFailed -> Res.string.check_update
        AboutMessage.BackupDirNotSet -> Res.string.about_backup_dir_not_set
        AboutMessage.LogRecordingDisabled -> Res.string.about_log_recording_disabled
        AboutMessage.SavedToBackupDir -> Res.string.about_saved_to_backup_dir
        AboutMessage.HeapDumpRecordingDisabled -> Res.string.about_heap_dump_recording_disabled
        AboutMessage.HeapDumpCreating -> Res.string.about_heap_dump_creating
        AboutMessage.HeapDumpNotFound -> Res.string.about_heap_dump_not_found
        AboutMessage.DownloadInfoIncomplete -> Res.string.about_download_info_incomplete
    }
)
