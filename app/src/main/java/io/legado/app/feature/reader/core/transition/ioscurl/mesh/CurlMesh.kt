package io.legado.app.feature.reader.core.transition.ioscurl.mesh

import io.legado.app.feature.reader.core.transition.ioscurl.mesh.kernel.CurlMeshKernel
import io.legado.app.feature.reader.core.transition.ioscurl.mesh.kernel.build
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlRect
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlValues

/** Straight RGBA input. Serialization premultiplies and quantizes once. */
data class CurlColor(val red: Double, val green: Double, val blue: Double, val alpha: Double)

data class CurlMaterial(
    val front: CurlColor = CurlColor(1.0, 1.0, 1.0, 1.0),
    val back: CurlColor = CurlColor(244.0 / 255, 244.0 / 255, 244.0 / 255, 0.8),
    val backShade: CurlColor = CurlColor(0.7, 0.7, 0.7, 1.0),
    val shadow: CurlColor = CurlColor(0.15, 0.15, 0.15, 1.0),
)

data class CurlMeshInput(
    val values: CurlValues,
    val width: Double,
    val height: Double,
    val scale: Double = 1.0,
    val startAngle: Double = 0.0,
    val endAngle: Double = kotlin.math.PI,
    val frontEnabled: Boolean = true,
    val backEnabled: Boolean = true,
    val material: CurlMaterial = CurlMaterial(),
    val surfaceRect: CurlRect? = null,
    val targetBounds: CurlRect? = null,
    val clipRect: CurlRect? = null,
    val shadowBounds: CurlRect? = null,
    /** Penumbra reach relative to the recovered profile. 1 preserves the reference mesh. */
    val shadowSoftness: Double = 1.0,
)

/** Vertex stride64: float4 position@0, float2 pixel-UV@16, premultiplied half4 tint@40. */
data class CurlDrawBatch(
    val primitive: Int,
    val mode: Int,
    val blendFunction: Int,
    val blendEnabled: Boolean,
    val vertexCount: Int,
    val count: Int,
    val vertices: ByteArray,
    val indices: ShortArray?,
    val clip: CurlRect,
)

data class CurlMeshFrame(val width: Int, val height: Int, val batches: List<CurlDrawBatch>)

/** Single-owner workspace, without platform objects, clocks or page/navigation state. */
class CurlMesh {
    private val kernel = CurlMeshKernel()

    fun build(input: CurlMeshInput): CurlMeshFrame = kernel.build(input)
}
