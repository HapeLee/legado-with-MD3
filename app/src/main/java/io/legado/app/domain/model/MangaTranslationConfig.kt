package io.legado.app.domain.model

enum class MangaTranslationEngine { OFFLINE, AI }

/** A request keeps this snapshot even if the user edits their AI profile mid-flight. */
data class MangaTranslationConfig(
    val engine: MangaTranslationEngine = MangaTranslationEngine.OFFLINE,
    val preset: AiTaskPresetConfig? = null,
) {
    override fun toString(): String = "MangaTranslationConfig(engine=$engine, model=${preset?.model?.id})"

    /** Deliberately excludes credentials and headers. Never use the full preset in diagnostics. */
    fun cacheIdentity(): String = buildString {
        append("manga-text-v1:").append(engine.name)
        preset?.let {
            append(listOf(it.model.provider.protocol, it.model.provider.baseUrl,
                it.model.provider.chatPath, it.model.provider.responsesPath, it.model.provider.messagesPath,
                it.model.modelId, it.promptTemplate, it.params.toString()).joinToString("\u0000"))
        }
    }
}

enum class MangaTranslationFailure { CONFIGURATION, AUTH, RATE_LIMIT, TIMEOUT, FORMAT, TOO_LARGE, NETWORK }

class MangaTranslationException(val failure: MangaTranslationFailure) : Exception(failure.name)
