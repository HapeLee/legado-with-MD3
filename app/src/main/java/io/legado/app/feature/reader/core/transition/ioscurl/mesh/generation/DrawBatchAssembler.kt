package io.legado.app.feature.reader.core.transition.ioscurl.mesh.generation

import kotlin.math.max
import io.legado.app.feature.reader.core.transition.ioscurl.mesh.CurlDrawBatch
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlRect

/**
 * Collects ordered renderer-neutral commands and snapshots working vertex buffers into owned byte arrays.
 */
internal class DrawBatchAssembler {
    var clip = CurlRect(0.0, 0.0, 0.0, 0.0)
    val batches = ArrayList<CurlDrawBatch>(MeshConstants.INITIAL_BATCH_CAPACITY)
    var coalesce = false
    var stripCount = 0
    var pendingCount = 0
    var pendingVertices = 0
    var pendingMode = 0
    val pendingBuffers = arrayOfNulls<VertexBuffer>(MeshConstants.MAX_DRAW_BATCHES)
    val pendingFirst = IntArray(MeshConstants.MAX_DRAW_BATCHES)
    val pendingSizes = IntArray(MeshConstants.MAX_DRAW_BATCHES)
}

/**
 * Emits front shading, front texture, cast shadow, back texture and back overlay in draw order.
 * Changing this order changes compositing; an empty clip produces no batches.
 */
internal fun DrawBatchAssembler.assemble(
    projection: CurlProjection,
    ranges: CurveSampleRanges,
    surface: PageSurfaceBuilder,
    shadow: ShadowBuilder,
    coalesceTriangleStrips: Boolean,
): List<CurlDrawBatch> {
    batches.clear()
    pendingCount = 0
    pendingVertices = 0
    stripCount = 0
    coalesce = coalesceTriangleStrips
    clip = projection.frame.clip
    val frontEnabled = projection.frame.input.frontEnabled
    val backEnabled = projection.frame.input.backEnabled
    if (clip.width <= 0 || clip.height <= 0) return emptyList()
    val backFirst = ranges.backStart * 2
    val backVertices = ranges.backCount * 2
    val fringes = ranges.backFringeVertices
    val backAlpha = projection.palette.back.alpha
    if (frontEnabled) {

        strip(DrawProgram.Color, surface.front.center, 0, surface.front.center.size)
        strip(DrawProgram.Color, surface.front.lower, 0, surface.front.lower.size)
        strip(DrawProgram.Color, surface.front.upper, 0, surface.front.upper.size)
        strip(
            DrawProgram.Texture,
            surface.curve.center,
            ranges.firstVisible * 2,
            max(ranges.backStart - ranges.firstVisible, 0) * 2,
        )
        strip(DrawProgram.Texture, surface.curve.lower, 0, backFirst)
        strip(DrawProgram.Texture, surface.curve.upper, 0, backFirst)
    }
    if (backEnabled) {
        if (shadow.topology.sampleCount != 0) drawShadowBands(shadow)
        strip(DrawProgram.Texture, surface.back.center, backFirst, backVertices)
        if (backAlpha == 0f) {
            strip(DrawProgram.Texture, surface.back.lower, backFirst, fringes)
            strip(DrawProgram.Texture, surface.back.upper, backFirst, fringes)
        }
        strip(DrawProgram.TextureAlpha, surface.curve.center, backFirst, backVertices)
        strip(DrawProgram.TextureAlpha, surface.curve.lower, backFirst, fringes)
        strip(DrawProgram.TextureAlpha, surface.curve.upper, backFirst, fringes)
    }
    flushStrips()
    return batches.toList()
}

/**
 * Emits each fade band as separate contour segments, followed by its optional cap and rounded corners.
 */
private fun DrawBatchAssembler.drawShadowBands(shadow: ShadowBuilder) {
    for (band in shadow.bands) {
        var first = 0
        for (segment in 0 until shadow.segmentCount) {
            val vertices = shadow.segmentCounts[segment] * 2
            strip(DrawProgram.Color, band.lower, first, vertices)
            strip(DrawProgram.Color, band.upper, first, vertices)
            first += vertices
        }
        if (shadow.capped) strip(DrawProgram.Color, band.cap, 0, 4)
        for (corner in shadow.corners.indices) strip(DrawProgram.Color, band.corners[corner], 0, 60)
    }
}

/**
 * Checks output budgets and snapshots a non-empty triangle-strip range into one draw command.
 */
private fun DrawBatchAssembler.strip(
    program: DrawProgram,
    vertices: VertexBuffer,
    first: Int,
    count: Int,
) {
    if (count == 0) return
    check(
        count.toLong() in 0..MeshConstants.MAX_VERTICES_PER_BATCH &&
            stripCount < MeshConstants.MAX_DRAW_BATCHES
    )
    stripCount++
    if (coalesce) {
        val padding = if (pendingCount == 0) 0 else 2 + (pendingVertices and 1)
        if (pendingCount > 0 &&
            (pendingMode != program.id ||
                pendingVertices.toLong() + padding + count > MeshConstants.MAX_VERTICES_PER_BATCH)
        ) flushStrips()
        pendingMode = program.id
        if (pendingCount > 0) pendingVertices += 2 + (pendingVertices and 1)
        pendingBuffers[pendingCount] = vertices
        pendingFirst[pendingCount] = first
        pendingSizes[pendingCount] = count
        pendingCount++
        pendingVertices += count
        return
    }
    batches +=
        CurlDrawBatch(
            2,
            program.id,
            3,
            true,
            count,
            count,
            vertices.bytes(first, count),
            null,
            clip,
        )
}

/** Packs each source range once, adding only degenerate connectors between ordered strips. */
private fun DrawBatchAssembler.flushStrips() {
    if (pendingCount == 0) return
    val bytes = ByteArray(pendingVertices * 64)
    var offset = 0
    for (index in 0 until pendingCount) {
        val vertices = checkNotNull(pendingBuffers[index])
        val first = pendingFirst[index]
        val count = pendingSizes[index]
        if (offset > 0) {
            val previous = offset - 64
            // The next strip must start at an even index to retain its original winding.
            if ((offset / 64) and 1 != 0) {
                bytes.copyInto(bytes, offset, previous, previous + 64)
                offset += 64
            }
            bytes.copyInto(bytes, offset, previous, previous + 64)
            offset += 64
            vertices.writeBytes(bytes, offset, first, 1)
            offset += 64
        }
        vertices.writeBytes(bytes, offset, first, count)
        offset += count * 64
        pendingBuffers[index] = null
    }
    check(offset == bytes.size)
    batches += CurlDrawBatch(2, pendingMode, 3, true, pendingVertices, pendingVertices, bytes, null, clip)
    pendingCount = 0
    pendingVertices = 0
}

/**
 * Stable renderer program codes, not platform shader or primitive enumerations.
 */
private enum class DrawProgram(val id: Int) {
    Color(0),
    Texture(3),
    TextureAlpha(12),
}
