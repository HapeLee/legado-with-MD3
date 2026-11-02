package io.legado.app.feature.reader.core.transition.ioscurl.mesh

import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlRect
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlValues
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.PI
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CurlMeshCompatibilityTest {
    @Test fun referenceFramesKeepTheirDrawContractAndGeometry() {
        val mesh = CurlMesh()
        for ((index, input) in inputs.withIndex()) {
            val frame = mesh.build(input)
            assertEquals("frame $index draw order, topology, clipping and packed colors",
                signatures[index], drawSignature(frame))
            // The new standard trig functions can differ by a few Float ULPs.
            // Old-kernel geometry is in logical units; UVs use scaled source coordinates.
            assertArrayEquals("frame $index geometry and texture coordinates",
                measurements[index], measure(frame), .001)
            for (batch in frame.batches) {
                assertEquals(batch.vertexCount, batch.count)
                assertEquals(batch.vertexCount * 64, batch.vertices.size)
                val bytes = ByteBuffer.wrap(batch.vertices).order(ByteOrder.LITTLE_ENDIAN)
                for (vertex in 0 until batch.vertexCount) {
                    assertEquals(1f, bytes.getFloat(vertex * 64 + 12), 0f)
                    for (offset in 0..20 step 4) {
                        assertTrue(bytes.getFloat(vertex * 64 + offset).isFinite())
                    }
                }
            }
        }
    }

    @Test fun retainedFramesOwnTheirBuffersAcrossLaterBuilds() {
        val mesh = CurlMesh()
        val retained = mesh.build(inputs[1])
        val copies = retained.batches.map { it.vertices.copyOf() }
        mesh.build(inputs[3])
        mesh.build(inputs[1].copy(frontEnabled = false, backEnabled = false))
        retained.batches.zip(copies).forEach { (batch, copy) -> assertArrayEquals(copy, batch.vertices) }
        val repeated = mesh.build(inputs[1])
        assertEquals(drawSignature(retained), drawSignature(repeated))
        repeated.batches.zip(copies).forEach { (batch, copy) -> assertArrayEquals(copy, batch.vertices) }
    }

    @Test fun disabledFacesAndEmptyClippingDoNotLeakPreviousBatches() {
        val mesh = CurlMesh()
        val input = inputs[1]
        assertTrue(mesh.build(input).batches.isNotEmpty())
        assertTrue(mesh.build(input.copy(frontEnabled = false, backEnabled = false)).batches.isEmpty())
        assertTrue(mesh.build(input.copy(clipRect = CurlRect(700.0, 1000.0, 10.0, 10.0))).batches.isEmpty())
        assertEquals(signatures[1], drawSignature(mesh.build(input)))
    }

    @Test fun invalidGeometryIsRejectedAtThePublicBoundary() {
        val input = inputs[1]
        for (invalid in listOf(
            input.copy(width = 0.0),
            input.copy(height = Double.POSITIVE_INFINITY),
            input.copy(scale = 0.0),
            input.copy(values = input.values.copy(radius = 0.0)),
            input.copy(values = input.values.copy(time = Double.NaN)),
            input.copy(values = input.values.copy(angle = Double.NaN)),
            input.copy(shadowSoftness = 4.0),
        )) {
            assertThrows(IllegalArgumentException::class.java) { CurlMesh().build(invalid) }
        }
    }

    /** Hash draw commands and colors; compare positions/UVs numerically and ignore unused padding. */
    private fun drawSignature(frame: CurlMeshFrame): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(frame.width)
            out.writeInt(frame.height)
            out.writeInt(frame.batches.size)
            for (batch in frame.batches) {
                out.writeInt(batch.primitive)
                out.writeInt(batch.mode)
                out.writeInt(batch.blendFunction)
                out.writeBoolean(batch.blendEnabled)
                out.writeInt(batch.vertexCount)
                out.writeInt(batch.count)
                for (value in listOf(batch.clip.x, batch.clip.y, batch.clip.width, batch.clip.height)) out.writeDouble(value)
                out.writeInt(batch.indices?.size ?: -1)
                batch.indices?.forEach { out.writeShort(it.toInt()) }
                for (vertex in 0 until batch.vertexCount) out.write(batch.vertices, vertex * 64 + 40, 8)
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun measure(frame: CurlMeshFrame): DoubleArray {
        val result = doubleArrayOf(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
            Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, 0.0, 0.0, 0.0, 0.0)
        var count = 0
        for (batch in frame.batches) {
            val bytes = ByteBuffer.wrap(batch.vertices).order(ByteOrder.LITTLE_ENDIAN)
            for (vertex in 0 until batch.vertexCount) {
                val x = bytes.getFloat(vertex * 64).toDouble()
                val y = bytes.getFloat(vertex * 64 + 4).toDouble()
                result[0] = minOf(result[0], x)
                result[1] = maxOf(result[1], x)
                result[2] = minOf(result[2], y)
                result[3] = maxOf(result[3], y)
                result[4] += x
                result[5] += y
                result[6] += bytes.getFloat(vertex * 64 + 8)
                result[7] += bytes.getFloat(vertex * 64 + 16)
                result[8] += bytes.getFloat(vertex * 64 + 20)
                count++
            }
        }
        for (i in 4..8) result[i] /= count
        return result
    }

    private val inputs = listOf(
        CurlMeshInput(CurlValues(0.0, 35.0, .6), 320.0, 480.0, 2.0),
        CurlMeshInput(CurlValues(.2, 35.0, .6), 320.0, 480.0, 2.0),
        CurlMeshInput(CurlValues(.5, 35.0, 0.0), 320.0, 480.0, 2.0),
        CurlMeshInput(CurlValues(.8, 35.0, -.6), 320.0, 480.0, 2.0),
        CurlMeshInput(CurlValues(1.0, 35.0, -.6), 320.0, 480.0, 2.0),
        CurlMeshInput(CurlValues(.5, 57.6, PI), 480.0, 320.0, 3.0),
        CurlMeshInput(CurlValues(.2, 49.38, .3), 411.5, 683.25, 1.5),
        CurlMeshInput(CurlValues(.5, 35.0, PI / 2), 320.0, 480.0),
    )

    // Captured from the pre-refactor kernel at 9b434db68, before replacing production sources.
    private val signatures = listOf(
        "cae72129e88eb719f294f2641ac25971186b98f26f387b0e885e200d5fe07ef8",
        "b29b791c4da143e3257f7dd72500172164430f4c00dea26422f61777e0406dcf",
        "50bc9e2163d7023bf41c47c0388559b09545b79266b2492ad30c9c430fa78261",
        "c7eddb73240f1cc9453aed5ad8467c9ec974e756a625dcb86fa7d267ff1f2016",
        "9c72d05de2d20207455b9d025bf66817af66149025ed541b809a3caf7a9cc2e1",
        "cd5bb064ca7eb48aa747a9dc24f0d2d0276f3230a85178855b3c20d3f99e61a5",
        "9d309037cb189cfb80715327902297ae5b1c664bd109140564cf7cd0dc18f4ae",
        "a5ebe58282675d7c19d406a4d90f7a3e70a5103daf80ea41aa0068cf065aceec",
    )
    private val measurements = listOf(
        doubleArrayOf(-.2250022441, 320.2250366211, -.2250055224, 480.2250366211, 11.5981101672, 16.9554835838, 0.0, 21.4009120022, 32.1000010542),
        doubleArrayOf(-3.5744087696, 320.2250366211, -7.2420048714, 480.2250366211, 72.6586795276, 74.3801566049, 0.0, 15.0372638365, 22.2669023568),
        doubleArrayOf(128.4424591064, 320.0, -15.7397785187, 495.7398071289, 179.2746420905, 240.0000004021, 0.0, 30.0579113813, 58.6447638604),
        doubleArrayOf(155.3801116943, 677.9540405273, -162.4110565186, 340.6567993164, 338.1095980663, 89.3878853390, 0.0, 47.5093155786, 32.9719053376),
        doubleArrayOf(293.5708312988, 866.3504028320, -291.2999572754, 211.7678985596, 475.3229233284, -21.5381560085, 0.0, 57.9326451157, 20.9708435284),
        doubleArrayOf(0.0, 291.9346923828, -24.4058227539, 344.4058227539, 214.3249386312, 159.9999999165, 0.0, 109.9923290590, 58.6447619961),
        doubleArrayOf(-2.6139638424, 412.3000488281, -15.4171199799, 683.6334228516, 71.8715665959, 109.8431567287, 0.0, 14.6831034508, 32.0920950013),
        doubleArrayOf(-15.7397785187, 335.7397766113, 208.4424591064, 480.0, 160.0000000110, 280.0825096303, 0.0, 19.5482546201, 24.4745411178),
    )
}
