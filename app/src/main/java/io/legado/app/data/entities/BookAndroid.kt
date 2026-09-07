package io.legado.app.data.entities

import io.legado.app.constant.PageAnim
import io.legado.app.data.appDb
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.applyTagGroupRulesForBook
import io.legado.app.help.book.getFolderNameNoCache
import io.legado.app.help.book.isEpub
import io.legado.app.help.book.isImage
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ReadBook
import kotlinx.coroutines.runBlocking
import java.nio.charset.Charset
import java.time.LocalDate
import kotlin.math.max

/**
 * `Book` 实体已下沉到 `:core:data` 的 commonMain，但它有一批方法依赖 Android/JVM
 * 平台栈（`java.nio.charset`、`java.time.LocalDate`、`appDb`、`ReadBook`、
 * `ReadBookConfig`、`BookHelp`/`ContentProcessor`、未下沉的 `SearchBook`）。
 *
 * 这些方法原样抽成同名扩展保留在 `:app`，语义与调用方式不变；与
 * `BookGroupAndroid.kt`、`DictRuleAndroid.kt`、`ServerAndroid.kt` 同一模式。
 * 调用方需显式 import（跨模块且通常不在同包）。
 */

fun Book.fileCharset(): Charset {
    return charset(charset ?: "UTF-8")
}

fun Book.getUnreadChapterNum() = max(simulatedTotalChapterNum() - durChapterIndex - 1, 0)

fun Book.getFolderName(): String {
    folderName?.let {
        return it
    }
    //防止书名过长,只取9位
    folderName = getFolderNameNoCache()
    return folderName!!
}

fun Book.toSearchBook() = SearchBook(
    name = name,
    author = author,
    kind = kind,
    bookUrl = bookUrl,
    origin = origin,
    originName = originName,
    type = type,
    wordCount = wordCount,
    latestChapterTitle = latestChapterTitle,
    coverUrl = coverUrl,
    intro = intro,
    tocUrl = tocUrl,
    originOrder = originOrder,
    variable = variable
).apply {
    this.infoHtml = this@toSearchBook.infoHtml
    this.tocHtml = this@toSearchBook.tocHtml
}

/**
 * 迁移旧的书籍的一些信息到新的书籍中
 */
fun Book.migrateTo(
    newBook: Book,
    toc: List<BookChapter>,
    defaultReplaceEnabled: Boolean,
    chineseConverterType: Int,
): Book {
    newBook.durChapterIndex = BookHelp
        .getDurChapter(durChapterIndex, durChapterTitle, toc, totalChapterNum)
    newBook.durChapterTitle = toc[newBook.durChapterIndex].getDisplayTitle(
        ContentProcessor.get(newBook.name, newBook.origin).getTitleReplaceRules(),
        getUseReplaceRule(defaultReplaceEnabled),
        chineseConverterType = chineseConverterType,
    )
    newBook.durChapterPos = durChapterPos
    newBook.durChapterTime = durChapterTime
    newBook.group = group
    newBook.order = order
    newBook.customCoverUrl = customCoverUrl
    newBook.customIntro = customIntro
    newBook.customTag = customTag
    newBook.canUpdate = canUpdate
    if (config.fixedType) {
        newBook.type = type
    }
    newBook.readConfig = readConfig
    if (newBook.wordCount.isNullOrBlank()) {
        newBook.wordCount = wordCount
    }
    return newBook
}

fun Book.save() {
    applyTagGroupRulesForBook(this)
    runBlocking {
        if (appDb.bookDao.has(bookUrl)) {
            appDb.bookDao.update(this@save)
        } else {
            appDb.bookDao.insert(this@save)
        }
    }
}

fun Book.delete() {
    if (ReadBook.isCurrentBook(bookUrl)) {
        ReadBook.clearCurrentBook()
    }
    runBlocking {
        appDb.bookChapterDao.delByBook(bookUrl)
        appDb.bookDao.delete(this@delete)
    }
}

fun Book.getPageAnim(): Int {
    var pageAnim = config.pageAnim
        ?: if (type and io.legado.app.constant.BookType.image > 0) {
            PageAnim.scrollPageAnim
        } else {
            ReadBookConfig.pageAnim
        }
    if (pageAnim < 0) {
        pageAnim = ReadBookConfig.pageAnim
    }
    return pageAnim
}

fun Book.getUseReplaceRule(defaultReplaceEnabled: Boolean): Boolean {
    val useReplaceRule = config.useReplaceRule
    if (useReplaceRule != null) {
        return useReplaceRule
    }
    //图片类书源 epub本地 默认关闭净化
    if (isImage || isEpub) {
        return false
    }
    return defaultReplaceEnabled
}

fun Book.setStartDate(startDate: LocalDate?) {
    config.startDate = startDate?.toString()
}

fun Book.getStartDate(): LocalDate? {
    if (!config.readSimulating || config.startDate == null) {
        return LocalDate.now()
    }
    return try {
        LocalDate.parse(config.startDate)
    } catch (e: Exception) {
        println("解析日期失败: ${config.startDate}, 错误: ${e.message}")
        LocalDate.now()
    }
}
