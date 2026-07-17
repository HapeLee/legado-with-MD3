package io.legado.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import io.legado.app.data.local.preferences.AppSettings

/**
 * 全局可访问的静态配置快照流
 */
val LocalAppSettings = staticCompositionLocalOf { AppSettings() }
