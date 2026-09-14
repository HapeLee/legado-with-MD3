package io.legado.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import io.legado.app.domain.model.settings.AppUiConfiguration

/**
 * Compose 树当前生效的只读应用界面配置。
 *
 * 配置由 Activity 根部从 [io.legado.app.domain.gateway.AppUiConfigurationGateway] 收集后提供，
 * 子组件只能读取不可变快照，不能通过 CompositionLocal 获取写依赖。
 */
val LocalAppUiConfiguration = compositionLocalOf { AppUiConfiguration() }
