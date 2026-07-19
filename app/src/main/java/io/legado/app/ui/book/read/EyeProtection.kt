package io.legado.app.ui.book.read

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix


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
        compositingStrategy = CompositingStrategy.Offscreen
        colorFilter = cachedColorFilter
    }
}
