package io.legado.app.domain.usecase

import io.legado.app.domain.model.readaloud.HttpTtsCatalogDraft
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.model.readaloud.VoiceCatalogEntry
import io.legado.app.utils.GSON

/**
 * 把 httpTTS 源自带的发言人 / 情绪目录同步进 `read_aloud_voices`。
 *
 * 目录的权威副本是表里那些行，不是源 JSON：导入之后改引擎、删引擎都只动表，
 * 源 JSON 不回写。音色 id 由 `engineType + engineId + speakerId` 推出，同一条源
 * 重复导入不会翻倍。
 *
 * 回收范围限定在 [engineId] 这一个引擎上——[SyncReadAloudVoicesUseCase] 默认按
 * `managedBy` 全局回收，直接用的话导入 B 引擎会把 A 引擎的目录行当成"本次没出现"删掉。
 */
class SyncHttpTtsCatalogUseCase(
    private val syncReadAloudVoices: SyncReadAloudVoicesUseCase,
) {

    /**
     * 源没带目录时什么都不做。
     *
     * 不能顺势清空：备份里没有 `speakersJson` 字段（我们自己的备份就不写它），
     * 恢复时会把每个源都当成「新版去掉了目录」，把用户已有的目录整批删掉。
     * 清理只在引擎真的没了的时候做，见 [clear]。
     */
    suspend operator fun invoke(
        engineId: String,
        draft: HttpTtsCatalogDraft,
        sourceRevision: Long = 0L,
    ): SyncReadAloudVoicesResult {
        if (draft.speakers.isEmpty()) return SyncReadAloudVoicesResult(0, 0, 0)
        return sync(
            engineId = engineId,
            entries = draft.speakers.map { speaker ->
                VoiceCatalogEntry(
                    engineType = ReadAloudVoice.ENGINE_HTTP,
                    engineId = engineId,
                    speakerId = speaker.toneID,
                    displayName = speaker.speakerName,
                    traitsJson = GSON.toJson(speaker.tags),
                    // 情绪在源里是引擎级的，整份挂到每个音色上：播放时按音色取，不用再回落引擎行
                    emotionCatalogJson = GSON.toJson(draft.emotions),
                    managedBy = ReadAloudVoice.MANAGED_BY_HTTP_SOURCE_CATALOG,
                    sourceRevision = sourceRevision,
                )
            },
        )
    }

    /** 引擎被删除时清掉它上一次导入留下的行，否则这些目录行会变成孤儿。 */
    suspend fun clear(engineId: String): SyncReadAloudVoicesResult =
        sync(engineId, entries = emptyList())

    private suspend fun sync(
        engineId: String,
        entries: List<VoiceCatalogEntry>,
    ): SyncReadAloudVoicesResult = syncReadAloudVoices(
        entries = entries,
        managedSources = setOf(ReadAloudVoice.MANAGED_BY_HTTP_SOURCE_CATALOG),
        removeMissingEngineTypes = setOf(ReadAloudVoice.ENGINE_HTTP),
        managedScope = { it.engineId == engineId },
    )
}
