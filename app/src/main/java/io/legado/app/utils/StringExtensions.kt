@file:Suppress("unused")

package io.legado.app.utils

import android.annotation.SuppressLint
import android.icu.text.Collator
import android.icu.util.ULocale
import android.net.Uri
import android.text.Editable
import androidx.core.net.toUri
import io.legado.app.constant.AppPattern
import io.legado.app.constant.AppPattern.dataUriRegex
import java.io.File
import java.lang.Character.codePointCount
import java.lang.Character.offsetByCodePoints
import java.net.InetAddress
import java.util.Locale


fun String?.safeTrim() = if (this.isNullOrBlank()) null else this.trim()

fun String?.isContentScheme(): Boolean = this?.startsWith("content://") == true

fun String.toEditable(): Editable = Editable.Factory.getInstance().newEditable(this)

fun String.parseToUri(): Uri {
    return if (isUri()) this.toUri() else {
        Uri.fromFile(File(this))
    }
}

fun String?.isUri(): Boolean {
    this ?: return false
    return this.startsWith("file://", true) || isContentScheme()
}

fun String?.isAbsUrl() =
    this?.let {
        it.startsWith("http://", true) || it.startsWith("https://", true)
    } ?: false

fun String?.isDataUrl() =
    this?.let {
        dataUriRegex.matches(it)
    } ?: false

fun String?.isJson(): Boolean =
    this?.run {
        val str = this.trim()
        when {
            str.startsWith("{") && str.endsWith("}") -> true
            str.startsWith("[") && str.endsWith("]") -> true
            else -> false
        }
    } ?: false

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

fun String?.isXml(): Boolean =
    this?.run {
        val str = this.trim()
        str.startsWith("<") && str.endsWith(">")
    } ?: false

fun String?.isTrue(nullIsTrue: Boolean = false): Boolean {
    if (this.isNullOrBlank() || this == "null") {
        return nullIsTrue
    }
    return !this.trim().matches("(?i)^(?:false|no|not|0|0.0)$".toRegex())
}

fun String.isHex(): Boolean {
    return all {c ->
        c in '0'..'9' || c in 'A'..'F' || c in 'a'..'f'
    }
}

fun String.splitNotBlank(vararg delimiter: String, limit: Int = 0): Array<String> = run {
    this.split(*delimiter, limit = limit).map { it.trim() }.filterNot { it.isBlank() }
        .toTypedArray()
}

fun String.splitNotBlank(regex: Regex, limit: Int = 0): Array<String> = run {
    this.split(regex, limit).map { it.trim() }.filterNot { it.isBlank() }.toTypedArray()
}

@SuppressLint("ObsoleteSdkInt")
fun String.cnCompare(other: String): Int {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        Collator.getInstance(ULocale.SIMPLIFIED_CHINESE).compare(this, other)
    } else {
        java.text.Collator.getInstance(Locale.CHINA).compare(this, other)
    }
}

/**
 * 字符串所占内存大小
 */
fun String?.memorySize(): Int {
    this ?: return 0
    return 40 + 2 * length
}

/**
 * 是否中文
 */
fun String.isChinese(): Boolean {
    return chineseCharRegex.containsMatchIn(this)
}

private val chineseCharRegex = Regex("[\u4e00-\u9fa5]")

/**
 * 将字符串拆分为单个字符,包含emoji
 */
fun CharSequence.toStringArray(): Array<String> {
    var codePointIndex = 0
    return try {
        Array(codePointCount(this, 0, length)) {
            val start = codePointIndex
            codePointIndex = offsetByCodePoints(this, start, 1)
            substring(start, codePointIndex)
        }
    } catch (e: Exception) {
        split("").toTypedArray()
    }
}

fun String.escapeRegex(): String {
    return replace(AppPattern.regexCharRegex, "\\\\$0")
}

fun String.encodeURI(): String {
    val builder = StringBuilder(length)
    for (c in this) {
        if (c.code < 128 && encodeQuerySafeChars[c.code]) {
            builder.append(c)
        } else {
            val bytes = c.toString().toByteArray(Charsets.UTF_8)
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                builder.append('%')
                builder.append(ENCODE_HEX_UPPER[v ushr 4])
                builder.append(ENCODE_HEX_UPPER[v and 0x0F])
            }
        }
    }
    return builder.toString()
}

private const val ENCODE_HEX_UPPER = "0123456789ABCDEF"

private val encodeQuerySafeChars: BooleanArray = BooleanArray(128).apply {
    for (c in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_.-~!$&'()*+,;=:@/?") {
        this[c.code] = true
    }
}

fun String.normalizeFileName(): String {
    return replace(AppPattern.fileNameRegex2, "_")
}

/**
 * 将字符串加上转义,方便传递字符串到浏览器
 */
fun String.escapeForJs(): String {
    return this.replace("\\", "\\\\")
        .replace("\"", "\\\"").replace("'", "\\'")
        .replace("\n", "\\n").replace("\r", "\\r")
        .replace("\t", "\\t")
        .replace("\u2028", "\\u2028")
        .replace("\u2029", "\\u2029")
}

/**
 * 将ip字符串转为InetAddress
 */
fun String.parseIpsFromString(): List<InetAddress>? =
    split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { it.runCatching { InetAddress.getByName(this) }.getOrNull() }
        .takeIf { it.isNotEmpty() }


fun String.quoteReplacementJs(): String {
    if (!this.contains('\\')) {
        return this
    }
    val sb = StringBuilder()
    for (c in this) {
        if (c == '\\') {
            sb.append("\\\\")
        } else {
            sb.append(c)
        }
    }
    return sb.toString()
}