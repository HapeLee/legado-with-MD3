package io.legado.app.feature.reader.core.transition.ioscurl.mesh.generation

import io.legado.app.feature.reader.core.transition.ioscurl.mesh.CurlMeshFrame

/**
 * Coordinates the reusable stages of page-mesh generation.
 *
 * Read the pipeline in this order when extending the algorithm:
 * 1. MeshBuildContext.kt validates inputs and constructs a [MeshBuildContext].
 * 2. [CurlProjectionBuilder] projects page corners and selects ordered curve samples.
 * 3. [PageSurfaceBuilder] creates the curved page and independent back-face strips.
 * 4. [ShadowBuilder] and the extensions in PenumbraBuilder.kt produce the cast shadow.
 * 5. Front strips use shadow bounds, then [DrawBatchAssembler] orders output.
 *
 * Working arrays are reused across frames, so a generator is not thread-safe.
 * Final batches copy their bytes and do not alias these working arrays.
 */
internal class MeshGenerator {
    private val projectionBuilder = CurlProjectionBuilder()
    private val surface = PageSurfaceBuilder()
    private val shadow = ShadowBuilder()
    private val output = DrawBatchAssembler()

    /**
     * Builds one output frame from a validated context.
     * Stage order matters: front strips require shadow bounds even when back rendering is disabled.
     */
    fun generateMesh(frame: MeshBuildContext): CurlMeshFrame {
        val projection = projectionBuilder.build(frame)
        val samples = projectionBuilder.samples
        surface.clear()
        surface.buildSurfaceStrips(projection, samples)
        surface.copyBackStrips(projection, samples)
        shadow.build(projection)
        surface.buildFrontStrips(projection, shadow.bounds)
        val batches = output.assemble(projection, samples, surface, shadow)
        return CurlMeshFrame(frame.source.width.toInt(), frame.source.height.toInt(), batches)
    }
}
