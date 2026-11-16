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
 * 排版阶段已经把字距写进每个元素的 `bounds.left`（按 `字形宽度 + gap` 累加），逐字
 * 绘制时字形按这些坐标定位。合成一次 `drawText` 让画笔统一做 shaping、kerning 和
 * 连字，同时用 `Paint.letterSpacing` 复现同一份字距；起点不能再偏移，否则会与仍按
 * `bounds` 绘制的选区、背景带、下划线错位。
 */
internal class ReaderTextDrawRun(
    /** 这一行的第一个字。绘制时用它判断当前元素是不是本次要画的那一段。 */
    val first: ReaderElement.Text,
    val style: ReaderTextStyle,
    val text: String,
    val baselinePx: Float,
    val startLeftPx: Float,
    /** 相邻元素 bounds 之间的缝隙，即画笔要在每个字符后额外插入的间距（px）。 */
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

/**
 * 带链接、样式预览下划线、背景图这类装饰的字要按元素单独定位，不参与合并。
 *
 * 排版按元素插入一次 gap，而 `Paint.letterSpacing` 按 UTF-16 字符插入：`clusterGlyphs`
 * 会把零宽后续字符并入同一元素，组合音标、变体选择符、emoji 代理对这类多字符元素
 * 一旦参与整段绘制，元素内部就会多插 `value.length - 1` 份间距并一路累积到后面的字。
 * 这里按 code unit 而不是 code point 排除，连代理对也退回逐字路径，不必依赖真机上
 * minikin 对簇内字符的具体计法。
 */
private fun ReaderElement.Text.drawsInOneRun(): Boolean =
    value.length == 1 &&
        link == null &&
        emphasisUnderline == null &&
        style.backgroundImage == null &&
        backgroundFrameTopPx == 0f &&
        backgroundFrameBottomPx == 0f &&
        !continuesBackgroundRun

/**
 * 一次 `drawText` 的起点。排版已经把 gap 写进逐字坐标，画笔在每个字符后再插一份
 * gap 时，第 n 个字形的落点正好是原 `bounds.left`（起点 + n × (字宽 + gap)）；末字
 * 之后多插的那份间距不移动任何可见字形，没有需要均摊到两端的余量。左移半个字距
 * 会让整段文字和仍按 bounds 画的选区、背景带、下划线一起错位。
 *
 * 抽成纯函数是为了让 JVM 单测能直接锁定「合并后每个字 x == 原 bounds.left」。
 */
internal fun ReaderTextDrawRun.mergedDrawStartLeftPx(): Float = startLeftPx

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
    val startLeftPx = run.mergedDrawStartLeftPx()
    if (run.gapExtraPx == 0f) {
        scratchPaint.letterSpacing = 0f
        canvas.drawText(run.text, startLeftPx, run.baselinePx, scratchPaint)
        return
    }
    scratchPaint.letterSpacing = run.gapExtraPx / scratchPaint.textSize
    canvas.drawText(
        run.text,
        0,
        run.text.length,
        startLeftPx,
        run.baselinePx,
        scratchPaint,
    )
}
