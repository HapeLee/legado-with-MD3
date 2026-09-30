package io.legado.app.ui.widget.components.image.cover

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * 共享封面在**源页面**里的落点（window 坐标 px）与圆角。
 *
 * 书架↔阅读页的形变以它为起点。为什么不复用 Material3 的共享元素状态：那要求两端在同一刻
 * 都注册同一个 key，在书架上停留一会儿再返回时配对会失败，转场直接退化成场景级的交叉淡入淡出
 * （就是「返回动画还是旧的」）。这里只读**开书那一刻**缓存下来的矩形，返回动画由阅读页自己
 * 驱动，不依赖后一站还组合着。
 */
internal object BookCoverAnchorStore {

    data class Anchor(val rectPx: Rect, val cornerRadiusPx: Float)

    private const val MAX_ENTRIES = 64

    /**
     * 普通 map：只在布局回调里写，读的一侧只在开书那一刻取一次，没必要进快照系统——
     * 进了反而会让每帧重绘去查一张被 Compose 盯着的表。
     */
    private val anchors = LinkedHashMap<String, Anchor>()

    fun report(key: String, rectPx: Rect, cornerRadiusPx: Float) {
        if (rectPx.width <= 0f || rectPx.height <= 0f) return
        synchronized(this) {
            anchors[key] = Anchor(rectPx, cornerRadiusPx.coerceAtLeast(0f))
            while (anchors.size > MAX_ENTRIES) {
                anchors.remove(anchors.keys.first())
            }
        }
    }

    fun peek(key: String?): Anchor? =
        key?.let { k -> synchronized(this) { anchors[k] } }
}

/**
 * 封面把自己的位置上报给形变（书架、详情页、搜索页共用这一条）。
 *
 * [cornerRadius] 是这一格自己裁的圆角。`BookCoverImage` 只画内容、圆角由外层裁，所以它上报
 * 源页面定格时缓存的那一份，外层 `CoilBookCover` 会在同一趟布局里用真实圆角覆盖掉。
 */
@Composable
internal fun Modifier.bookCoverMorphAnchor(
    sharedCoverKey: String?,
    cornerRadius: Dp,
): Modifier {
    if (sharedCoverKey == null) return this
    val radiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
    return onGloballyPositioned { coords ->
        BookCoverAnchorStore.report(sharedCoverKey, coords.boundsInWindow(), radiusPx)
    }
}
