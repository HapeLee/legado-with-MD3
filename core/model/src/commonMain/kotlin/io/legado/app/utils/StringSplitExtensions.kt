package io.legado.app.utils

/** Splits, trims, and drops blank fields from persisted rule and grouping strings. */
fun String.splitNotBlank(vararg delimiter: String, limit: Int = 0): Array<String> =
    split(*delimiter, limit = limit).map { it.trim() }.filterNot { it.isBlank() }.toTypedArray()

/** Regex counterpart of [splitNotBlank] for rule-defined delimiters. */
fun String.splitNotBlank(regex: Regex, limit: Int = 0): Array<String> =
    split(regex, limit).map { it.trim() }.filterNot { it.isBlank() }.toTypedArray()
