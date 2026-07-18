package io.legado.app.data.repository

import io.legado.app.data.dao.ReadAloudVoiceDao
import io.legado.app.data.entities.BookVoiceBindingEntity
import io.legado.app.data.entities.ReadAloudVoiceEntity
import io.legado.app.domain.gateway.ReadAloudVoiceGateway
import io.legado.app.domain.model.readaloud.BookVoiceBinding
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ReadAloudVoiceRepository(
    private val dao: ReadAloudVoiceDao,
) : ReadAloudVoiceGateway {

    override fun observeVoices(): Flow<List<ReadAloudVoice>> =
        dao.observeVoices().map { voices -> voices.map(ReadAloudVoiceEntity::toDomain) }

    override suspend fun getVoices(): List<ReadAloudVoice> = withContext(Dispatchers.IO) {
        dao.getVoices().map(ReadAloudVoiceEntity::toDomain)
    }

    override suspend fun getEnabledVoices(): List<ReadAloudVoice> = withContext(Dispatchers.IO) {
        dao.getEnabledVoices().map(ReadAloudVoiceEntity::toDomain)
    }

    override suspend fun getVoice(id: String): ReadAloudVoice? = withContext(Dispatchers.IO) {
        dao.getVoice(id)?.toDomain()
    }

    override suspend fun upsertVoice(voice: ReadAloudVoice) = withContext(Dispatchers.IO) {
        dao.upsertVoice(voice.toEntity())
    }

    override suspend fun deleteVoice(voice: ReadAloudVoice) = withContext(Dispatchers.IO) {
        dao.deleteVoice(voice.toEntity())
    }

    override fun observeBindings(bookUrl: String): Flow<List<BookVoiceBinding>> =
        dao.observeBindings(bookUrl).map { bindings ->
            bindings.map(BookVoiceBindingEntity::toDomain)
        }

    override suspend fun getBindings(bookUrl: String): List<BookVoiceBinding> =
        withContext(Dispatchers.IO) {
            dao.getBindings(bookUrl).map(BookVoiceBindingEntity::toDomain)
        }

    override suspend fun getBinding(
        bookUrl: String,
        subjectType: String,
        subjectId: String,
    ): BookVoiceBinding? = withContext(Dispatchers.IO) {
        dao.getBinding(bookUrl, subjectType, subjectId)?.toDomain()
    }

    override suspend fun upsertBinding(binding: BookVoiceBinding) = withContext(Dispatchers.IO) {
        dao.upsertBinding(binding.toEntity())
    }

    override suspend fun deleteBinding(binding: BookVoiceBinding) = withContext(Dispatchers.IO) {
        dao.deleteBinding(binding.toEntity())
    }
}

private fun ReadAloudVoiceEntity.toDomain() = ReadAloudVoice(
    id = id,
    engineType = engineType,
    engineId = engineId,
    speakerId = speakerId,
    displayName = displayName,
    traitsJson = traitsJson,
    emotionCatalogJson = emotionCatalogJson,
    managedBy = managedBy,
    enabled = enabled,
    available = available,
    revision = revision,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun ReadAloudVoice.toEntity() = ReadAloudVoiceEntity(
    id = id,
    engineType = engineType,
    engineId = engineId,
    speakerId = speakerId,
    displayName = displayName,
    traitsJson = traitsJson,
    emotionCatalogJson = emotionCatalogJson,
    managedBy = managedBy,
    enabled = enabled,
    available = available,
    revision = revision,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun BookVoiceBindingEntity.toDomain() = BookVoiceBinding(
    bookUrl = bookUrl,
    subjectType = subjectType,
    subjectId = subjectId,
    voiceId = voiceId,
    locked = locked,
    source = source,
    confidence = confidence,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun BookVoiceBinding.toEntity() = BookVoiceBindingEntity(
    bookUrl = bookUrl,
    subjectType = subjectType,
    subjectId = subjectId,
    voiceId = voiceId,
    locked = locked,
    source = source,
    confidence = confidence,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
