package io.legado.app.domain.gateway

import io.legado.app.domain.model.readaloud.BookVoiceBinding
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import kotlinx.coroutines.flow.Flow

interface ReadAloudVoiceGateway {
    /** 在一致的存储事务中同步目录，与引擎的导入/删除共用事务边界。 */
    suspend fun <T> withTransaction(block: suspend () -> T): T
    suspend fun countExcludedVoices(): Int
    fun observeVoices(): Flow<List<ReadAloudVoice>>
    suspend fun getVoices(): List<ReadAloudVoice>
    suspend fun getEnabledVoices(): List<ReadAloudVoice>
    suspend fun getVoice(id: String): ReadAloudVoice?
    suspend fun upsertVoice(voice: ReadAloudVoice)
    suspend fun deleteVoice(voice: ReadAloudVoice)

    fun observeBindings(bookUrl: String): Flow<List<BookVoiceBinding>>
    suspend fun getBindings(bookUrl: String): List<BookVoiceBinding>
    suspend fun getBinding(
        bookUrl: String,
        subjectType: String,
        subjectId: String,
    ): BookVoiceBinding?

    suspend fun upsertBinding(binding: BookVoiceBinding)
    suspend fun deleteBinding(binding: BookVoiceBinding)
}
