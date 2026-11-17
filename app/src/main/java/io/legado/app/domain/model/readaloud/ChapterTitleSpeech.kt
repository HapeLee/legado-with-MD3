package io.legado.app.domain.model.readaloud

/**
 * 本次章节准备要不要把标题作为第一句送进朗读队列。
 *
 * 三个来源必须一致，否则会出现「正文没标题、朗读却念标题」这种自相矛盾的表现：
 * - [readTitleEnabled]：朗读设置里的「朗读章节标题」，与阅读器是否显示标题无关；
 * - [hasReadableParagraphs]：只有卷名、没有可读正文的章节，标题就是这一章唯一的内容，
 *   任何设置下都要念，否则这一章会静默跳过；
 * - [startsAtChapterBeginning] / [moveToLast]：从章中间恢复进度（或直接跳到章末）时，
 *   本来就不在章首，插一条标题 cue 会把游标顶回开头。
 */
fun shouldSpeakChapterTitle(
    readTitleEnabled: Boolean,
    title: String,
    hasReadableParagraphs: Boolean,
    startsAtChapterBeginning: Boolean,
    moveToLast: Boolean = false,
): Boolean {
    if (title.isBlank()) return false
    if (!hasReadableParagraphs) return true
    if (moveToLast || !startsAtChapterBeginning) return false
    return readTitleEnabled
}
