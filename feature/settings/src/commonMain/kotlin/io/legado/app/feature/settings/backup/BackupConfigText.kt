package io.legado.app.feature.settings.backup

import androidx.compose.runtime.Composable
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.backup
import io.legado.app.feature.settings.res.backup_fail
import io.legado.app.feature.settings.res.backup_success
import io.legado.app.feature.settings.res.loading
import io.legado.app.feature.settings.res.on_restore
import io.legado.app.feature.settings.res.restore_fail_with_error
import io.legado.app.feature.settings.res.restore_success
import io.legado.app.feature.settings.res.test_sync_loading_text
import io.legado.app.feature.settings.res.test_sync_status_fail
import io.legado.app.feature.settings.res.test_sync_status_success
import io.legado.app.feature.settings.res.webdav_restore_fail
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * 本页需要**查表成文案**的消息种类（迁移前是 `R.string.*` 的 `Int` 资源 id）。
 *
 * 两种情况都用它：
 *  - `BackupConfigDialog.Loading(title)` —— 对话框标题，由 UI 侧 [localized] 渲染；
 *  - `BackupConfigEffect.ShowMessage(message, argument)` —— 一次性提示，由宿主收集 Effect 时
 *    用 [localizedText] 取文案，`argument` 仍是可以为空的格式化参数。
 *
 * 枚举名按**语义**取，不照抄资源名（`test_sync_status_success` → [TestSyncSuccess]）。
 */
enum class BackupConfigText {
    /** 测试 WebDAV 连接中。 */
    TestSyncLoading,

    /** 测试 WebDAV 成功。 */
    TestSyncSuccess,

    /** 测试 WebDAV 失败。 */
    TestSyncFail,

    /** 正在备份（对话框标题）。 */
    BackingUp,

    /** 备份成功。 */
    BackupSuccess,

    /** 备份失败（可带错误详情）。 */
    BackupFail,

    /** 正在恢复（对话框标题）。 */
    Restoring,

    /** 通用加载中（对话框标题）。 */
    Loading,

    /** 恢复成功。 */
    RestoreSuccess,

    /** WebDAV 恢复失败（可带错误详情）。 */
    WebDavRestoreFail,

    /** 本地恢复失败（可带错误详情）。 */
    RestoreFailWithError,
}

/**
 * `BackupConfigText` → 本地化文案，两种适配器共用**同一张表**。
 *
 * 与 `feature/about` 的 `AboutMessage.localizedText()` 同一模式、同一理由（迁移前 VM 直接
 * 持有 `R.string.*` 的 `Int`，而 `@StringRes` / 资源 id 是 Android 概念，进不了 commonMain）。
 *
 * 本页比 about 多一个适配器：about 的消息只走 Effect（在协程里取文案即可），
 * 而这里 `BackupConfigDialog.Loading(title)` 是**要在组合里直接渲染的对话框标题**
 * ⇒ 除了 `suspend` 的 [localizedText] 还需要 `@Composable` 的 [localized]。
 * 两者都从 [resource] 这一处 [StringResource] 映射派生，避免 `when` 写两份而漂移。
 */
private fun BackupConfigText.resource(): StringResource = when (this) {
    BackupConfigText.TestSyncLoading -> Res.string.test_sync_loading_text
    BackupConfigText.TestSyncSuccess -> Res.string.test_sync_status_success
    BackupConfigText.TestSyncFail -> Res.string.test_sync_status_fail
    BackupConfigText.BackingUp -> Res.string.backup
    BackupConfigText.BackupSuccess -> Res.string.backup_success
    BackupConfigText.BackupFail -> Res.string.backup_fail
    BackupConfigText.Restoring -> Res.string.on_restore
    BackupConfigText.Loading -> Res.string.loading
    BackupConfigText.RestoreSuccess -> Res.string.restore_success
    BackupConfigText.WebDavRestoreFail -> Res.string.webdav_restore_fail
    BackupConfigText.RestoreFailWithError -> Res.string.restore_fail_with_error
}

/**
 * 组合内取文案（对话框标题等）。
 *
 * ⚠️ 映射函数刻意是 **private**：返回 `StringResource` 会把它摆进公开签名，逼着
 * `org.jetbrains.compose.resources` 从 `implementation` 升成 `api`（同 `AboutMessage` 的判断）。
 */
@Composable
fun BackupConfigText.localized(): String = stringResource(resource())

/**
 * 协程内取文案（宿主收集 Effect 时用）。
 *
 * [argument] 对应迁移前 `context.getString(res, argument)` 的那个格式化参数
 * （`backup_fail` / `webdav_restore_fail` / `restore_fail_with_error` 都带 `%1$s`；
 * 不带参数的提示传 `null`，等价于迁移前的 `context.getString(res)` 重载）。
 *
 * ⚠️ 调用点必须在协程里（`LaunchedEffect` / `rememberCoroutineScope().launch`）。
 */
suspend fun BackupConfigText.localizedText(argument: String? = null): String =
    if (argument == null) getString(resource()) else getString(resource(), argument)
