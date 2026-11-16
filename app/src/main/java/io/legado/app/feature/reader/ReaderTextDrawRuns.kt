package io.legado.app.feature.reader

import android.graphics.Canvas
import android.graphics.Paint
import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.floor

/**
 * 一行里连续、同样式、字距一致的文本元素合成的一段正文。
 *
 * 排版阶段已经把字距写进每个元素的 `bounds.left`（按 `字形宽度 + gap` 累加），逐字
 * 绘制时字形按这些坐标定位。合成一次 `drawText` 让画笔统一做 shaping、kerning 和
 * 连字，代价是字距只能交给 `Paint.letterSpacing`，而画笔的落笔规则和排版并不相同：
 * minikin 会把字距折算成整数 px，又在第一个字形之前先垫半个字距（见
 * [minikinLetterSpacingPx]、[minikinLeadingHalfSpacingPx]）。因此只有「量化不改变
 * 数值」的缝隙才允许合并，起点还要减掉那半格；否则整段会随字数累积偏移，行尾最后
 * 几个字会被内容裁剪框切掉。
 */
internal class ReaderTextDrawRun(
    /** 这一行的第一个字。绘制时用它判断当前元素是不是本次要画的那一段。 */
    val first: ReaderElement.Text,
    val style: ReaderTextStyle,
    val text: String,
    val baselinePx: Float,
    val startLeftPx: Float,
    /** 相邻元素 bounds 之间的缝隙，即画笔要在每个字形之间额外插入的间距（px）。 */
    val gapExtraPx: Float,
    val underline: Boolean,
)

/** 字距相差不到这个值仍视为一致，容下排版阶段浮点累加的误差。 */
private const val GAP_TOLERANCE_PX = 0.01f

/**
 * 量化后的字距与排版缝隙允许的差。
 *
 * 画笔按整数 px 插字距（见 [minikinLetterSpacingPx]），每个字都偏 `round(gap) - gap`，
 * 一行累积 `(字数 - 1) × 该差`。卡在 0.01px 以内，20 字一行的累计偏移不超过 0.2px，
 * 与仍按 `bounds` 画的选区、背景带、下划线对得上。
 */
private const val GAP_QUANTIZE_TOLERANCE_PX = 0.01f

/**
 * 画笔在每个字形之间实际插入的间距。
 *
 * minikin `Layout::doLayoutRun` 里 `letterSpace = letterSpacing * textSize`，非 linearText
 * 时再 `letterSpace = round(letterSpace)`——字距被取整到整数 px（C `round`：.5 远离零，
 * 与 `kotlin.math.round` 的「凑偶」不同）。排版用的是浮点缝隙，两者只有在取整不改变
 * 数值时才可能逐字重合。
 */
internal fun minikinLetterSpacingPx(gapPx: Float, linearText: Boolean): Float =
    if (linearText) gapPx else roundHalfAwayFromZero(gapPx)

/** C `round()` 的等价：`.5` 远离零取整，`2.5 → 3`、`-2.5 → -3`。 */
private fun roundHalfAwayFromZero(value: Float): Float =
    if (value < 0f) -floor(-value + 0.5f) else floor(value + 0.5f)

/**
 * 第一个字形之前先垫的那半格：minikin 在 glyph 循环之前就 `x += letterSpaceHalfLeft`，
 * 其中 `letterSpaceHalfLeft = floor(letterSpace / 2)`（linearText 下是精确一半）。
 *
 * 排版给出的 `bounds.left` 是笔位、不含这半格，所以整段绘制必须从
 * `startLeftPx - 该值` 起笔，首字才落回原 `bounds.left`。
 */
internal fun minikinLeadingHalfSpacingPx(gapPx: Float, linearText: Boolean): Float {
    val spacing = minikinLetterSpacingPx(gapPx, linearText)
    return if (linearText) spacing / 2f else floor(spacing / 2f)
}

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
        val linearText = first.style.linearText
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
            // 画笔的字距是整数 px：与排版缝隙不等时每个字都偏一点，一行累积下来行尾
            // 最后几个字会被内容裁剪框切掉。这种行留在逐字绘制路径，那里每个字都按
            // 自己的 bounds.left 落笔。
            if (abs(minikinLetterSpacingPx(gap, linearText) - gap) > GAP_QUANTIZE_TOLERANCE_PX) break
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
 * 另外三类字同样不能合并：
 * - 多 code unit 的元素（组合音标、变体选择符、emoji 代理对）：`Paint.letterSpacing`
 *   按 UTF-16 字符插入而排版按元素插入，元素内部会多插 `value.length - 1` 份间距，
 *   并一路累积到后面的字。这里按 code unit 排除，连代理对也退回逐字路径，不必依赖
 *   真机上 minikin 对簇内字符的具体计法。
 * - 排版把宽度记成 0 的字（HTML 结构块隐藏的段首缩进占位）：画笔仍按字体给它真实
 *   advance，一旦合并，后面所有字整体右移一个字宽。
 * - 词间空格：按词两端对齐（HTML 段）会把 `wordSpaceExtra` 摊进空格元素自己的
 *   `bounds` 宽度，一次 `drawText` 只有一个统一字距，复现不了这份增量。
 */
private fun ReaderElement.Text.drawsInOneRun(): Boolean =
    value.length == 1 &&
        value != " " &&
        bounds.width > 0f &&
        link == null &&
        emphasisUnderline == null &&
        style.backgroundImage == null &&
        backgroundFrameTopPx == 0f &&
        backgroundFrameBottomPx == 0f &&
        !continuesBackgroundRun

/**
 * 一次 `drawText` 的起点：排版笔位减去画笔会自己垫在首字之前的半个字距（见
 * [minikinLeadingHalfSpacingPx]）。这样第 n 个字形的落点正好是原 `bounds.left`，
 * 与仍按 `bounds` 画的选区、背景带、下划线严丝合缝。
 *
 * 抽成纯函数是为了让 JVM 单测能直接锁定「合并后每个字 x == 原 bounds.left」。
 */
internal fun ReaderTextDrawRun.mergedDrawStartLeftPx(): Float =
    startLeftPx - minikinLeadingHalfSpacingPx(gapExtraPx, style.linearText)

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
    // 用画笔实际会插入的整数字距，而不是排版缝隙本身，否则两者的差会随字数累积。
    val letterSpacingPx = minikinLetterSpacingPx(run.gapExtraPx, run.style.linearText)
    val startLeftPx = run.mergedDrawStartLeftPx()
    if (letterSpacingPx == 0f) {
        scratchPaint.letterSpacing = 0f
        canvas.drawText(run.text, startLeftPx, run.baselinePx, scratchPaint)
        return
    }
    scratchPaint.letterSpacing = letterSpacingPx / scratchPaint.textSize
    canvas.drawText(
        run.text,
        0,
        run.text.length,
        startLeftPx,
        run.baselinePx,
        scratchPaint,
    )
}
