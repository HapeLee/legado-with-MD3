package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.ThemeSettings
import kotlinx.coroutines.flow.Flow

interface ThemeSettingsGateway {
    val currentSettings: ThemeSettings
    val settings: Flow<ThemeSettings>

    /**
     * 仅持久化 gateway 映射声明的 59 个配置键。
     * [ThemeSettings] 是包含主题包专用字段的 61 字段读模型；[ThemeSettings.customMode] 与
     * [ThemeSettings.bookInfoInputColor] 仍须由主题包事务写入，不能在此处 copy 后期待自动落盘。
     */
    suspend fun update(transform: (ThemeSettings) -> ThemeSettings)
}
