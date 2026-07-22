package io.legado.app.data.repository

import io.legado.app.data.dao.CloudTtsEngineDao
import io.legado.app.data.entities.CloudTtsEngineEntity
import io.legado.app.data.security.CloudTtsCredentialCipher
import io.legado.app.domain.gateway.CloudTtsEngineGateway
import io.legado.app.domain.model.readaloud.CloudTtsEngine
import io.legado.app.domain.model.readaloud.CloudTtsProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

class CloudTtsEngineRepository(
    private val dao: CloudTtsEngineDao,
    private val credentialCipher: CloudTtsCredentialCipher,
) : CloudTtsEngineGateway {
    override fun observeAll(): Flow<List<CloudTtsEngine>> =
        dao.observeAll()
            .onEach(::encryptLegacyCredentials)
            .map { engines -> engines.map(::toDomain) }

    override suspend fun getAll(): List<CloudTtsEngine> = withContext(Dispatchers.IO) {
        dao.getAll().also { encryptLegacyCredentials(it) }.map(::toDomain)
    }

    override suspend fun get(id: String): CloudTtsEngine? = withContext(Dispatchers.IO) {
        dao.get(id)?.let { engine ->
            val encrypted = engine.encryptCredentials(credentialCipher)
            if (encrypted != engine) dao.updateCredentials(engine, encrypted)
            toDomain(engine)
        }
    }

    override suspend fun upsert(engine: CloudTtsEngine) = withContext(Dispatchers.IO) {
        dao.upsert(engine.toEntity(credentialCipher))
    }

    override suspend fun delete(engine: CloudTtsEngine) = withContext(Dispatchers.IO) {
        dao.delete(engine.toEntity(credentialCipher))
    }

    private fun toDomain(entity: CloudTtsEngineEntity) = entity.toDomain(credentialCipher)

    private suspend fun encryptLegacyCredentials(engines: List<CloudTtsEngineEntity>) {
        engines.forEach { engine ->
            val encrypted = engine.encryptCredentials(credentialCipher)
            if (encrypted != engine) dao.updateCredentials(engine, encrypted)
        }
    }

    private suspend fun CloudTtsEngineDao.updateCredentials(
        old: CloudTtsEngineEntity,
        new: CloudTtsEngineEntity,
    ) = updateCredentials(
        id = old.id,
        oldApiKey = old.apiKey,
        oldSecretKey = old.secretKey,
        newApiKey = new.apiKey,
        newSecretKey = new.secretKey,
    )
}

private fun CloudTtsEngineEntity.encryptCredentials(cipher: CloudTtsCredentialCipher) = copy(
    apiKey = cipher.encrypt(apiKey),
    secretKey = cipher.encrypt(secretKey),
)

private fun CloudTtsEngineEntity.toDomain(cipher: CloudTtsCredentialCipher) = CloudTtsEngine(
    id = id,
    name = name,
    provider = CloudTtsProviderType.fromStorage(provider),
    baseUrl = baseUrl,
    apiKey = cipher.decrypt(apiKey),
    secretKey = cipher.decrypt(secretKey),
    region = region,
    appId = appId,
    model = model,
    optionsJson = optionsJson,
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun CloudTtsEngine.toEntity(cipher: CloudTtsCredentialCipher) = CloudTtsEngineEntity(
    id = id,
    name = name,
    provider = provider.storageValue,
    baseUrl = baseUrl,
    apiKey = cipher.encrypt(apiKey),
    secretKey = cipher.encrypt(secretKey),
    region = region,
    appId = appId,
    model = model,
    optionsJson = optionsJson,
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
