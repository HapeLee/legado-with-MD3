package io.legado.app.data.entities

/**
 * [BookProgress] 的 Android 特有扩展：从 [Book] 构造进度快照。
 *
 * 实体本体已下沉 :core:data，但 [Book] 尚未下沉（依赖 BookHelp/ContentProcessor/
 * ReadBook 等平台栈），故工厂函数作为 Android 侧扩展保留。
 */
fun Book.toBookProgress(): BookProgress = BookProgress(
    name = name,
    author = author,
    durChapterIndex = durChapterIndex,
    durChapterPos = durChapterPos,
    durChapterTime = durChapterTime,
    durChapterTitle = durChapterTitle
)
