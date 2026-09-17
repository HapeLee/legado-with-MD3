package io.legado.app.host.desktop

import io.legado.app.feature.about.AboutDiagnostics
import io.legado.app.feature.about.AppUpdateChecker
import io.legado.app.feature.about.BundledTextReader
import io.legado.app.feature.about.CrashLogEntry
import io.legado.app.feature.about.UpdateInfo

/**
 * `:feature:about` 三个平台契约的 desktop 实现：**显式不可用**（M5-1c）。
 *
 * 按 AGENTS.md「平台能力不可用时必须显式建模为 capability/unsupported，不得用静默空实现
 * 伪造跨平台支持」抛出 [UnsupportedOperationException]。先例是 M2-1 的
 * [DesktopImportJsonEditor]（连同它的断言用例 `DesktopImportJsonEditorTest`）。
 *
 * 为什么**不**顺手用 JVM 等价物复刻一份：这三个契约的每一处都是在复刻 Android 的行为契约，
 * 而不是在实现一个抽象能力——
 *
 *  - [DesktopAppUpdateChecker]：Android 实现委托的 `AppUpdateGitHub` 走 GSON 门面
 *    （`:core:data/androidMain`）+ OkHttp + `AppConst.appInfo`（版本名/渠道），还要解析
 *    GitHub release 的资产名来挑 ABI 匹配的包。desktop 没有版本名与 ABI 概念，这份复刻
 *    没有任何验证基准。
 *  - [DesktopAboutDiagnostics]：崩溃日志源自 Android 的 `CrashHandler`（`crash` 目录约定）、
 *    日志目录布局（`logs` / `logcat.txt` / `heapDump`）与 `Runtime.exec("logcat -d")`；
 *    备份目录是 SAF 树 URI。desktop 一样都没有。
 *  - [DesktopBundledTextReader]：Android 的 `assets` 在 desktop 侧要先决定「资源怎么打进
 *    jar、解码口径是否与 `assets` 一致」，那是需要独立证据的切片。
 *
 * 另一个关键理由与 `DesktopImportJsonEditor` 相同：这些方法都有**看似合理**的降级返回值
 * （空列表 / `null` / `false`），很容易被顺手写成「返回空，让它别崩」。那会把
 * 「桌面端还没做」伪装成「没有崩溃日志 / 没有这篇文档 / 保存成功」——用户看到的是内容问题，
 * 而不是能力缺失。谁把它改成静默降级，谁就得先改
 * `DesktopAboutCapabilitiesTest` 里的断言。
 */
object DesktopAppUpdateChecker : AppUpdateChecker {

    override suspend fun check(): Result<UpdateInfo> = unsupported("check")
}

/**
 * @see DesktopAppUpdateChecker
 */
object DesktopAboutDiagnostics : AboutDiagnostics {

    override suspend fun listCrashLogs(): List<CrashLogEntry> = unsupported("listCrashLogs")

    override suspend fun readCrashLog(entry: CrashLogEntry): Result<String> =
        unsupported("readCrashLog")

    override suspend fun clearCrashLogs(): Unit = unsupported("clearCrashLogs")

    override suspend fun saveLogs(): Unit = unsupported("saveLogs")

    override suspend fun createHeapDump(): Boolean = unsupported("createHeapDump")
}

/**
 * @see DesktopAppUpdateChecker
 */
object DesktopBundledTextReader : BundledTextReader {

    override suspend fun readText(fileName: String): String? = unsupported("readText")
}

private fun unsupported(method: String): Nothing = throw UnsupportedOperationException(
    "desktop host 未实现「关于」页的平台能力（AboutDiagnostics/AppUpdateChecker/" +
        "BundledTextReader.$method）：Android 侧实现依赖 CrashHandler 的目录约定、" +
        "SAF 备份目录、assets 打包与 GitHub release 资产解析；desktop 复刻需要先建立等价证据。"
)
