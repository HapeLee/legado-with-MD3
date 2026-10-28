package io.legado.app.feature.reader.core.transition.ioscurl.layout

import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlBinding
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlConfig
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlDirection
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlRect

/** Source identity is separate from page numbering and texture ownership. */
enum class CurlPageSource(val nativeValue: Int) {
    Outgoing0(0),
    Outgoing1(1),
    Incoming0(2),
    Incoming1(3),
}

data class CurlStaticPage(val source: CurlPageSource, val frame: CurlRect)

data class CurlPagePass(
    val source: CurlPageSource,
    val frame: CurlRect,
    val front: Boolean,
    val back: Boolean,
    val mirrorContent: Boolean = false,
    val transparentBackMaterial: Boolean = false,
)

data class CurlPageLayout(
    val passes: List<CurlPagePass>,
    val underlays: List<CurlStaticPage>,
    val outgoing: List<CurlStaticPage>,
    val incoming: List<CurlStaticPage>,
)

/** Supports edge and middle bindings in the portable core; Android currently exposes one sheet. */
object CurlLayout {
    fun build(
        c: CurlConfig,
        rect: CurlRect = CurlRect(0.0, 0.0, c.width, c.height),
    ): CurlPageLayout {
        val middle = c.binding.middle
        val reverse = c.direction == CurlDirection.Reverse
        val minimum = c.binding == CurlBinding.Left || c.binding == CurlBinding.Top
        fun half(first: Boolean): CurlRect =
            when (c.binding) {
                CurlBinding.MiddleHorizontal ->
                    rect.copy(
                        x = rect.x + if (first) 0.0 else rect.width * 0.5,
                        width = rect.width * 0.5,
                    )
                CurlBinding.MiddleVertical ->
                    rect.copy(
                        y = rect.y + if (first) 0.0 else rect.height * 0.5,
                        height = rect.height * 0.5,
                    )
                else -> rect
            }
        val frame = half(reverse)
        val frontSource =
            when {
                minimum -> if (reverse) CurlPageSource.Incoming0 else CurlPageSource.Outgoing0
                middle -> if (reverse) CurlPageSource.Outgoing0 else CurlPageSource.Outgoing1
                else -> if (reverse) CurlPageSource.Outgoing0 else CurlPageSource.Incoming0
            }
        val backSource =
            when {
                !c.doubleSided -> null
                middle -> if (reverse) CurlPageSource.Incoming1 else CurlPageSource.Incoming0
                else -> CurlPageSource.Incoming1
            }
        val passes =
            mutableListOf(CurlPagePass(frontSource, frame, front = true, back = backSource == null))
        if (backSource != null)
            passes +=
                CurlPagePass(
                    backSource,
                    frame,
                    front = false,
                    back = true,
                    mirrorContent = true,
                    transparentBackMaterial = true,
                )
        val count = if (middle) 2 else 1
        val outgoing =
            (0 until count).map { index ->
                CurlStaticPage(
                    if (index == 0) CurlPageSource.Outgoing0 else CurlPageSource.Outgoing1,
                    half(index == 0 && (middle || !minimum)),
                )
            }
        val incoming = outgoing.mapIndexed { index, page ->
            page.copy(
                source = if (index == 0) CurlPageSource.Incoming0 else CurlPageSource.Incoming1
            )
        }
        val underlays =
            outgoing.filter { it.source != frontSource && it.source != backSource }.toMutableList()
        if (!reverse && (minimum || middle))
            underlays.add(
                0,
                CurlStaticPage(
                    if (middle) CurlPageSource.Incoming1 else CurlPageSource.Incoming0,
                    half(false),
                ),
            )
        if (reverse && (middle || !minimum))
            underlays.add(0, CurlStaticPage(CurlPageSource.Incoming0, half(true)))
        return CurlPageLayout(passes, underlays, outgoing, incoming)
    }

    fun incomingPages(c: CurlConfig, current: Int): IntArray {
        val sign = if (c.direction == CurlDirection.Forward) 1 else -1
        return if (c.binding.middle) intArrayOf(current + 2 * sign, current + 2 * sign + 1)
        else if (c.doubleSided) intArrayOf(current + 2 * sign, current + sign)
        else intArrayOf(current + sign)
    }

    fun sourcePage(c: CurlConfig, current: Int, source: CurlPageSource): Int =
        if (source.nativeValue < 2) current + source.nativeValue
        else
            incomingPages(
                c,
                current,
            )[source.nativeValue - 2]
}
