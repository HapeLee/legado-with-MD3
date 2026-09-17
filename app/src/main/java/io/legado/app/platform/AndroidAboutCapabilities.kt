package io.legado.app.platform

import android.content.Context
import android.net.Uri
import io.legado.app.constant.AppLog
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.feature.about.AboutDiagnostics
import io.legado.app.feature.about.AppUpdateChecker
import io.legado.app.feature.about.BundledTextReader
import io.legado.app.feature.about.CrashLogEntry
import io.legado.app.help.CrashHandler
import io.legado.app.help.update.AppUpdate
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.createFileIfNotExist
import io.legado.app.utils.createFolderIfNotExist
import io.legado.app.utils.delete
import io.legado.app.utils.externalCache
import io.legado.app.utils.find
import io.legado.app.utils.getFile
import io.legado.app.utils.list
import io.legado.app.utils.openInputStream
import io.legado.app.utils.openOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileFilter

/**
 * `:feature:about` 三个平台契约的 Android 适配（M5-1c）。
 *
 * 这三段代码迁移前**整段住在 `AboutViewModel` 里**（连同 `context.externalCacheDir`、
 * `FileDoc` + SAF、`ZipUtils`、`Runtime.exec("logcat -d")`、`System.gc()`、
 * `CrashHandler.doHeapDump`），是这一片最大的「直连 Android」块。搬进 Feature 的
 * `commonMain` 时按 AGENTS.md「Android 专有能力通过窄接口进入共享层」抽成契约，
 * **实现逐行照抄**，只做两件事：
 *
 *  1. 包一层 `withContext(Dispatchers.IO)` —— 迁移前这些块跑在 `BaseViewModel.execute{}` 的
 *     `Dispatchers.IO` 上，回调切回 Main；现在共享层 VM 只 `viewModelScope.launch`，
 *     IO 必须由实现侧声明。
 *  2. 在 `FileDoc` ↔ [CrashLogEntry] 之间映射。共享层不能出现 `Uri`/SAF 类型，所以条目在边界上
 *     退化成「id + 展示名」：id 取 `FileDoc.uri.toString()`，回读时
 *     `FileDoc.fromUri(Uri.parse(id), false)`——与原对象只差一次 `Uri` 往返，
 *     `readBytes()` 走的是同一条 `uri.readBytes(appCtx)`。
 *
 * 失败语义与迁移前一致，**不要顺手统一**：
 *  - [AndroidAboutDiagnostics.saveLogs] / [AndroidAboutDiagnostics.createHeapDump] 的失败
 *    在实现内部记 `AppLog`（前者 `toast = true`、后者不带 toast，且都带各自的固定前缀），
 *    不往共享层抛——那两条分支在迁移前也没改过 UI 状态。
 *  - [AndroidAboutDiagnostics.clearCrashLogs] / [AndroidAboutDiagnostics.readCrashLog] 的失败
 *    **抛/返回 failure**，由共享层 VM 发 `AboutEffect.ShowText(it.localizedMessage ?: "")`
 *    ——迁移前同样如此。
 */
class AndroidAppUpdateChecker : AppUpdateChecker {

    /**
     * 委托既有的 `AppUpdate.gitHubUpdate`。
     *
     * 迁移前 VM 里是 `AppUpdate.gitHubUpdate?.run { check(viewModelScope).onSuccess{}.onError{}.onFinally{} }`。
     * `Coroutine` 的 `init` 里就启动了任务、且要求回调在完成前注册（它的 KDoc 明说
     * 「协程太快完成，回调会不执行」），所以这里同样在拿到对象后立刻挂回调、再用
     * `CompletableDeferred` 把值交回 `suspend` 世界。
     *
     * `Coroutine` 的块跑在 `Dispatchers.IO`、回调切到 `Dispatchers.Main`（与迁移前一致）；
     * 它被 `launch` 进本函数的 `coroutineScope` 作用域，调用方取消时会一起取消，不留悬挂任务。
     */
    override suspend fun check(): Result<io.legado.app.feature.about.UpdateInfo> = coroutineScope {
        val deferred = CompletableDeferred<Result<io.legado.app.feature.about.UpdateInfo>>()
        val updater = AppUpdate.gitHubUpdate
        if (updater == null) {
            // 迁移前 `gitHubUpdate == null` 时什么都不做（连进度对话框都不会收）。它实际不可达
            // （`AppUpdate.gitHubUpdate` 是 `by lazy { AppUpdateGitHub }`），但真出现时
            // 「挂死」比「明确报错」糟得多，所以这里显式失败，由调用方提示。
            deferred.complete(Result.failure(IllegalStateException("当前没有可用的更新渠道")))
        } else {
            updater.check(this)
                .onSuccess { updateInfo ->
                    deferred.complete(Result.success(updateInfo.toSharedUpdateInfo()))
                }
                .onError { throwable ->
                    deferred.complete(Result.failure(throwable))
                }
        }
        deferred.await()
    }

    private fun AppUpdate.UpdateInfo.toSharedUpdateInfo() =
        io.legado.app.feature.about.UpdateInfo(
            tagName = tagName,
            updateLog = updateLog,
            downloadUrl = downloadUrl,
            fileName = fileName,
        )
}

/**
 * 诊断能力的 Android 实现。逐行照抄迁移前 `AboutViewModel` 的那五段私有方法，
 * 只把 `context` 换成构造参数、把「取备份目录」从 VM 的判定改成实现自己的前置读取
 * （判定与提示仍留在共享层 VM —— 见 `AboutDiagnostics` 的 KDoc）。
 */
class AndroidAboutDiagnostics(
    private val context: Context,
    private val backupSettingsGateway: BackupSettingsGateway,
) : AboutDiagnostics {

    override suspend fun listCrashLogs(): List<CrashLogEntry> = withContext(Dispatchers.IO) {
        val list = arrayListOf<CrashLogEntry>()
        context.externalCacheDir
            ?.getFile("crash")
            ?.listFiles(FileFilter { it.isFile })
            ?.forEach { list.add(FileDoc.fromFile(it).toEntry()) }
        val backupPath = backupSettingsGateway.currentSettings.backupPath
        if (!backupPath.isNullOrEmpty()) {
            FileDoc.fromUri(Uri.parse(backupPath), true)
                .find("crash")
                ?.list { !it.isDir }
                ?.let { docs -> list.addAll(docs.map { it.toEntry() }) }
        }
        list.sortedByDescending { it.name }.distinctBy { it.name }
    }

    override suspend fun readCrashLog(entry: CrashLogEntry): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                // 迁移前直接对列表里的 `FileDoc` 调 `readBytes()`；这里用 id 还原一个等价对象。
                String(FileDoc.fromUri(Uri.parse(entry.id), false).readBytes())
            }
        }

    override suspend fun clearCrashLogs(): Unit = withContext(Dispatchers.IO) {
        context.externalCacheDir
            ?.getFile("crash")
            ?.let { FileUtils.delete(it, false) }
        val backupPath = backupSettingsGateway.currentSettings.backupPath
        if (!backupPath.isNullOrEmpty()) {
            FileDoc.fromUri(Uri.parse(backupPath), true)
                .find("crash")
                ?.delete()
        }
    }

    override suspend fun saveLogs(): Unit = withContext(Dispatchers.IO) {
        try {
            val backupPath = backupSettingsGateway.currentSettings.backupPath
                ?: return@withContext
            val doc = FileDoc.fromUri(Uri.parse(backupPath), true)
            copyLogs(doc)
            copyHeapDump(doc)
        } catch (e: Exception) {
            // 迁移前 `execute{}.onError { AppLog.put("保存日志出错\n${it.localizedMessage}", it, true) }`。
            AppLog.put("保存日志出错\n${e.localizedMessage}", e, true)
        }
    }

    override suspend fun createHeapDump(): Boolean = withContext(Dispatchers.IO) {
        try {
            val backupPath = backupSettingsGateway.currentSettings.backupPath
                ?: return@withContext false
            System.gc()
            CrashHandler.doHeapDump(true)
            val doc = FileDoc.fromUri(Uri.parse(backupPath), true)
            copyHeapDump(doc)
        } catch (e: Exception) {
            // 迁移前 `execute{}.onError { AppLog.put("保存堆转储失败\n${it.localizedMessage}", it) }`
            // ——注意与 saveLogs 不同：这一条**不带 toast**。
            AppLog.put("保存堆转储失败\n${e.localizedMessage}", e)
            false
        }
    }

    private fun FileDoc.toEntry() = CrashLogEntry(id = uri.toString(), name = name)

    private fun copyLogs(doc: FileDoc) {
        val cacheDir = context.externalCache
        val logFiles = File(cacheDir, "logs")
        val crashFiles = File(cacheDir, "crash")
        val logcatFile = File(cacheDir, "logcat.txt")
        dumpLogcat(logcatFile)
        val zipFile = File(cacheDir, "logs.zip")
        ZipUtils.zipFiles(arrayListOf(logFiles, crashFiles, logcatFile), zipFile)
        doc.find("logs.zip")?.delete()
        zipFile.inputStream().use { input ->
            doc.createFileIfNotExist("logs.zip").openOutputStream().getOrNull()
                ?.use { input.copyTo(it) }
        }
        zipFile.delete()
    }

    private fun copyHeapDump(doc: FileDoc): Boolean {
        val heapFile = FileDoc.fromFile(File(context.externalCache, "heapDump")).list()
            ?.firstOrNull() ?: return false
        doc.find("heapDump")?.delete()
        val heapDumpDoc = doc.createFolderIfNotExist("heapDump")
        heapFile.openInputStream().getOrNull()?.use { input ->
            heapDumpDoc.createFileIfNotExist(heapFile.name).openOutputStream().getOrNull()
                ?.use { input.copyTo(it) }
        }
        return true
    }

    private fun dumpLogcat(file: File) {
        try {
            val process = Runtime.getRuntime().exec("logcat -d")
            file.outputStream().use { process.inputStream.copyTo(it) }
        } catch (e: Exception) {
            AppLog.put("保存Logcat失败\n$e", e)
        }
    }
}

/**
 * 内置 markdown 的 Android 实现：`assets.open(fileName).readBytes()`。
 *
 * 迁移前是 `String(context.assets.open(fileName).readBytes())`（默认字符集 = UTF-8）。
 * 读不到就返回 `null`，让共享层的「不弹层」行为继续成立。
 */
class AndroidBundledTextReader(
    private val context: Context,
) : BundledTextReader {

    override suspend fun readText(fileName: String): String? = withContext(Dispatchers.IO) {
        runCatching { String(context.assets.open(fileName).readBytes()) }.getOrNull()
    }
}
