package io.legado.app.data.local.preferences

import androidx.compose.runtime.Stable

/**
 * 全应用设置的“唯一真理模型” - 强类型、不可变、业务语义化
 */
@Stable
data class AppSettings(
    val darkTheme: Boolean = false,
    val language: String = "auto",
    val readAloudSpeed: Int = 5,
    val textWeight: Int = 1,
    val showBrightnessView: Boolean = true
)
