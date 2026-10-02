package io.legado.app.domain.gateway

import io.legado.app.domain.model.ReadingProgress
import kotlinx.coroutines.flow.Flow

interface ReadingProgressGateway {
    val isConfigured: Boolean

    /**
     * 云端进度同步可用性的变更流。
     *
     * 只读一次 [isConfigured] 会拿到快照：后端的网络初始化晚到或失败时，
     * UI 侧的状态会永久停留在「未配置」。故显式建模成流。
     */
    val isConfiguredFlow: Flow<Boolean>

    /** 补齐缺失的云端配置（幂等；已初始化成功时为 no-op）。 */
    suspend fun ensureConfigured()

    suspend fun getProgress(name: String, author: String): ReadingProgress?
    suspend fun uploadProgress(progress: ReadingProgress): Long?
}
