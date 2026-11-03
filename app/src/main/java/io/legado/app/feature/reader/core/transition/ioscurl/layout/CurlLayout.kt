package io.legado.app.feature.reader.core.transition.ioscurl.layout

import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlBinding
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlConfig
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlDirection
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlRect

/**
 * Symbolic content slots for one transition, not absolute document page numbers.
 * Outgoing slots refer to the current content; Incoming slots refer to destination content.
 */
enum class CurlPageSource(val nativeValue: Int) {
    Outgoing0(0),
    Outgoing1(1),
    Incoming0(2),
    Incoming1(3),
}

/**
 * A content source and its logical rectangle for a non-curled page layer.
 */
data class CurlStaticPage(val source: CurlPageSource, val frame: CurlRect)

/**
 * One curled-face rendering pass.
 *
 * @property source Content slot to resolve with CurlLayout.sourcePage.
 * @property frame Logical target rectangle for this face.
 * @property front Whether the pass draws front geometry.
 * @property back Whether the pass draws back geometry.
 * @property mirrorContent Whether the host must mirror independently supplied back content.
 * @property transparentBackMaterial Whether the pass needs a transparent back material to expose that content.
 */
data class CurlPagePass(
    val source: CurlPageSource,
    val frame: CurlRect,
    val front: Boolean,
    val back: Boolean,
    val mirrorContent: Boolean = false,
    val transparentBackMaterial: Boolean = false,
)

/**
 * Layer plan for a turn.
 *
 * @property passes Ordered curled-face passes.
 * @property underlays Static layers underneath the turning sheet, in planned order.
 * @property outgoing Static arrangement before the turn.
 * @property incoming Static arrangement after a committed turn.
 */
data class CurlPageLayout(
    val passes: List<CurlPagePass>,
    val underlays: List<CurlStaticPage>,
    val outgoing: List<CurlStaticPage>,
    val incoming: List<CurlStaticPage>,
)

/**
 * Builds content and layer plans without creating textures or changing the document page index.
 */
object CurlLayout {
    /**
     * Selects page sources, front/back passes and underlays for the configured binding.
     * rect is in the caller's logical layout coordinates and is split only for center bindings.
     */
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
        // A separate back-content pass is needed only when the two faces use independent content.
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
        // Exclude content already on the turning sheet, then place newly revealed content underneath it.
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

    /**
     * Returns destination document indices from the supplied current index.
     * Center folds advance by two; independent edge-bound faces also consume two indices.
     * No bounds checking is performed against the host document.
     */
    fun incomingPages(c: CurlConfig, current: Int): IntArray {
        val sign = if (c.direction == CurlDirection.Forward) 1 else -1
        return if (c.binding.middle) intArrayOf(current + 2 * sign, current + 2 * sign + 1)
        else if (c.doubleSided) intArrayOf(current + 2 * sign, current + sign)
        else intArrayOf(current + sign)
    }

    /**
     * Resolves a symbolic content slot to a document index.
     * The caller supplies the index base and remains responsible for document bounds.
     */
    fun sourcePage(c: CurlConfig, current: Int, source: CurlPageSource): Int =
        if (source.nativeValue < 2) current + source.nativeValue
        else
            incomingPages(
                c,
                current,
            )[source.nativeValue - 2]
}
