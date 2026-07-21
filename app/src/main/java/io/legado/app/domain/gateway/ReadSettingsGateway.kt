package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.ReadSettings
import kotlinx.coroutines.flow.Flow

interface ReadSettingsGateway {
    val currentSettings: ReadSettings
    val settings: Flow<ReadSettings>

    /**
     * 仅持久化 gateway 实现映射声明的 45 个配置键。
     * [ReadSettings] 是包含遗留阅读菜单/样式字段的读模型超集；未纳入 gateway 映射的字段
     * 仍须通过对应的遗留 Repository setter 写入，不能在此处 copy 后期待自动落盘。
     */
    suspend fun update(transform: (ReadSettings) -> ReadSettings)
}
