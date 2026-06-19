package io.legado.app.ui.book.read

import androidx.annotation.Keep
import java.util.UUID

@Keep
data class ReadAiBookHistory(
    val bookUrl: String,
    val bookName: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val currentSessionId: String = "",
    val sessions: List<ReadAiSession> = emptyList()
)

@Keep
data class ReadAiSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val chapterTitle: String = "",
    val chapterIndex: Int = -1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<ReadAiMessage> = emptyList()
)

@Keep
data class ReadAiMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    @Keep
    enum class Role {
        USER,
        ASSISTANT
    }
}
