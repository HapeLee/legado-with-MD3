package io.legado.app.utils

/**
 * M5-19d：把 ARGB 颜色转成 HSL。**纯算式，等价于 AndroidX 的
 * `androidx.core.graphics.ColorUtils.colorToHSL(int, float[])`**。
 *
 * 为什么要自带一份：`androidx.core.graphics.ColorUtils` 是 **Android-only** 制品
 * （`androidx.core:core`），进不了 `commonMain`。而 `themeConfig` 的两个地方要用它
 * （`TagColorGenerator` 求基准色相、`LabelColorManageSheet` 在取色后重算背景色），
 * 且共享层此前从未用过 `ColorUtils`（无先例可循）。
 *
 * 算法与 AndroidX 逐句对应（`delta == 0` 时 H/S 记为 0；`max == r` 那支带 `g < b` 的
 * 回绕修正；`s` 按 `l <= 0.5` 分成两支）。返回值语义同 AndroidX：
 * `[0] = H ∈ [0, 360)`、`[1] = S ∈ [0, 1]`、`[2] = L ∈ [0, 1]`。
 *
 * ⇒ 等价性由 `ColorHslTest` 的已知值用例锁住（红/绿/蓝/白/黑/灰的 H、S、L）。
 */
fun colorToHsl(color: Int): FloatArray {
    val r = ((color shr 16) and 0xFF) / 255f
    val g = ((color shr 8) and 0xFF) / 255f
    val b = (color and 0xFF) / 255f

    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val l = (max + min) / 2f

    val h: Float
    val s: Float
    if (delta == 0f) {
        // 无彩色：色相无定义（记 0），饱和度为 0
        h = 0f
        s = 0f
    } else {
        s = if (l <= 0.5f) delta / (max + min) else delta / (2f - max - min)
        h = when (max) {
            r -> ((g - b) / delta + if (g < b) 6f else 0f)
            g -> ((b - r) / delta + 2f)
            else -> ((r - g) / delta + 4f)
        } * 60f
    }
    return floatArrayOf(h, s, l)
}
