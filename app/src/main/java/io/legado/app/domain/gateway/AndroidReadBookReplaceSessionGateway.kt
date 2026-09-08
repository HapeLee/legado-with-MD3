package io.legado.app.domain.gateway

import io.legado.app.data.entities.getUseReplaceRule
import io.legado.app.model.ReadBook

/**
 * [ReadBookReplaceSessionGateway] 的 Android 实现：`ReadBook` 是 `:app` 侧的阅读器运行时
 * 单例，无法下沉，因此只把它的替换相关能力以契约形式暴露出去。
 *
 * 每个方法都与迁移前 `ReplaceRuleViewModel` 直接调用 `ReadBook` 的写法逐条对应，
 * 包括「无书时 `setReSegment` 静默跳过、但 `loadContent` 仍会执行」这类细节——
 * 改一条就改了用户可见行为。
 */
class AndroidReadBookReplaceSessionGateway(
    private val otherSettingsGateway: OtherSettingsGateway,
) : ReadBookReplaceSessionGateway {

    override fun snapshot(): ReadBookReplaceSnapshot? {
        val book = ReadBook.book ?: return null
        val chapterInput = ReadBook.readerChapterInputWindow.current
        return ReadBookReplaceSnapshot(
            bookUrl = book.bookUrl,
            chapterIndex = ReadBook.durChapterIndex,
            useReplaceRule = book.getUseReplaceRule(
                otherSettingsGateway.currentSettings.replaceEnableDefault
            ),
            reSegment = book.getReSegment(),
            effectiveReplaceRules = chapterInput?.content?.effectiveReplaceRules.orEmpty(),
        )
    }

    override fun setUseReplaceRule(enabled: Boolean) {
        ReadBook.book?.setUseReplaceRule(enabled)
    }

    override fun setReSegment(enabled: Boolean) {
        ReadBook.book?.setReSegment(enabled)
    }

    override fun saveRead() {
        ReadBook.saveRead()
    }

    override fun loadContent(resetPageOffset: Boolean) {
        ReadBook.loadContent(resetPageOffset)
    }
}
