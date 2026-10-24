package io.legado.app.ui.book.manga

import androidx.compose.runtime.Immutable
import kotlin.math.floor
import kotlin.math.roundToInt

/** 条漫列表的最大捏合放大倍数；分段上限按它预留余量。 */
internal const val MAX_WEBTOON_ZOOM = 3f

/**
 * 当前 Compose 的 Constraints 无法表示任意大的尺寸：宽度与高度占用的位数之和超过 31 即抛
 * IllegalArgumentException（BitsNeedForSize 阶梯：>=262143 的维度直接视为不可表示，见 #2475）。
 * 返回该宽度下高度还能安全使用的最大值（含 fixed 场景 min==max）。
 */
internal fun webtoonSlotHeightCap(width: Int): Int = when {
    width < 8_191 -> 262_142
    width < 32_767 -> 65_534
    width < 65_535 -> 32_766
    width < 262_143 -> 8_190
    else -> 1
}

/**
 * 条漫列表的一行。普通页与章节过渡独占一行；单张显示高度超过上限的长条图拆成多段连续行，
 * 各行共同平铺整张图。未拆分页保持行键等于页键，现有锚点/保位逻辑零变化。
 */
@Immutable
internal data class MangaWebtoonRow(
    val key: String,
    /** 对应 state.pages 的下标。 */
    val itemIndex: Int,
    /** 所属页键；过渡/边界等非页行为 null。 */
    val pageKey: String?,
    val segmentIndex: Int?,
    val segmentCount: Int?,
)

/**
 * 单张图片在显示高度 [displayHeight]（未放大）时需要的分段数。
 * 每段预留 [MAX_WEBTOON_ZOOM] 倍放大与 +1px 重叠余量，保证放大后单段仍可表示，
 * 且缩放只改段高、不改段数，行身份在捏合过程中保持稳定。
 */
internal fun webtoonSegmentCount(displayHeight: Int, width: Int): Int {
    if (displayHeight <= 1) return 1
    val cap = ((webtoonSlotHeightCap(width) - 1) / MAX_WEBTOON_ZOOM).toInt()
    if (cap <= 0) return 1
    return ((displayHeight + cap - 1) / cap).coerceAtLeast(1)
}

internal fun webtoonSegmentStart(totalHeight: Int, segmentIndex: Int, segmentCount: Int): Int =
    (totalHeight.toLong() * segmentIndex / segmentCount).toInt()

internal fun webtoonSegmentEnd(totalHeight: Int, segmentIndex: Int, segmentCount: Int): Int =
    (totalHeight.toLong() * (segmentIndex + 1) / segmentCount).toInt()

/** 页在行列表中的首行（未拆分即自身）。 */
internal fun List<MangaWebtoonRow>.webtoonRowIndexOfPageTop(pageIndex: Int): Int =
    indexOfFirst { it.itemIndex == pageIndex && (it.segmentIndex == null || it.segmentIndex == 0) }

/**
 * 把“页内内容偏移”换算成滚动目标（行下标, 行内偏移）。分段行按当前总高平铺区间定位；
 * 偏移保持原值（可为负——条目顶在视口下方时的锚点语义），越界由 LazyColumn 自行钳制。
 */
internal fun webtoonRowScrollTarget(
    rows: List<MangaWebtoonRow>,
    pageKey: String,
    pageY: Int,
    totalHeight: Int,
): Pair<Int, Int>? {
    val pageRows = rows.withIndex().filter { it.value.pageKey == pageKey }
    val first = pageRows.firstOrNull() ?: return null
    val row = first.value
    val count = row.segmentCount
    if (row.segmentIndex == null || count == null) return first.index to pageY
    var chosen = first
    for (candidate in pageRows) {
        val segmentIndex = candidate.value.segmentIndex ?: continue
        if (pageY >= webtoonSegmentStart(totalHeight, segmentIndex, count)) {
            chosen = candidate
        } else break
    }
    val chosenIndex = chosen.value.segmentIndex ?: row.segmentIndex
    val start = webtoonSegmentStart(totalHeight, chosenIndex, count)
    return chosen.index to (pageY - start)
}

/**
 * 行高重排（宽高比到达或刷新）后的保位目标。
 * [anchorRowKey] 是旧行键，[anchorOffset] 为视口顶进入该行的距离（正数）；
 * 未拆分页等价于旧行为（目标行键即页键、偏移不变），拆分页按页内内容偏移落到新行。
 */
internal fun webtoonResizeScrollTarget(
    rowsBefore: List<MangaWebtoonRow>,
    rowsAfter: List<MangaWebtoonRow>,
    anchorRowKey: String,
    anchorOffset: Int,
    totalBefore: Int,
    totalAfter: Int,
): Pair<Int, Int>? {
    val before = rowsBefore.firstOrNull { it.key == anchorRowKey } ?: return null
    val pageKey = before.pageKey
    if (pageKey == null) {
        val index = rowsAfter.indexOfFirst { it.key == anchorRowKey }
        return if (index >= 0) index to anchorOffset else null
    }
    val segStartBefore = before.segmentIndex
        ?.let { webtoonSegmentStart(totalBefore, it, before.segmentCount ?: 1) } ?: 0
    // 偏移保持原值：负数（条目顶在视口下方）与比例首达（totalBefore == 0，旧行只是兜底高度）
    // 都沿用旧行为，由 LazyColumn 钳制最终滚动位置。
    val pageY = segStartBefore + anchorOffset
    return webtoonRowScrollTarget(rowsAfter, pageKey, pageY, totalAfter)
}

/**
 * 条漫列表行集合：高度未知（尚未加载）的页、章节过渡与边界各占一行；
 * 已知比例且显示高度超过分段上限的页拆成多段。分段判定只依赖未放大宽度，与缩放无关。
 */
internal fun buildMangaWebtoonRows(
    items: List<MangaReaderItemUi>,
    aspectRatios: Map<String, Float>,
    itemWidthPx: Float,
): List<MangaWebtoonRow> = buildList {
    items.forEachIndexed { index, item ->
        if (item !is MangaReaderItemUi.Page) {
            add(MangaWebtoonRow(item.key, index, null, null, null))
            return@forEachIndexed
        }
        val ratio = aspectRatios[item.key]?.takeIf { it.isFinite() && it > 0f }
        val segments = if (ratio != null && itemWidthPx > 0f) {
            webtoonSegmentCount(
                floor(itemWidthPx / ratio).toInt().coerceAtLeast(1),
                itemWidthPx.roundToInt().coerceAtLeast(1),
            )
        } else 1
        if (segments <= 1) {
            add(MangaWebtoonRow(item.key, index, item.key, null, null))
        } else {
            repeat(segments) { segment ->
                add(MangaWebtoonRow("${item.key}#seg$segment", index, item.key, segment, segments))
            }
        }
    }
}
