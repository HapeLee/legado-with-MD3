package io.legado.app.feature.about

import androidx.compose.runtime.Stable

/**
 * 一条崩溃日志（M5-1c）。
 *
 * 迁移前这些条目是 `FileDoc`（`name` / `isDir` / `size` / `lastModified` / `uri: Uri`）。
 * 共享层不能出现 `Uri`/SAF 类型（AGENTS.md），而列表与「打开某条」只需要两件事：
 *  - [name]：列表里显示、并在 MarkdownSheet 标题里回显（迁移前就是 `fileDoc.name`）；
 *  - [id]：把点中的条目还给平台实现去读内容。Android 实现用 `FileDoc.uri.toString()` 当 id，
 *    回读时 `FileDoc.fromUri(Uri.parse(id), false)`——只走 `uri.readBytes(appCtx)`，
 *    与迁移前 `fileDoc.readBytes()` 等价。
 */
@Stable
data class CrashLogEntry(
    val id: String,
    val name: String,
)

/**
 * 诊断能力契约（M5-1c）：崩溃日志的列举/读取/清空，以及「保存日志」与「生成堆转储」。
 *
 * 这是本片最大的一个平台契约，收进来的原因很直接 —— 迁移前 `AboutViewModel` 里这一段是
 * **整段直连 Android**：`context.externalCacheDir`、`FileUtils`、`FileDoc` + SAF、
 * `ZipUtils`、`Runtime.getRuntime().exec("logcat -d")`、`System.gc()`、
 * `CrashHandler.doHeapDump()`。没有一样能在 commonMain 表达。
 *
 * 契约边界刻意**只到「动作」**，不到「实现细节」：
 *  - 备份目录路径（`BackupSettings.backupPath`）与两个开关（`OtherSettings.recordLog` /
 *    `recordHeapDump`）都能从共享层读到（`OtherSettingsGateway` / `BackupSettingsGateway`
 *    住在 `:core:data` 的 commonMain）⇒ **前置校验与「未设置/未开启」提示留在 VM**，
 *    实现侧不重复判断，也就不会出现两处判定漂移。
 *  - 由此 [saveLogs] / [createHeapDump] 失败时**不改变 UI 状态**，只记 `AppLog`——与迁移前
 *    的 `execute { }.onError { AppLog.put(...) }` 一致；那两条分支的日志与轻提示由**实现侧**
 *    原样保留（`:app` 的 `AppLog.put(message, throwable, toast = true)` 会弹提示，而共享层的
 *    `AppLogStore.put` 不弹），所以异常在实现内部消化，不往共享层抛。
 *
 * 平台能力缺失必须显式建模：desktop 实现一律抛 `UnsupportedOperationException`
 * （先例 `:host:desktop` 的 `DesktopImportJsonEditor`），不得返回空列表/null 伪装成
 * 「没有崩溃日志」。
 */
interface AboutDiagnostics {

    /**
     * 列出崩溃日志：外部缓存的 `crash` 目录 + 备份目录下的 `crash` 目录，按名字降序、按名字去重。
     * 与迁移前 `loadCrashLogFiles()` 逐行对应（包括两个目录都不可用 ⇒ 返回空列表）。
     */
    suspend fun listCrashLogs(): List<CrashLogEntry>

    /** 读取一条崩溃日志的文本；失败以 `Result.failure` 返回（迁移前 `execute{}.onError`）。 */
    suspend fun readCrashLog(entry: CrashLogEntry): Result<String>

    /** 清空崩溃日志（外部缓存的 `crash` 目录 + 备份目录下的 `crash` 目录）。失败抛异常。 */
    suspend fun clearCrashLogs()

    /**
     * 保存日志到备份目录：把 `logs` / `crash` / `logcat.txt` 打成 `logs.zip` 落盘，
     * 并顺带把堆转储目录一起复制过去。
     */
    suspend fun saveLogs()

    /**
     * 生成堆转储并复制到备份目录。
     *
     * @return `false` 表示没找到堆转储文件（迁移前 `copyHeapDump` 的 `?: return false`，
     *   UI 侧据此提示 `AboutMessage.HeapDumpNotFound`）。
     */
    suspend fun createHeapDump(): Boolean
}
