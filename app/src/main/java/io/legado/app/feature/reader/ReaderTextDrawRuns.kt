package io.legado.app.feature.reader

import android.graphics.Canvas
import android.graphics.Paint
import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import java.util.IdentityHashMap
import kotlin.math.abs

/**
 * 一行里连续、同样式、字距一致的文本元素合成的一段正文。
 *
 * 旧 View 版把这种行交给 `TextLine.fastDrawTextLine` 整行画一次，其余情况按字逐个画。
 * 本版本原先一律按字逐个画。合并成一次 `drawText` 的意义在于两端对齐摊出来的字距由
 * 画笔一次插入，标点前后的空隙因此更均匀；按字逐个画时每个字形都按排版阶段算好的
 * 坐标单独定位。
 */
internal class ReaderTextDrawRun(
    /** 这一行的第一个字。绘制时用它判断当前元素是不是本次要画的那一段。 */
    val first: ReaderElement.Text,
    val style: ReaderTextStyle,
    val text: String,
    val baselinePx: Float,
    val startLeftPx: Float,
    /** 相邻两个字之间比字形宽度多出来的距离，两端对齐的余量和字距设置都在里面。 */
    val gapExtraPx: Float,
    val underline: Boolean,
)

/** 字距相差不到这个值仍视为一致，容下排版阶段浮点累加的误差。 */
private const val GAP_TOLERANCE_PX = 0.01f

/**
 * 找出可以整段绘制的文本元素。返回的 map 覆盖段内每个元素，绘制时只在
 * [ReaderTextDrawRun.first] 上画一次，其余元素跳过。
 *
 * 用引用身份做键：元素是数据类，按值哈希要逐层算边界和样式，而这里比的是同一批
 * 元素实例。
 */
internal fun buildMergedTextRuns(
    elements: List<ReaderElement>,
): Map<ReaderElement.Text, ReaderTextDrawRun> {
    val runs = IdentityHashMap<ReaderElement.Text, ReaderTextDrawRun>()
    var index = 0
    while (index < elements.size) {
        val first = elements[index] as? ReaderElement.Text
        if (first == null || !first.drawsInOneRun()) {
            index++
            continue
        }
        val text = StringBuilder(first.value)
        var gapExtraPx: Float? = null
        var next = index + 1
        while (next < elements.size) {
            val candidate = elements[next] as? ReaderElement.Text ?: break
            val previous = elements[next - 1] as? ReaderElement.Text ?: break
            if (!candidate.drawsInOneRun() || candidate.style != first.style) break
            // 同一行的字共用基线和行盒顶边，跨行、跨栏、跨段落都在这里断开。
            if (candidate.baselinePx != first.baselinePx || candidate.bounds.top != first.bounds.top) break
            val gap = candidate.bounds.left - previous.bounds.right
            val uniformGap = gapExtraPx
            if (uniformGap == null) {
                gapExtraPx = gap
            } else if (abs(gap - uniformGap) > GAP_TOLERANCE_PX) {
                // 字距不一致说明这段缝隙不是同一种排版结果，例如两端对齐余量只落在
                // 某些缝上，或中间夹了拉丁文空格的额外间距。切开后各段仍按自己的
                // 字距整段画。
                break
            }
            text.append(candidate.value)
            next++
        }
        if (next - index > 1) {
            val run = ReaderTextDrawRun(
                first = first,
                style = first.style,
                text = text.toString(),
                baselinePx = first.baselinePx,
                startLeftPx = first.bounds.left,
                gapExtraPx = gapExtraPx ?: 0f,
                underline = first.style.nativeUnderline && first.style.underline == null,
            )
            for (member in index until next) {
                runs[elements[member] as ReaderElement.Text] = run
            }
            index = next
        } else {
            index++
        }
    }
    return runs
}

/** 带链接、样式预览下划线、背景图这类装饰的字要按元素单独定位，不参与合并。 */
private fun ReaderElement.Text.drawsInOneRun(): Boolean =
    link == null &&
        emphasisUnderline == null &&
        style.backgroundImage == null &&
        backgroundFrameTopPx == 0f &&
        backgroundFrameBottomPx == 0f &&
        !continuesBackgroundRun

/**
 * 整段画一次。[scratchPaint] 由调用方持有并复用，用来避免改动 [stylePaint] 上的字距。
 */
internal fun drawMergedTextRun(
    canvas: Canvas,
    run: ReaderTextDrawRun,
    stylePaint: Paint,
    colorArgb: Int,
    scratchPaint: Paint,
) {
    scratchPaint.set(stylePaint)
    scratchPaint.color = colorArgb
    scratchPaint.isUnderlineText = run.underline
    if (run.gapExtraPx == 0f) {
        scratchPaint.letterSpacing = 0f
        canvas.drawText(run.text, run.startLeftPx, run.baselinePx, scratchPaint)
        return
    }
    scratchPaint.letterSpacing = run.gapExtraPx / scratchPaint.textSize
    // 与旧 View `TextLine.fastDrawTextLine` 的 `extraLetterSpacingOffsetX = -d / 2` 一致，
    // 起点左移半个字距，把画笔在字后插入的间距均摊到这一段的两端。
    canvas.drawText(
        run.text,
        0,
        run.text.length,
        run.startLeftPx - run.gapExtraPx / 2f,
        run.baselinePx,
        scratchPaint,
    )
}
