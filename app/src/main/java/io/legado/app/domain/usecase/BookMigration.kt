package io.legado.app.domain.usecase

import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.removeType

/**
 * 把 this（源书籍）携带的数据搬到 [target]。
 *
 * 换源、批量换源、加入书架时的迁移共用同一份实现，保证「同一组开关在三个入口下含义一致」。
 * 只搬运 [options] 勾选的部分，未勾选的字段由 [target] 自身保持（通常是新源刚解析出来的值）。
 *
 * @param chapters 目标书籍的目录，用于按章节重算阅读进度
 * @param options 用户在换源/共存选项中勾选的数据项
 */
internal fun Book.migrateInto(
    target: Book,
    chapters: List<BookChapter>,
    options: ChangeSourceMigrationOptions,
    defaultReplaceEnabled: Boolean,
    chineseConverterType: Int,
) {
    target.totalChapterNum = chapters.size
    if (options.migrateReadingProgress && chapters.isNotEmpty()) {
        target.durChapterIndex = BookHelp
            .getDurChapter(durChapterIndex, durChapterTitle, chapters, totalChapterNum)
            .coerceIn(0, chapters.lastIndex)
        target.durChapterTitle = chapters[target.durChapterIndex].getDisplayTitle(
            ContentProcessor.get(target.name, target.origin).getTitleReplaceRules(),
            getUseReplaceRule(defaultReplaceEnabled),
            chineseConverterType = chineseConverterType,
        )
        target.durChapterPos = durChapterPos
        target.durChapterTime = durChapterTime
    } else {
        target.durChapterIndex = 0
        target.durChapterTitle = chapters.firstOrNull()?.getDisplayTitle(
            ContentProcessor.get(target.name, target.origin).getTitleReplaceRules(),
            getUseReplaceRule(defaultReplaceEnabled),
            chineseConverterType = chineseConverterType,
        )
        target.durChapterPos = 0
        target.durChapterTime = System.currentTimeMillis()
    }
    if (options.migrateGroup) {
        target.group = group
        target.order = order
    }
    if (options.migrateCover) {
        target.customCoverUrl = customCoverUrl
    }
    if (options.migrateCategory) {
        target.customTag = customTag
    }
    if (options.migrateRemark) {
        target.customIntro = customIntro
        target.remark = remark
    }
    target.canUpdate = canUpdate
    if (config.fixedType) {
        target.type = type
    }
    if (options.migrateReadConfig) {
        target.readConfig = readConfig
    }
    if (target.wordCount.isNullOrBlank()) {
        target.wordCount = wordCount
    }
    target.removeType(BookType.updateError)
}

/**
 * 把源书籍的数据搬到 [target]，且不改变 [target] 自身元数据。
 *
 * 用于「共存」：两本书都留在书架里，因此搬运只能是从已有作品复制到新书，不能反过来改旧书，
 * 也不重算阅读进度所依赖的章节数（新书还没有旧书的目录对应关系）。
 */
internal fun Book.copyMigratableFieldsTo(
    target: Book,
    options: ChangeSourceMigrationOptions,
) {
    if (options.migrateGroup) {
        target.group = group
    }
    if (options.migrateCover) {
        target.customCoverUrl = customCoverUrl
    }
    if (options.migrateCategory) {
        target.customTag = customTag
    }
    if (options.migrateRemark) {
        target.customIntro = customIntro
        target.remark = remark
    }
    if (options.migrateReadConfig) {
        target.readConfig = readConfig
    }
    if (options.migrateReadingProgress) {
        target.durChapterIndex = durChapterIndex
        target.durChapterTitle = durChapterTitle
        target.durChapterPos = durChapterPos
        target.durChapterTime = durChapterTime
    }
    if (target.wordCount.isNullOrBlank()) {
        target.wordCount = wordCount
    }
}
