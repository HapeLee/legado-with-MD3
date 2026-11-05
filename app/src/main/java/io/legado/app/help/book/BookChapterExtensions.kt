package io.legado.app.help.book

import io.legado.app.data.entities.BookChapter
import io.legado.app.help.RuleBigDataHelp

/**
 * 视频章节的弹幕，与上游一致挂在 danmaku 变量上：
 * 先取章节变量（内存/DB），再取规则大数据落盘的内容。
 */
fun BookChapter.getDanmaku(): String? {
    return variableMap["danmaku"] ?: RuleBigDataHelp.getChapterVariable(bookUrl, url, "danmaku")
}

fun BookChapter.putDanmaku(value: String?) {
    putVariable("danmaku", value)
}
