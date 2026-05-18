package io.legado.app.ui.widget.components.effect

internal object BgEffectConfig {

    internal class Config(
        val points: FloatArray,
        val colors1: FloatArray,
        val colors2: FloatArray,
        val colors3: FloatArray,
        val colorInterpPeriod: Float,
        val lightOffset: Float,
        val saturateOffset: Float,
        val pointOffset: Float,
    )

    // --- 优化配色 (基于用户提供的颜色调优) ---
    private val C1_L = floatArrayOf(0.68f, 0.65f, 0.74f, 1.0f) // 亮色优化 1
    private val C2_L = floatArrayOf(0.92f, 0.62f, 0.73f, 1.0f) // 亮色优化 2
    private val C3_L = floatArrayOf(0.98f, 0.98f, 0.98f, 1.0f) // 亮色优化 3
    private val CM_L = floatArrayOf(0.85f, 0.80f, 0.90f, 1.0f) // 亮色过渡

    private val C1_D = floatArrayOf(0.16f, 0.14f, 0.20f, 1.0f) // 暗色优化 1
    private val C2_D = floatArrayOf(0.49f, 0.17f, 0.29f, 1.0f) // 暗色优化 2
    private val C3_D = floatArrayOf(0.15f, 0.15f, 0.15f, 1.0f) // 暗色优化 3
    private val CM_D = floatArrayOf(0.25f, 0.15f, 0.28f, 1.0f) // 暗色过渡

    // OS3 配色组合 (A/B/C 三阶段循环实现流光感)
    private val COLORS_L_A = floatArrayOf(
        C1_L[0],
        C1_L[1],
        C1_L[2],
        C1_L[3],
        C2_L[0],
        C2_L[1],
        C2_L[2],
        C2_L[3],
        C3_L[0],
        C3_L[1],
        C3_L[2],
        C3_L[3],
        CM_L[0],
        CM_L[1],
        CM_L[2],
        CM_L[3]
    )
    private val COLORS_L_B = floatArrayOf(
        C2_L[0],
        C2_L[1],
        C2_L[2],
        C2_L[3],
        C3_L[0],
        C3_L[1],
        C3_L[2],
        C3_L[3],
        CM_L[0],
        CM_L[1],
        CM_L[2],
        CM_L[3],
        C1_L[0],
        C1_L[1],
        C1_L[2],
        C1_L[3]
    )
    private val COLORS_L_C = floatArrayOf(
        C3_L[0],
        C3_L[1],
        C3_L[2],
        C3_L[3],
        CM_L[0],
        CM_L[1],
        CM_L[2],
        CM_L[3],
        C1_L[0],
        C1_L[1],
        C1_L[2],
        C1_L[3],
        C2_L[0],
        C2_L[1],
        C2_L[2],
        C2_L[3]
    )

    private val COLORS_D_A = floatArrayOf(
        C1_D[0],
        C1_D[1],
        C1_D[2],
        C1_D[3],
        C2_D[0],
        C2_D[1],
        C2_D[2],
        C2_D[3],
        C3_D[0],
        C3_D[1],
        C3_D[2],
        C3_D[3],
        CM_D[0],
        CM_D[1],
        CM_D[2],
        CM_D[3]
    )
    private val COLORS_D_B = floatArrayOf(
        C2_D[0],
        C2_D[1],
        C2_D[2],
        C2_D[3],
        C3_D[0],
        C3_D[1],
        C3_D[2],
        C3_D[3],
        CM_D[0],
        CM_D[1],
        CM_D[2],
        CM_D[3],
        C1_D[0],
        C1_D[1],
        C1_D[2],
        C1_D[3]
    )
    private val COLORS_D_C = floatArrayOf(
        C3_D[0],
        C3_D[1],
        C3_D[2],
        C3_D[3],
        CM_D[0],
        CM_D[1],
        CM_D[2],
        CM_D[3],
        C1_D[0],
        C1_D[1],
        C1_D[2],
        C1_D[3],
        C2_D[0],
        C2_D[1],
        C2_D[2],
        C2_D[3]
    )

    // OS3 Configs (参数完全对齐原版 OS3)
    private val OS3_PHONE_LIGHT = Config(
        points = floatArrayOf(
            0.8f,
            0.2f,
            1.0f,
            0.8f,
            0.9f,
            1.0f,
            0.2f,
            0.9f,
            1.0f,
            0.2f,
            0.2f,
            1.0f
        ),
        colors1 = COLORS_L_A, colors2 = COLORS_L_B, colors3 = COLORS_L_C,
        colorInterpPeriod = 5.0f, lightOffset = 0.1f, saturateOffset = 0.2f, pointOffset = 0.2f
    )
    private val OS3_PHONE_DARK = Config(
        points = floatArrayOf(
            0.8f,
            0.2f,
            1.0f,
            0.8f,
            0.9f,
            1.0f,
            0.2f,
            0.9f,
            1.0f,
            0.2f,
            0.2f,
            1.0f
        ),
        colors1 = COLORS_D_A, colors2 = COLORS_D_B, colors3 = COLORS_D_C,
        colorInterpPeriod = 8.0f, lightOffset = 0.0f, saturateOffset = 0.17f, pointOffset = 0.4f
    )

    internal fun get(
        deviceType: DeviceType,
        isDark: Boolean,
        isOs3: Boolean = true, // 默认且仅使用 OS3 逻辑
    ): Config = if (!isDark) OS3_PHONE_LIGHT else OS3_PHONE_DARK
}
