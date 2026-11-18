package io.legado.app.feature.reader

import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.floor

/**
 * 同一行、同样式、同字距的正文合成一次 shaping 与绘制，保留整段排版的效果。
 * 只有实际画笔的逐字 advance 与排版坐标相符时才合并，避免末字越过内容裁剪边界。
 */
internal class ReaderTextDrawRun(
    val first: ReaderElement.Text,
    val style: ReaderTextStyle,
    val text: String,
    val baselinePx: Float,
    val startLeftPx: Float,
    val gapExtraPx: Float,
    val underline: Boolean,
)

private const val GAP_TOLERANCE_PX = 0.01f
/** 比较整段累计坐标，不能只限制每一道缝的误差，否则长行仍会累积偏移。 */
private const val POSITION_TOLERANCE_PX = 0.05f

/** Android 10 及更早的 Minikin 在非 linearText 下取整；Android 11 起保留浮点字距。 */
internal fun minikinLetterSpacingPx(gapPx: Float, linearText: Boolean, sdkInt: Int): Float =
    if (sdkInt >= 30 || linearText) gapPx
    else if (gapPx < 0f) -floor(-gapPx + .5f) else floor(gapPx + .5f)

/**
 * drawTextRun 不自动裁掉两端的半字距（与新版 drawText 不同），因而各版本可用同一笔位。
 * Android 10 及更早的非 linearText 仍需遵守旧的 floor(整数字距 / 2) 规则。
 */
internal fun minikinLeadingHalfSpacingPx(gapPx: Float, linearText: Boolean, sdkInt: Int): Float {
    val spacing = minikinLetterSpacingPx(gapPx, linearText, sdkInt)
    return if (sdkInt >= 30 || linearText) spacing / 2f else floor(spacing / 2f)
}

/**
 * 整段合并依赖 `Paint#getTextRunAdvances(char[], …)`，该重载自 API 29 才公开
 * （api-versions 记录 since="29"）。低于 Q 的设备不做合并，退回逐元素绘制：
 * 几何与合并前一致，只是少一次绘制合并，不会出现 NoSuchMethodError / 隐藏 API 拦截。
 */
@ChecksSdkIntAtLeast(parameter = 0, api = 29)
internal fun supportsMergedTextRuns(sdkInt: Int): Boolean = sdkInt >= 29

/** 建页缓存时完成真实字体测量；逐帧绘制只读取已经验证的 run。 */
internal fun buildMergedTextRuns(
    elements: List<ReaderElement>,
    paints: Map<ReaderTextStyle, Paint>,
): Map<ReaderElement.Text, ReaderTextDrawRun> {
    if (!supportsMergedTextRuns(Build.VERSION.SDK_INT)) return emptyMap()
    val scratchPaint = Paint()
    return buildMergedTextRuns(elements, Build.VERSION.SDK_INT) { run, members ->
        scratchPaint.configureMergedRun(paints.getValue(run.style), run)
        val chars = run.text.toCharArray()
        val advances = FloatArray(chars.size)
        scratchPaint.getTextRunAdvances(chars, 0, chars.size, 0, chars.size, false, advances, 0)
        run.matchesLayout(members, advances)
    }
}

/** 结构分段与平台测量分开，纯 JVM 测试可覆盖旧系统量化与段内累计误差。 */
internal fun buildMergedTextRuns(
    elements: List<ReaderElement>,
    sdkInt: Int,
    matchesPaint: (ReaderTextDrawRun, List<ReaderElement>) -> Boolean,
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
            if (candidate.baselinePx != first.baselinePx || candidate.bounds.top != first.bounds.top) break
            val gap = candidate.bounds.left - previous.bounds.right
            if (!gap.isFinite()) break
            val actualGap = minikinLetterSpacingPx(gap, first.style.linearText, sdkInt)
            if (abs(actualGap - gap) > GAP_TOLERANCE_PX) break
            val uniformGap = gapExtraPx
            if (uniformGap == null) gapExtraPx = gap
            else if (abs(gap - uniformGap) > GAP_TOLERANCE_PX) break
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
            // 缺字风险来自 shaping 与原排版不一致，不能用「缝隙相等」替代实际字体测量。
            if (matchesPaint(run, elements.subList(index, next))) {
                for (member in index until next) runs[elements[member] as ReaderElement.Text] = run
            }
            index = next
        } else {
            index++
        }
    }
    return runs
}

/** 校验每个落点及末字右沿，字体替换、连字变化和不支持字距的文字都能安全回退。 */
internal fun ReaderTextDrawRun.matchesLayout(members: List<ReaderElement>, advances: FloatArray): Boolean {
    if (members.size != advances.size) return false
    var x = startLeftPx
    members.forEachIndexed { index, member ->
        val element = member as? ReaderElement.Text ?: return false
        val width = advances[index] - gapExtraPx
        if (!x.isFinite() || !width.isFinite() ||
            abs(x - element.bounds.left) > POSITION_TOLERANCE_PX ||
            abs(x + width - element.bounds.right) > POSITION_TOLERANCE_PX
        ) return false
        x += advances[index]
    }
    return true
}

/**
 * 多 code unit 的元素按自己的簇绘制，不能假定一次 run shaping 会保留相同的簇边界。
 * 零宽缩进与按词对齐空格的 bounds 不是字体 advance；链接、背景图等装饰仍独立定位。
 * 当前排版元素按从左到右排列，双向/控制字符交回逐元素路径，不强制当作 LTR run。
 */
private fun ReaderElement.Text.drawsInOneRun(): Boolean =
    value.length == 1 &&
        glyphOffsetX == 0f &&
        rubyText == null &&
        rubyGroupId == 0 &&
        !value[0].isSurrogate() &&
        !Character.isISOControl(value[0]) &&
        when (Character.getDirectionality(value[0])) {
            Character.DIRECTIONALITY_LEFT_TO_RIGHT,
            Character.DIRECTIONALITY_EUROPEAN_NUMBER,
            Character.DIRECTIONALITY_EUROPEAN_NUMBER_SEPARATOR,
            Character.DIRECTIONALITY_EUROPEAN_NUMBER_TERMINATOR,
            Character.DIRECTIONALITY_COMMON_NUMBER_SEPARATOR,
            Character.DIRECTIONALITY_OTHER_NEUTRALS,
            Character.DIRECTIONALITY_WHITESPACE -> true
            else -> false
        } &&
        value != " " &&
        bounds.width > 0f && bounds.width.isFinite() &&
        link == null &&
        emphasisUnderline == null &&
        style.backgroundImage == null &&
        backgroundFrameTopPx == 0f &&
        backgroundFrameBottomPx == 0f &&
        !continuesBackgroundRun

internal fun ReaderTextDrawRun.mergedDrawStartLeftPx(sdkInt: Int): Float =
    startLeftPx - minikinLeadingHalfSpacingPx(gapExtraPx, style.linearText, sdkInt)

private fun Paint.configureMergedRun(stylePaint: Paint, run: ReaderTextDrawRun) {
    set(stylePaint)
    // run API 必须保留两端半字距，不能继承新版 Paint 的行首/行尾裁剪标记。
    if (Build.VERSION.SDK_INT >= 35) {
        flags = flags and (Paint.TEXT_RUN_FLAG_LEFT_EDGE or Paint.TEXT_RUN_FLAG_RIGHT_EDGE).inv()
    }
    letterSpacing = minikinLetterSpacingPx(
        run.gapExtraPx, run.style.linearText, Build.VERSION.SDK_INT,
    ) / textSize
    isUnderlineText = run.underline
}

internal fun drawMergedTextRun(
    canvas: Canvas,
    run: ReaderTextDrawRun,
    stylePaint: Paint,
    colorArgb: Int,
    scratchPaint: Paint,
) {
    scratchPaint.configureMergedRun(stylePaint, run)
    scratchPaint.color = colorArgb
    canvas.drawTextRun(
        run.text,
        0,
        run.text.length,
        0,
        run.text.length,
        run.mergedDrawStartLeftPx(Build.VERSION.SDK_INT),
        run.baselinePx,
        false,
        scratchPaint,
    )
}

internal fun drawRubyText(
    canvas: Canvas,
    element: ReaderElement.Text,
    basePaint: Paint,
    scratchPaint: Paint,
) {
    val ruby = element.rubyText ?: return
    scratchPaint.set(basePaint)
    scratchPaint.textSize = element.rubyFontSizePx
    scratchPaint.letterSpacing = 0f
    scratchPaint.isUnderlineText = false
    scratchPaint.isStrikeThruText = false
    val width = scratchPaint.measureText(ruby)
    canvas.drawText(ruby, element.rubyCenterX - width / 2f, element.rubyBaselinePx, scratchPaint)
}
