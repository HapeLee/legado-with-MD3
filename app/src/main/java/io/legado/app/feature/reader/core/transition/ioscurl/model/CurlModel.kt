package io.legado.app.feature.reader.core.transition.ioscurl.model

/**
 * A point or vector in the caller's logical coordinate system.
 * The usual page convention is x to the right and y downward; use the same units for input and layout.
 */
data class CurlPoint(val x: Double, val y: Double)

/**
 * An axis-aligned rectangle expressed as origin plus size, not two opposite corners.
 */
data class CurlRect(val x: Double, val y: Double, val width: Double, val height: Double)

/**
 * The primary axis along which a page turn is recognized.
 */
enum class CurlAxis {
    Horizontal,
    Vertical,
}

/**
 * The fixed edge or center fold of the page.
 *
 * @property nativeValue Stable code used by control calculations; edge codes are also tested as bit flags.
 * @property axis The direction in which the page moves, rather than the direction of the binding line.
 * @property middle Whether the layout contains two independent page faces around a center fold.
 */
enum class CurlBinding(val nativeValue: Int, val axis: CurlAxis, val middle: Boolean = false) {
    Left(1, CurlAxis.Horizontal),
    Top(2, CurlAxis.Vertical),
    MiddleHorizontal(4, CurlAxis.Horizontal, true),
    MiddleVertical(8, CurlAxis.Vertical, true),
    Right(16, CurlAxis.Horizontal),
    Bottom(32, CurlAxis.Vertical),
}

/**
 * Logical navigation direction. Forward is code 0; Reverse is code 1.
 * Its visual motion depends on the binding edge.
 */
enum class CurlDirection(val nativeValue: Int) {
    Forward(0),
    Reverse(1),
}

/**
 * Control mode codes consumed by geometry, timing and transition scheduling.
 * Keep their numeric values stable: internal branches use 3 as the reverse-mode boundary.
 */
enum class CurlKind(val nativeValue: Int) {
    Manual(0),
    Automatic(1),
    ReverseManual(3),
    ReverseAutomatic(4);

    internal companion object {
        /**
         * Tests whether a control code advances without a continuing drag gesture.
         */
        fun isAutomatic(type: Int) =
            type == Automatic.nativeValue || type == ReverseAutomatic.nativeValue

        /**
         * Tests the reverse-mode code range; this is separate from the navigation direction.
         */
        fun isReverse(type: Int) = type >= ReverseManual.nativeValue
    }
}

/**
 * Configuration shared by control, layout and transition scheduling.
 *
 * @property width Page-container width in logical units.
 * @property height Page-container height in the same units.
 * @property initial Initial touch or animation reference point in container coordinates.
 * @property binding Fixed edge or center fold. Center bindings require [doubleSided].
 * @property direction Navigation direction, interpreted together with [binding].
 * @property kind Manual or automatic control mode.
 * @property doubleSided Whether front and back use independent content.
 */
data class CurlConfig(
    val width: Double,
    val height: Double,
    val initial: CurlPoint = CurlPoint(0.0, 0.0),
    val binding: CurlBinding = CurlBinding.Left,
    val direction: CurlDirection = CurlDirection.Forward,
    val kind: CurlKind = CurlKind.Manual,
    val doubleSided: Boolean = false,
) {
    init {
        require(width.isFinite() && height.isFinite() && width > 0 && height > 0)
        require(initial.x.isFinite() && initial.y.isFinite())
        require(!binding.middle || doubleSided) {
            "A middle binding needs independent recto/verso pages"
        }
    }
}

/**
 * The three animated inputs used to construct a curled page.
 *
 * @property time Normalized geometric progress, normally 0..1; this is not elapsed seconds.
 * @property radius Curl radius in logical page units. Must be positive for mesh generation.
 * @property angle Orientation of the curl normal, in radians.
 */
data class CurlValues(val time: Double, val radius: Double, val angle: Double) {

    /**
     * Rounds each component to Float precision, then exposes it as Double for render-compatible calculations.
     */
    fun renderValues() =
        CurlValues(
            time.toFloat().toDouble(),
            radius.toFloat().toDouble(),
            angle.toFloat().toDouble(),
        )
}

/**
 * Start and target geometry for a complete turn in the requested control mode.
 */
data class CurlEndpoints(val from: CurlValues, val to: CurlValues)

/**
 * Initial animation instruction returned by the control layer.
 *
 * @property from Geometry at animation start.
 * @property to Initial target; manual input may replace it on later updates.
 * @property durationSeconds Suggested animation duration in seconds.
 * @property explicitTo True for automatic turns whose target is already fixed.
 * @property type Effective control code after accounting for binding and navigation direction.
 */
data class CurlBegin(
    val from: CurlValues,
    val to: CurlValues,
    val durationSeconds: Double,
    val explicitTo: Boolean,
    val type: Int,
)
