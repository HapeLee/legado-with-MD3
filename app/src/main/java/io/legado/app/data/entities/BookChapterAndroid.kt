package io.legado.app.data.entities

import android.annotation.SuppressLint
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.exception.RegexTimeoutException
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.ChineseUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.replace
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import splitties.init.appCtx

/**
 * `BookChapter` 实体已下沉到 `:core:data`，但它有 4 个方法依赖 Android/JVM 平台栈：
 * 中文繁简转换、Room 写操作、UI toast、AnalyzeUrl/NetworkUtils、MD5 文件名。
 *
 * 原样抽成同名扩展保留在 `:app`；调用方需显式 import。模式同 `BookAndroid.kt`。
 */

fun BookChapter.getDisplayTitle(
    replaceRules: List<ReplaceRule>? = null,
    useReplace: Boolean = true,
    chineseConvert: Boolean = true,
    chineseConverterType: Int,
): String {
    var displayTitle = title.replace(AppPattern.rnRegex, "")
    if (chineseConvert) {
        when (chineseConverterType) {
            1 -> displayTitle = ChineseUtils.t2s(displayTitle)
            2 -> displayTitle = ChineseUtils.s2t(displayTitle)
        }
    }
    if (useReplace && replaceRules != null) kotlin.run {
        replaceRules.forEach { item ->
            if (item.pattern.isNotEmpty()) {
                try {
                    val mDisplayTitle = if (item.isRegex) {
                        displayTitle.replace(
                            item.regex,
                            item.replacement,
                            item.getValidTimeoutMillisecond()
                        )
                    } else {
                        displayTitle.replace(item.pattern, item.replacement)
                    }
                    if (mDisplayTitle.isNotBlank()) {
                        displayTitle = mDisplayTitle
                    }
                } catch (e: RegexTimeoutException) {
                    item.isEnabled = false
                    runBlocking { appDb.replaceRuleDao.update(item) }
                } catch (e: CancellationException) {
                    return@run
                } catch (e: Exception) {
                    AppLog.put("${item.name}替换出错\n替换内容\n${displayTitle}", e)
                    appCtx.toastOnUi("${item.name}替换出错")
                }
            }
        }
    }
    return displayTitle
}

fun BookChapter.getAbsoluteURL(): String {
    //二级目录解析的卷链接为空 返回目录页的链接
    if (url.startsWith(title) && isVolume) return baseUrl
    val urlMatch = AnalyzeUrl.paramPattern.find(url)
    val urlBefore = if (urlMatch != null) url.substring(0, urlMatch.range.first) else url
    val urlAbsoluteBefore = NetworkUtils.getAbsoluteURL(baseUrl, urlBefore)
    return if (urlMatch == null) {
        urlAbsoluteBefore
    } else {
        "$urlAbsoluteBefore," + url.substring(urlMatch.range.last + 1)
    }
}

@SuppressLint("DefaultLocale")
fun BookChapter.getFileName(suffix: String = "nb"): String {
    ensureTitleMD5Init()
    return String.format("%05d-%s.%s", index, titleMD5, suffix)
}

@SuppressLint("DefaultLocale")
@Suppress("unused")
fun BookChapter.getFontName(): String {
    ensureTitleMD5Init()
    return String.format("%05d-%s.ttf", index, titleMD5)
}

private fun BookChapter.ensureTitleMD5Init() {
    if (titleMD5 == null) {
        titleMD5 = MD5Utils.md5Encode16(title)
    }
}
