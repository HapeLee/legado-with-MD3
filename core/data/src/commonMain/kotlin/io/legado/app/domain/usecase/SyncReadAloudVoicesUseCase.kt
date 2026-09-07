package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.ReadAloudVoiceGateway
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.model.readaloud.SpeechIdentity
import io.legado.app.domain.model.readaloud.VoiceCatalogEntry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SyncReadAloudVoicesResult(
    val inserted: Int,
    val updated: Int,
    val unavailable: Int,
)

class SyncReadAloudVoicesUseCase(
    private val voiceGateway: ReadAloudVoiceGateway,
) {

    private val syncMutex = Mutex()

    suspend operator fun invoke(
        entries: List<VoiceCatalogEntry>,
        managedSources: Set<String>,
        removeMissingEngineTypes: Set<String> = emptySet(),
        now: Long = System.currentTimeMillis(),
    ): SyncReadAloudVoicesResult = syncMutex.withLock {
        val existing = voiceGateway.getVoices()
        val existingById = existing.associateBy(ReadAloudVoice::id)
        val incoming = entries
            .distinctBy { Triple(it.engineType, it.engineId, it.speakerId) }
            .map { entry -> entry to entry.stableId() }
        val incomingIds = incoming.mapTo(mutableSetOf()) { it.second }
        var inserted = 0
        var updated = 0

        incoming.forEach { (entry, id) ->
            val old = existingById[id]
            val metadataChanged = old == null ||
                old.engineType != entry.engineType ||
                old.engineId != entry.engineId ||
                old.speakerId != entry.speakerId ||
                old.displayName != entry.displayName ||
                old.traitsJson != entry.traitsJson ||
                old.emotionCatalogJson != entry.emotionCatalogJson ||
                old.managedBy != entry.managedBy
            val revision = when {
                old == null -> entry.sourceRevision
                metadataChanged -> maxOf(entry.sourceRevision, old.revision + 1)
                else -> maxOf(entry.sourceRevision, old.revision)
            }
            val voice = ReadAloudVoice(
                id = id,
                engineType = entry.engineType,
                engineId = entry.engineId,
                speakerId = entry.speakerId,
                displayName = entry.displayName,
                traitsJson = entry.traitsJson,
                emotionCatalogJson = entry.emotionCatalogJson,
                managedBy = entry.managedBy,
                enabled = old?.enabled ?: true,
                available = true,
                revision = revision,
                createdAt = old?.createdAt ?: now,
                updatedAt = if (old == null || metadataChanged || !old.available || revision != old.revision) {
                    now
                } else {
                    old.updatedAt
                },
            )
            if (old == null) {
                inserted++
                voiceGateway.upsertVoice(voice)
            } else if (voice != old) {
                updated++
                voiceGateway.upsertVoice(voice)
            }
        }

        var unavailable = 0
        existing.asSequence()
            .filter { it.managedBy in managedSources }
            .filter { it.id !in incomingIds }
            .forEach { voice ->
                if (voice.engineType in removeMissingEngineTypes) {
                    unavailable++
                    voiceGateway.deleteVoice(voice)
                } else if (voice.available) {
                    unavailable++
                    voiceGateway.upsertVoice(voice.copy(available = false, updatedAt = now))
                }
            }
        SyncReadAloudVoicesResult(inserted, updated, unavailable)
    }

    private fun VoiceCatalogEntry.stableId(): String =
        SpeechIdentity.voiceId(engineType, engineId, speakerId)
}
