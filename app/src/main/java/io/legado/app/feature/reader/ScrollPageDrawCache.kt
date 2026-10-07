package io.legado.app.feature.reader

import android.graphics.Paint
import androidx.compose.runtime.mutableStateOf
import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import io.legado.app.feature.reader.core.model.textBackgroundRuns
import io.legado.app.feature.reader.core.style.ReaderBackgroundBand
import io.legado.app.feature.reader.core.style.mergeBackgroundBounds
import io.legado.app.feature.reader.platform.ReaderAndroidPaintFactory
import io.legado.app.feature.reader.platform.ReaderPageDecorationDrawCache

/**
 * 滚动模式单画布的每页绘制数据（对照 shutiao 的 page.textLayoutCache）。
 *
 * 普通类、组合外构建：窗口发布后由 effect 期预热，draw 期直读；位图类字段用
 * snapshot state 承载，加载完成只触发重绘不重组。
 */
internal class ScrollPageDrawData(val page: ReaderPage) {
    val textElements: List<ReaderElement.Text> = page.elements.filterIsInstance<ReaderElement.Text>()
    val paints: Map<ReaderTextStyle, Paint> =
        textElements.map { it.style }.distinct().associateWith(ReaderAndroidPaintFactory::create)
    val textBackgrounds = page.textBackgroundRuns()
    val textBackgroundBands: List<ReaderBackgroundBand> = textElements.mergeBackgroundBounds()
    // 背景图不许开抗锯齿：九宫格相邻两格各画一条半覆盖的边会合成出一条透底的切割线。
    val textBackgroundPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    val decorationDrawCache = ReaderPageDecorationDrawCache.create(page)

    /** Text-background bitmaps are owned by ReaderTextBackgroundLoader's byte-bounded LRU. */
    val textBackgroundRevision = mutableStateOf(0)
}

/**
 * 页 → 绘制数据缓存。key 取 elements 的**引用身份**：跨页换窗、分钟级页眉时间刷新
 * 都会生成新页实例但复用同一 elements 列表，缓存持续命中；只有真重排（新列表）才
 * 重建。超容量按 LRU 淘汰。
 */
internal class ScrollPageDrawCache(capacity: Int = 8) {
    /**
     * 按 elements 的引用而非内容比较。每次查询都会创建新的 key 包装对象，故必须
     * 显式实现引用相等；Object 默认实现会把包装对象本身作为身份，令缓存永远 miss。
     */
    private class ElementsKey(private val elements: List<ReaderElement>) {
        override fun equals(other: Any?): Boolean =
            other is ElementsKey && elements === other.elements

        override fun hashCode(): Int = System.identityHashCode(elements)
    }

    private val entries = object : LinkedHashMap<ElementsKey, ScrollPageDrawData>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ElementsKey, ScrollPageDrawData>): Boolean =
            size > capacity
    }

    fun put(page: ReaderPage, data: ScrollPageDrawData) {
        synchronized(this) { entries[ElementsKey(page.elements)] = data }
    }

    fun peek(page: ReaderPage): ScrollPageDrawData? = synchronized(this) {
        entries[ElementsKey(page.elements)]
    }

    /** draw 期兜底：miss 时主线程同步构建，保正确性不缺字；正常路径由预热先行。 */
    fun ensure(page: ReaderPage): ScrollPageDrawData =
        peek(page) ?: ScrollPageDrawData(page).also { put(page, it) }
}
