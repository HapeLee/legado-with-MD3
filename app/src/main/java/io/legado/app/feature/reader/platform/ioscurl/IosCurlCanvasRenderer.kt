package io.legado.app.feature.reader.platform.ioscurl

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.Mesh
import android.graphics.MeshSpecification
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.annotation.RequiresApi
import io.legado.app.feature.reader.core.transition.ioscurl.math.BinaryMath
import io.legado.app.feature.reader.core.transition.ioscurl.mesh.CurlDrawBatch
import io.legado.app.feature.reader.core.transition.ioscurl.mesh.CurlMeshFrame
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlRect
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** HWUI fast path for page snapshots recorded over a full opaque background. */
@RequiresApi(34)
internal class IosCurlCanvasRenderer(val current: Bitmap, val target: Bitmap) {
    private val pages = arrayOf(current, target)
    private val shaders = pages.map { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    private val paint = Paint().apply {
        color = Color.WHITE
        isFilterBitmap = true
        isDither = false
    }
    private val bounds = RectF(0f, 0f, current.width.toFloat(), current.height.toFloat())
    private val specification = MeshSpecification.make(
        arrayOf(
            MeshSpecification.Attribute(MeshSpecification.TYPE_FLOAT2, 0, "xy"),
            MeshSpecification.Attribute(MeshSpecification.TYPE_FLOAT2, 8, "uv"),
            MeshSpecification.Attribute(MeshSpecification.TYPE_FLOAT4, 16, "tint"),
        ),
        32,
        arrayOf(
            MeshSpecification.Varying(MeshSpecification.TYPE_FLOAT2, "uv"),
            MeshSpecification.Varying(MeshSpecification.TYPE_FLOAT4, "tint"),
        ),
        "Varyings main(const Attributes a){Varyings v;v.position=a.xy;v.uv=a.uv;v.tint=a.tint;return v;}",
        "float2 main(const Varyings v,out float4 color){color=v.tint;return v.uv;}",
        ColorSpace.get(ColorSpace.Named.SRGB),
        MeshSpecification.ALPHA_TYPE_PREMULTIPLIED,
    )

    /**
     * [opaquePageBackground] is the host's full-page background contract, not Bitmap.hasAlpha.
     * Partial underlays and translucent pages require the existing GL compositing path.
     */
    fun draw(
        canvas: Canvas,
        frame: CurlMeshFrame,
        width: Double,
        height: Double,
        turningTexture: Int,
        underTexture: Int,
        underlays: List<Pair<Int, CurlRect>>,
        opaquePageBackground: Boolean,
    ) {
        require(canvas.isHardwareAccelerated)
        require(canDraw(width, height, underlays, opaquePageBackground))
        val logicalWidth = width.toFloat()
        val logicalHeight = height.toFloat()
        // Validate and allocate before painting, so unsupported packets cannot leave a partial page.
        val meshes = frame.batches.map { batch ->
            require(batch.primitive == 2 && batch.blendFunction == 3 && batch.blendEnabled &&
                (batch.mode == 0 || batch.mode == 3 || batch.mode == 12))
            if (batch.count == 0) null else createMesh(batch, frame, turningTexture, logicalWidth, logicalHeight)
        }
        val saved = canvas.save()
        try {
            canvas.clipRect(bounds)
            val underlayState = canvas.save()
            canvas.scale(current.width / logicalWidth, current.height / logicalHeight)
            paint.shader = null
            paint.blendMode = BlendMode.SRC
            if (underlays.isEmpty()) {
                canvas.drawBitmap(pages[underTexture], null, RectF(0f, 0f, logicalWidth, logicalHeight), paint)
            } else for ((texture, rect) in underlays) {
                canvas.drawBitmap(pages[texture], null, RectF(rect.x.toFloat(), rect.y.toFloat(),
                    (rect.x + rect.width).toFloat(), (rect.y + rect.height).toFloat()), paint)
            }
            canvas.restoreToCount(underlayState)
            for (index in meshes.indices) {
                val mesh = meshes[index] ?: continue
                val batch = frame.batches[index]
                paint.blendMode = if (batch.blendEnabled) BlendMode.SRC_OVER else BlendMode.SRC
                paint.shader = if (batch.mode == 0) null else shaders[turningTexture]
                canvas.drawMesh(mesh, if (batch.mode == 12) BlendMode.DST_IN else BlendMode.MODULATE, paint)
            }
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    fun canDraw(width: Double, height: Double, underlays: List<Pair<Int, CurlRect>>,
                opaquePageBackground: Boolean): Boolean =
        opaquePageBackground && (underlays.isEmpty() || underlays.any { (_, rect) ->
            rect.x <= 0.0 && rect.y <= 0.0 && rect.x + rect.width >= width && rect.y + rect.height >= height
        })

    private fun createMesh(
        batch: CurlDrawBatch,
        frame: CurlMeshFrame,
        texture: Int,
        width: Float,
        height: Float,
    ): Mesh {
        val vertices = ByteBuffer.allocateDirect(batch.vertexCount * 32)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        val source = ByteBuffer.wrap(batch.vertices).order(ByteOrder.LITTLE_ENDIAN)
        val uScale = pages[texture].width.toFloat() / frame.width
        val vScale = pages[texture].height.toFloat() / frame.height
        val xToNdc = 2f / width
        val yToNdc = -2f / height
        val halfWidth = current.width * .5f
        val halfHeight = current.height * .5f
        repeat(batch.vertexCount) { vertex ->
            val at = vertex * 64
            // Match the GLES projection and viewport rounding before interpolating on HWUI's pixel grid.
            val x = ((source.getFloat(at) * xToNdc - 1f) + 1f) * halfWidth
            val y = (1f - (source.getFloat(at + 4) * yToNdc + 1f)) * halfHeight
            vertices.put(x).put(y)
                .put(source.getFloat(at + 16) * uScale).put(source.getFloat(at + 20) * vScale)
            repeat(4) { channel ->
                vertices.put(BinaryMath.halfToFloat(source.getShort(at + 40 + channel * 2).toInt() and 65535))
            }
        }
        vertices.flip()
        val indices = batch.indices ?: return Mesh(specification, Mesh.TRIANGLE_STRIP, vertices,
            batch.count, bounds)
        val elements = ByteBuffer.allocateDirect(batch.count * 2).order(ByteOrder.nativeOrder())
            .asShortBuffer().apply { put(indices, 0, batch.count); flip() }
        return Mesh(specification, Mesh.TRIANGLE_STRIP, vertices, batch.vertexCount, elements, bounds)
    }
}
