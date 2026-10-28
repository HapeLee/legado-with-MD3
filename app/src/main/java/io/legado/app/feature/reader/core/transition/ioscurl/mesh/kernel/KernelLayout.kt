package io.legado.app.feature.reader.core.transition.ioscurl.mesh.kernel

/**
 * Byte offsets in the recovered numeric records. These describe data layout; they are not curve
 * parameters and must not be tuned to change appearance.
 */
internal object KernelLayout {
    const val DOUBLE_BYTES = 8L
    const val FLOAT_BYTES = 4L
    const val HALF_BITS = 16
    const val WORD_MASK = 0xffff_ffffL
    const val HALF_MASK = 0xffffL
    const val HALF_RGBA_WHITE = 0x3c00_3c00_3c00_3c00L
    const val POSITION_Z_ZERO_W_ONE = 0x3f80_0000_0000_0000L
    const val VERTEX_PAIR_INDEX_MASK = 0x01ff_ffff_ffff_ffffL

    object Workspace {
        const val FADE_WEIGHTS = 0x100L
        const val FADE_STEPS = 0x120L
        const val INNER_CLAMP = 0x140L
        const val OUTER_CLAMP = 0x160L
        const val CONTROL = 0x200L
        const val MATERIAL = 0x400L
        const val CONTEXT = 0x1000L
        const val LAYER_STATE = 0x2000L
        const val DRAW_STATE = 0x2400L
        const val SURFACE = 0x2800L
        const val CALLBACK_TABLE = 0x2c00L
        const val SCRATCH = 0x4000L
        const val ARENA_BYTES = 0x30000
        const val INTEGER_SLOTS = 31
        const val VECTOR_SLOTS = 32
    }

    object Control {
        const val TIME = 0L
        const val ANGLE = 8L
        const val RADIUS = 16L
        const val START_ANGLE = 24L
        const val END_ANGLE = 32L
        const val NORMAL_X = 40L
        const val NORMAL_Y = 48L
        const val TANGENT_X = 56L
        const val TANGENT_Y = 64L
        const val SHADOW_BOUNDS = 72L
    }

    object Material {
        const val FRONT_ENABLED = 0L
        const val BACK_ENABLED = 1L
        const val INVERSE_TRANSFORM = 8L
        const val INVERSE_X_FROM_X = 8L
        const val INVERSE_Y_FROM_X = 16L
        const val INVERSE_X_FROM_Y = 40L
        const val INVERSE_Y_FROM_Y = 48L
        const val INVERSE_Z_SCALE = 88L
        const val INVERSE_W_SCALE = 128L
        const val PAGE_BOUNDS = 136L
        const val SOURCE_BOUNDS = 168L
        const val NORMAL_X = 200L
        const val NORMAL_Y = 208L
        const val TANGENT_X = 216L
        const val TANGENT_Y = 224L
        const val FRONT_COLOR = 232L
        const val FRONT_MULTIPLIER = 240L
        const val BACK_COLOR = 248L
        const val BACK_SHADE = 256L
        const val LIGHTING_ENABLED = 264L
        const val SHADOW_COLOR = 272L
        const val OPACITY = 280L
    }

    object Context {
        const val CALLBACK_TABLE = 0L
        const val DRAW_STATE = 8L
        const val LAYER_STATE = 224L
        const val FRAME_COUNT = 232L
        const val ACTIVE_SURFACE = 552L
    }

    object DrawState {
        const val PROGRAM = 16L
        const val BLEND_FUNCTION = 416L
        const val FLAGS = 417L
    }

    object LayerState {
        const val COLOR_MULTIPLIER = 32L
    }

    object Surface {
        const val PIXEL_BOUNDS = 32L
    }

    object Matrix {
        const val X_FROM_X = 0L
        const val Y_FROM_X = 8L
        const val W_FROM_X = 24L
        const val X_FROM_Y = 32L
        const val Y_FROM_Y = 40L
        const val W_FROM_Y = 56L
        const val TRANSLATION_X = 96L
        const val TRANSLATION_Y = 104L
        const val W_SCALE = 120L
    }

    object Rect {
        const val X = 0L
        const val Y = 8L
        const val WIDTH = 16L
        const val HEIGHT = 24L
    }

    object Vertex {
        const val STRIDE_BYTES = 64
        const val PAIR_BYTES = 128L
        const val POSITION_Y = 4L
        const val POSITION_Z = 8L
        const val POSITION_W = 12L
        const val TEXTURE_U = 16L
        const val TEXTURE_V = 20L
        const val COLOR = 40L
    }
}

/** Appearance / safety constants, kept separate from the binary record layout. */
internal object KernelTuning {
    const val MAX_DIMENSION = 4096.0
    const val MIN_SCALE = 0.1
    const val MAX_SCALE = 4.0
    const val AXIS_SNAP_EPSILON = 0.001
    val TANGENT_SNAP_EPSILON = 0.001f.toDouble()
    const val INNER_SAMPLE_LIMIT = 170.0
    const val OUTER_SAMPLE_LIMIT = 180.0
    val SAMPLE_LIMIT_RATIO = 0.4f.toDouble()
    const val MAX_VERTICES_PER_BATCH = 10_000L
    const val MAX_DRAW_BATCHES = 128
    const val INITIAL_BATCH_CAPACITY = 32
    const val CHANNEL_MAX = 255.0
    const val NORMALIZED_CHANNEL_SCALE = 1f / 255f
    const val NORMALIZED_CHANNEL_SCALE_DOUBLE = 1.0 / 255.0
    const val SHADOW_PLANE_Z = 1.0 / 128.0
    val FADE_WEIGHTS = doubleArrayOf(0.5, 0.135f.toDouble(), 0.0)
    val FADE_STEPS = doubleArrayOf(0.33f.toDouble(), 0.66f.toDouble(), 1.0)
}
