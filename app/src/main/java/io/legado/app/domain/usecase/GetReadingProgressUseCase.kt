package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.ReadingProgressGateway
import io.legado.app.domain.model.ReadingProgress
import kotlinx.coroutines.flow.Flow

class GetReadingProgressUseCase(
    private val readingProgressGateway: ReadingProgressGateway
) {

    val isConfigured: Boolean
        get() = readingProgressGateway.isConfigured

    /** 云端进度同步可用性变更流；UI 侧状态不得只依赖 [isConfigured] 的一次性快照。 */
    val isConfiguredFlow: Flow<Boolean>
        get() = readingProgressGateway.isConfiguredFlow

    /** 补齐缺失的云端配置（幂等；已初始化成功时为 no-op）。 */
    suspend fun ensureConfigured() {
        readingProgressGateway.ensureConfigured()
    }

    suspend fun execute(name: String, author: String): ReadingProgress? {
        return readingProgressGateway.getProgress(name, author)
    }
}
