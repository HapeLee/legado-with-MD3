package io.legado.app.data.entities

/**
 * 阅读进度快照（WebDAV 上传/恢复用）。
 *
 * 注意：原 `constructor(book: Book)` 依赖未下沉的 [Book]，已抽到 Android 侧
 * 工厂扩展 `Book.toBookProgress()`（见 `BookProgressAndroid.kt`）。
 */
data class BookProgress(
    val name: String,
    val author: String,
    val durChapterIndex: Int,
    val durChapterPos: Int,
    val durChapterTime: Long,
    val durChapterTitle: String?
)
