package io.legado.app.feature.reader.core.transition.ioscurl.model

/**
 * All algorithms use logical points and seconds. Platform adapters own pixel/density conversion.
 */
data class CurlPoint(val x: Double, val y: Double)

data class CurlRect(val x: Double, val y: Double, val width: Double, val height: Double)

enum class CurlAxis {
    Horizontal,
    Vertical,
}

enum class CurlBinding(val nativeValue: Int, val axis: CurlAxis, val middle: Boolean = false) {
    Left(1, CurlAxis.Horizontal),
    Top(2, CurlAxis.Vertical),
    MiddleHorizontal(4, CurlAxis.Horizontal, true),
    MiddleVertical(8, CurlAxis.Vertical, true),
    Right(16, CurlAxis.Horizontal),
    Bottom(32, CurlAxis.Vertical),
}

/** Physical direction; reading order and RTL mapping belong to the host. */
enum class CurlDirection(val nativeValue: Int) {
    Forward(0),
    Reverse(1),
}

enum class CurlKind(val nativeValue: Int) {
    Manual(0),
    Automatic(1),
    ReverseManual(3),
    ReverseAutomatic(4);

    internal companion object {
        fun isAutomatic(type: Int) =
            type == Automatic.nativeValue || type == ReverseAutomatic.nativeValue

        fun isReverse(type: Int) = type >= ReverseManual.nativeValue
    }
}

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

data class CurlValues(val time: Double, val radius: Double, val angle: Double) {
    /** Core Animation stores filter inputs as float32 at the render boundary. */
    fun renderValues() =
        CurlValues(
            time.toFloat().toDouble(),
            radius.toFloat().toDouble(),
            angle.toFloat().toDouble(),
        )
}

data class CurlEndpoints(val from: CurlValues, val to: CurlValues)

data class CurlBegin(
    val from: CurlValues,
    val to: CurlValues,
    val durationSeconds: Double,
    val explicitTo: Boolean,
    val type: Int,
)
