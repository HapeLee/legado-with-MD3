package io.legado.app.help.book

/**
 * 一次图片缓存批次是否可视为章节图片缓存完成。
 * 纯函数，可供 JVM 单元测试直接验证，不依赖 Android 运行时。
 */
fun isChapterImageCacheComplete(failures: Int, filesCached: Boolean): Boolean {
    return failures == 0 && filesCached
}

/**
 * 段评气泡地址（`dp:<数量>,{...}` 原生气泡 / `bubble://paragraph?...` 渲染层占位）是排版时
 * 现算的位图，没有可下载的远程资源。缓存链路必须在发起下载前跳过它们，否则下载必然失败，
 * 整章被判「图片缓存未完成」，目录/缓存管理显示缓存失败（#2593）。
 *
 * 纯前缀判定，不读取气泡设置，可供 JVM 单元测试直接验证；需要跟随气泡设置变化的判定
 * 仍由 `ParagraphBubbleRenderer.isBubbleSrc` 负责。
 */
fun isParagraphBubbleImageSrc(src: String): Boolean {
    val value = src.trim()
    return value.startsWith("dp:", ignoreCase = true) ||
        value.startsWith("bubble://", ignoreCase = true)
}
