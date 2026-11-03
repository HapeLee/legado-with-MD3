package io.legado.app.feature.reader.core.transition.ioscurl.mesh.generation

/**
 * Input limits, numerical tolerances, output budgets and visual calibration values.
 * Float-to-Double constants deliberately preserve Float precision used by the geometry.
 * Changing fade arrays requires matching band counts in ShadowBuilder and PenumbraBuilder.
 */
internal object MeshConstants {
    // Validation limits for logical dimensions, radius and output scale.
    const val MAX_DIMENSION = 4096.0
    const val MIN_SCALE = 0.1
    const val MAX_SCALE = 4.0
    // Near-axis tolerances prevent unstable reciprocals when intersecting cross-sections.
    const val AXIS_SNAP_EPSILON = 0.001
    val TANGENT_SNAP_EPSILON = 0.001f.toDouble()
    // Shadow intensity limits in the 0..255 channel domain; scaled down early in a turn.
    const val INNER_SAMPLE_LIMIT = 170.0
    const val OUTER_SAMPLE_LIMIT = 180.0
    val SAMPLE_LIMIT_RATIO = 0.4f.toDouble()
    // Hard output budgets checked before emitting a draw batch.
    const val MAX_VERTICES_PER_BATCH = 10_000L
    const val MAX_DRAW_BATCHES = 128
    const val INITIAL_BATCH_CAPACITY = 32
    // Premultiplied color quantization and normalization scales.
    const val CHANNEL_MAX = 255.0
    const val NORMALIZED_CHANNEL_SCALE = 1f / 255f
    const val NORMALIZED_CHANNEL_SCALE_DOUBLE = 1.0 / 255.0
    // Three outward penumbra bands: opacity weights and relative spread positions.
    val FADE_WEIGHTS = doubleArrayOf(0.5, 0.135f.toDouble(), 0.0)
    val FADE_STEPS = doubleArrayOf(0.33f.toDouble(), 0.66f.toDouble(), 1.0)
}
