package io.legado.app.core.ui.morph

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import java.util.concurrent.ConcurrentHashMap

/**
 * 记录图书封面在全局坐标中的锚点信息，用于无缝 Morph 展开到阅读器、漫画或有声书界面。
 */
@Immutable
data class BookCoverMorphAnchor(
    val bounds: Rect,
    val cornerRadiusPx: Float,
    val bookName: String?,
    val author: String?,
    val coverPath: String?,
    val sourceOrigin: String? = null,
    val bookUrl: String? = null,
    val badgeText: String? = null,
    val showBadgeDot: Boolean = false,
    val leftBottomText: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

object BookCoverMorphAnchors {
    private val anchors = ConcurrentHashMap<String, BookCoverMorphAnchor>()

    var activeMorphKey by mutableStateOf<String?>(null)
    var activeMorphState by mutableStateOf<BookMorphState?>(null)

    fun setActiveMorph(key: String?, state: BookMorphState?) {
        activeMorphKey = key
        activeMorphState = state
    }

    fun clearActiveMorph(key: String?) {
        if (key == null || isMorphing(key)) {
            activeMorphKey = null
            activeMorphState = null
        }
    }

    fun clearActiveMorphKey(key: String?) {
        clearActiveMorph(key)
    }

    fun isMorphing(key: String?): Boolean {
        val active = activeMorphKey ?: return false
        if (key.isNullOrBlank()) return false
        if (key == active) return true
        val cleanActive = active.removePrefix("book-cover:")
        val cleanKey = key.removePrefix("book-cover:")
        return cleanActive == cleanKey ||
                cleanActive.endsWith(":$cleanKey") ||
                cleanKey.endsWith(":$cleanActive")
    }

    /**
     * 判断指定书源/书架封面当前是否应当隐藏（让位给飞行封面）。
     *
     * 参考播放悬浮胶囊方案：在 graphicsLayer 内动态读取动画进度 progress.value。
     * - 动画进行中 (progress > 0.001f)：源封面隐藏 (alpha = 0f)，避免双封面叠影；
     * - 动画归零收回 (progress <= 0.001f)：源封面在同一绘制帧瞬间显现 (alpha = 1f)，
     *   与飞行封面隐去实现零延迟接力，彻底消除等待 onDispose/popBackStack 导致的空白跳闪。
     */
    fun isOriginCoverHidden(key: String?): Boolean {
        if (!isMorphing(key)) return false
        val state = activeMorphState ?: return true
        return state.progress.value > 0.001f
    }

    fun report(
        key: String?,
        bounds: Rect,
        cornerRadiusPx: Float,
        bookName: String? = null,
        author: String? = null,
        coverPath: String? = null,
        sourceOrigin: String? = null,
        bookUrl: String? = null,
        badgeText: String? = null,
        showBadgeDot: Boolean = false,
        leftBottomText: String? = null,
    ) {
        if (key.isNullOrBlank() || bounds.isEmpty) return
        anchors[key] = BookCoverMorphAnchor(
            bounds = bounds,
            cornerRadiusPx = cornerRadiusPx,
            bookName = bookName,
            author = author,
            coverPath = coverPath,
            sourceOrigin = sourceOrigin,
            bookUrl = bookUrl,
            badgeText = badgeText,
            showBadgeDot = showBadgeDot,
            leftBottomText = leftBottomText,
        )
        // 限制最大缓存容量，保留最近活跃的封面锚点
        if (anchors.size > 80) {
            val oldest = anchors.entries.minByOrNull { it.value.timestamp }?.key
            if (oldest != null) anchors.remove(oldest)
        }
    }

    fun get(key: String?): BookCoverMorphAnchor? {
        if (key.isNullOrBlank()) return null
        val direct = anchors[key]
        if (direct != null) return direct
        // 兜底策略：若键名包含 bookUrl，允许部分匹配
        return anchors.entries.firstOrNull { it.key.contains(key) || key.contains(it.key) }?.value
    }

    fun remove(key: String?) {
        if (key != null) anchors.remove(key)
    }
}
