package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_provider_profiles")
data class AiProviderProfile(
    @PrimaryKey
    val id: String,
    val name: String,
    val protocol: String,
    val baseUrl: String,
    val modelsUrl: String? = null,
    val apiKey: String = "",
    val authType: String = AUTH_TYPE_BEARER,
    val secretRef: String? = null,
    val headersJson: String? = null,
    val chatPath: String? = null,
    val responsesPath: String? = null,
    val messagesPath: String? = null,
    val modelsPath: String? = null,
    val customHeadersJson: String? = null,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val AUTH_TYPE_NONE = "none"
        const val AUTH_TYPE_BEARER = "bearer"
        const val AUTH_TYPE_HEADER = "header"
    }
}
