package io.legado.app.utils

/**
 * JSON 文本形状判定（P4 规则 VM 去 app 直连第 3 刀）。
 *
 * 从 `:app` 的 `utils/StringExtensions.kt` 原样下沉：规则列表 VM 的 `parseImportRules`
 * 用它们区分「导入的是单个规则对象还是一组规则」，Feature 提升为独立模块后不可再用 app 工具。
 *
 * 语义与原实现逐字一致：只做 `trim()` 后的首尾字符判定，**不解析 JSON**。
 */
fun String?.isJsonObject(): Boolean =
    this?.run {
        val str = this.trim()
        str.startsWith("{") && str.endsWith("}")
    } ?: false

fun String?.isJsonArray(): Boolean =
    this?.run {
        val str = this.trim()
        str.startsWith("[") && str.endsWith("]")
    } ?: false
