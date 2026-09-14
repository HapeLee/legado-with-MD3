package io.legado.app.host.desktop

import io.legado.app.core.rules.RuleTransferPlatform
import java.io.File

/**
 * [RuleTransferPlatform] 的 desktop 实现。
 *
 * 契约的三分支语义（见 `RuleTransferPlatform` KDoc）：
 *
 * | 输入 | Android（`AndroidRuleTransferPlatform`） | desktop（本类） |
 * | --- | --- | --- |
 * | 绝对 URL | okhttp GET + 解压（`#requestWithoutUA` 特例） | **显式 unsupported**（抛异常） |
 * | `file://` URI / 本地路径 | ——（`content://` 走 ContentResolver） | 读文件 |
 * | 其余（纯 JSON 文本） | 原样返回 | 原样返回 |
 *
 * 为什么 URL 分支是抛异常而不是「返回空串」：AGENTS.md 要求平台能力缺失时**显式建模**，
 * 不得用静默空实现伪造。desktop host 目前没有注入 `HttpClient`（`:core:platform` 的
 * `HttpClient` 契约还没有 desktop 实现），把 URL 当纯文本返回会让导入静默失败——
 * 用户看到的是「格式不正确」，而不是「这个能力还没接」。
 *
 * [writeExport] 写文件，且**失败必须静默**：迁移前 `openOutputStream` 返回 null 就什么都不做，
 * 调用方随后仍报告「导出成功」。改这条语义等于改用户可见行为，所以这里 `runCatching` 吞掉。
 */
class DesktopRuleTransferPlatform : RuleTransferPlatform {

    override suspend fun readImportSource(text: String): String {
        val trimmed = text.trim()
        return when {
            isFileUri(trimmed) -> readFile(File(trimmed.removePrefix(FILE_SCHEME)))
            isAbsoluteHttpUrl(trimmed) -> throw UnsupportedOperationException(
                "desktop host 未接入 HttpClient：暂不支持从 URL 导入规则（$trimmed）。" +
                    "请先导出为文件后导入，或给 :core:platform 的 HttpClient 补 desktop 实现。"
            )

            isExistingPath(trimmed) -> readFile(File(trimmed))
            // 纯 JSON 文本：契约要求原样返回。
            else -> text
        }
    }

    override suspend fun writeExport(targetUri: String, content: String) {
        runCatching {
            val file = File(targetUri.removePrefix(FILE_SCHEME))
            file.parentFile?.mkdirs()
            file.writeText(content)
        }
    }

    private fun readFile(file: File): String =
        file.readText(Charsets.UTF_8)

    private fun isExistingPath(text: String): Boolean =
        !text.contains("\n") && File(text).isFile

    private fun isFileUri(text: String): Boolean =
        text.startsWith(FILE_SCHEME)

    private fun isAbsoluteHttpUrl(text: String): Boolean =
        text.startsWith("http://", ignoreCase = true) ||
            text.startsWith("https://", ignoreCase = true)

    private companion object {
        const val FILE_SCHEME = "file://"
    }
}
