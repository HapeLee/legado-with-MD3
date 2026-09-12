package io.legado.app.feature.reader.core.model

/**
 * 滚动模式内容裁剪的外扩量。
 *
 * 对照旧 View `ChapterProvider` 的 `visibleRect`：可见矩形按
 * `shadowPad = 阴影半径 + 2`（`Build.VERSION_CODES.Q` 及以上的 `shadowLayerRadius`）与
 * `italicPad = 字号 * 0.25`（仅斜体）向外扩，否则贴边裁切会把文字阴影和斜体字缘切掉。
 * 取页内所有文字元素的最大值，等价于旧版那个全局 Paint 的取值在混排样式下的推广。
 */
val ReaderPage.contentClipPadPx: Float
    get() {
        var pad = 0f
        elements.forEach { element ->
            val text = element as? ReaderElement.Text ?: return@forEach
            text.style.shadow?.let { shadow ->
                pad = maxOf(pad, shadow.radiusPx.coerceAtLeast(0f) + SHADOW_RADIUS_PADDING_PX)
            }
            if (text.style.italic) {
                pad = maxOf(pad, text.style.fontSizePx * ITALIC_PAD_RATIO)
            }
        }
        return pad
    }

private const val SHADOW_RADIUS_PADDING_PX = 2f
private const val ITALIC_PAD_RATIO = 0.25f
