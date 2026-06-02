package io.legado.app.ui.book.read

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.Paint
import io.legado.app.ui.config.labConfig.LabConfig
import io.legado.app.ui.config.themeConfig.ThemeConfig
import java.util.Calendar

/**
 * 护眼色温工具类
 * 提供色温（暖色）转 ColorMatrix 以及覆盖层颜色计算
 *
 * 激活条件：
 * Lab 总开关开启 && 实验室护眼模式开启 && 主题护眼模式开启 &&
 * (未开启定时 OR 当前时间在定时区间内)
 */
object EyeProtectionHelper {

    /**
     * 判断当前时间是否在护眼定时区间内
     */
    fun isInSchedule(): Boolean {
        if (!ThemeConfig.eyeProtectionSchedule) return true
        val now = Calendar.getInstance()
        val curMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val start = parseTime(ThemeConfig.eyeProtectionStartTime)
        val end = parseTime(ThemeConfig.eyeProtectionEndTime)
        if (start == null || end == null) return true
        return if (start == end) {
            true
        } else if (start < end) {
            curMinute in start..end
        } else {
            curMinute >= start || curMinute <= end
        }
    }

    private fun parseTime(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        return try {
            val parts = value.split(":")
            if (parts.size < 2) return null
            val h = parts[0].trim().toInt()
            val m = parts[1].trim().toInt()
            h * 60 + m
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 是否应该激活护眼色温
     * 仅当总开关打开，并且满足定时条件时返回 true
     */
    fun isActive(): Boolean {
        if (!LabConfig.labEnabled) return false
        if (!LabConfig.eyeProtection) return false
        if (!ThemeConfig.eyeProtectionEnabled) return false
        return isInSchedule()
    }

    /**
     * 根据当前色温设置获取暖色覆盖颜色
     * temperature: 0-100，0 不变，100 最暖
     */
    fun overlayColor(): Int {
        if (!isActive()) return Color.TRANSPARENT
        val t = ThemeConfig.colorTemperature.coerceIn(0, 100)
        if (t == 0) return Color.TRANSPARENT
        val alpha = (t / 100f * 90f).toInt().coerceIn(0, 255)
        return Color.argb(alpha, 255, 180, 90)
    }

    /**
     * 创建色温 ColorMatrix（暖色调，降低蓝光通道）
     */
    fun buildColorMatrix(): ColorMatrix {
        val t = if (isActive()) ThemeConfig.colorTemperature.coerceIn(0, 100) else 0
        val ratio = t / 100f
        val r = 1f
        val g = 1f - 0.45f * ratio
        val b = 1f - 0.85f * ratio
        val brightness = 1f - 0.05f * ratio
        val matrix = ColorMatrix(
            floatArrayOf(
                r * brightness, 0f, 0f, 0f, 8f * ratio,
                0f, g * brightness, 0f, 0f, 4f * ratio,
                0f, 0f, b * brightness, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return matrix
    }

    /**
     * 为 Paint 应用护眼色温 ColorFilter
     */
    fun applyColorFilter(paint: Paint) {
        if (isActive()) {
            paint.colorFilter = android.graphics.ColorMatrixColorFilter(buildColorMatrix())
        } else {
            paint.colorFilter = null
        }
    }
}
