package io.legado.app.help.book

/**
 * 一次图片缓存批次是否可视为章节图片缓存完成。
 * 纯函数，可供 JVM 单元测试直接验证，不依赖 Android 运行时。
 */
fun isChapterImageCacheComplete(failures: Int, filesCached: Boolean): Boolean {
    return failures == 0 && filesCached
}
