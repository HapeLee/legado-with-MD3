package io.legado.app.ui.book.read

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix
import kotlinx.coroutines.delay
import java.time.LocalTime


object EyeProtection {
    const val MIN_INTENSITY = 0
    const val MAX_INTENSITY = 100

    private const val MIN_FILTER_TEMPERATURE = 2596
    private const val MAX_FILTER_TEMPERATURE = 5500

    private val coefficients = floatArrayOf(
        0f, 0f, 1f,
        -9.623533E-9f, 1.5304548E-4f, 0.39078277f,
        -1.8935904E-8f, 3.024122E-4f, -0.1986509f,
    )

    fun temperatureForIntensity(intensity: Int): Int {
        val normalized = intensity.coerceIn(MIN_INTENSITY, MAX_INTENSITY)
        return (MAX_FILTER_TEMPERATURE - (MAX_FILTER_TEMPERATURE - MIN_FILTER_TEMPERATURE) * normalized / MAX_INTENSITY.toFloat())
            .toInt()
    }

    fun matrixValuesForIntensity(intensity: Int): FloatArray {
        if (intensity <= MIN_INTENSITY) return IDENTITY_MATRIX.copyOf()

        val cct = temperatureForIntensity(intensity).toFloat()
        val cctSquared = cct * cct
        val red = cctSquared * coefficients[0] + cct * coefficients[1] + coefficients[2]
        val green = cctSquared * coefficients[3] + cct * coefficients[4] + coefficients[5]
        val blue = cctSquared * coefficients[6] + cct * coefficients[7] + coefficients[8]
        return floatArrayOf(
            red, 0f, 0f, 0f, 0f,
            0f, green, 0f, 0f, 0f,
            0f, 0f, blue, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    fun syncEnabledForNight(isNight: Boolean, autoNight: Boolean): Boolean? =
        if (autoNight) isNight else null

    /**
     * 计算护眼在给定时刻是否应生效。
     * - 总开关关闭 → 永远不生效；
     * - 未启用定时 → 只要总开关开就生效；
     * - 启用定时但时间无法解析 → 退化为仅按总开关生效；
     * - 启用定时 → 仅在 [startTime, endTime) 时段内生效（支持跨零点，如 22:00–07:00）。
     */
    fun isActiveAt(
        enabled: Boolean,
        schedule: Boolean,
        startTime: String,
        endTime: String,
        now: LocalTime,
    ): Boolean {
        if (!enabled) return false
        if (!schedule) return true
        val start = parseTime(startTime) ?: return true
        val end = parseTime(endTime) ?: return true
        if (start == end) return true
        return if (start < end) {
            now >= start && now < end
        } else {
            now >= start || now < end
        }
    }

    private fun parseTime(value: String): LocalTime? =
        runCatching { LocalTime.parse(value.trim()) }.getOrNull()

    private val IDENTITY_MATRIX = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}

@Composable
fun Modifier.eyeProtectionColorFilter(
    enabled: Boolean,
    intensity: Int,
): Modifier {
    val cachedColorFilter = remember(enabled, intensity) {
        if (enabled) {
            ColorFilter.colorMatrix(
                ComposeColorMatrix(EyeProtection.matrixValuesForIntensity(intensity))
            )
        } else {
            null
        }
    }
    if (cachedColorFilter == null) return this

    return graphicsLayer {
        colorFilter = cachedColorFilter
    }
}

/**
 * 解析护眼当前是否生效，启用定时时按分钟自动重算，使时段边界能实时切换。
 */
@Composable
fun rememberEyeProtectionActive(
    enabled: Boolean,
    schedule: Boolean,
    startTime: String,
    endTime: String,
): Boolean {
    if (!enabled) return false
    if (!schedule) return true
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(startTime, endTime) {
        while (true) {
            now = LocalTime.now()
            delay(60_000L)
        }
    }
    return EyeProtection.isActiveAt(enabled, schedule, startTime, endTime, now)
}
