package io.legado.app.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object VibrationUtils {

    /**
     * 触发单次震动
     * @param context 上下文
     * @param milliseconds 震动时长（毫秒）
     */
    fun vibrate(context: Context, milliseconds: Long) {
        val vibrator = getVibrator(context)
        vibrator.vibrate(
            VibrationEffect.createOneShot(
                milliseconds,
                VibrationEffect.DEFAULT_AMPLITUDE
            )
        )
    }

    /**
     * 触发带模式的震动
     * @param context 上下文
     * @param pattern 震动模式数组（如 longArrayOf(0, 200, 100, 300)）
     * @param repeat 重复次数（-1 为不重复）
     */
    fun vibratePattern(context: Context, pattern: LongArray, repeat: Int = -1) {
        val vibrator = getVibrator(context)
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat))
    }

    /**
     * 获取 Vibrator 实例（兼容 Android 12+）
     */
    private fun getVibrator(context: Context): Vibrator {
        return if (Build.VERSION.SDK_INT >= 31) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
}