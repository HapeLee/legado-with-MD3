package io.legado.app.ui.book.read.page

/**
 * 自定义页眉/页脚模板里 `{PageRemaining}` 的取值。
 *
 * **刻意只出数字，剩 0 页也不换成「本章完」**：模板是用户自己拼的，占位符两侧往往还有
 * 自己写的文案（「剩 {PageRemaining} 页」），中途把数字换成一句话会让整行读不通。
 * 要「本章完」这种语义，用户在模板里自己写即可。
 *
 * 唯一的非数字取值是 `-`：章节还没排完且页数未知，此时余量无从算起。
 */
internal fun formatCustomTipPageRemaining(
    pageSize: Int,
    pageIndex: Int,
    isChapterCompleted: Boolean,
): String {
    if (!isChapterCompleted && pageSize <= 0) return "-"
    return (pageSize - pageIndex - 1).coerceAtLeast(0).toString()
}
