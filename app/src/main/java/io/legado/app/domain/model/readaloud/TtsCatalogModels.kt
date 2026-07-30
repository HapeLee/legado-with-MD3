package io.legado.app.domain.model.readaloud

/** A configured synthesis engine. Native voices are discovered separately from user presets. */
data class TtsEngineDescriptor(
    val id: String,
    val kind: TtsEngineKind,
    val sourceId: String,
    val displayName: String,
    val providerName: String = "",
    val supportsVoiceDiscovery: Boolean = false,
    val loginUrl: String = "",
)

enum class TtsEngineKind {
    System,
    Cloud,
    Http,
}

/** A voice supplied by an engine. This is not persisted until it becomes a [ReadAloudVoice] preset. */
data class TtsNativeVoice(
    val id: String,
    val engineId: String,
    val displayName: String,
    val locale: String = "",
    val gender: String = "",
    val quality: Int? = null,
    val latency: Int? = null,
    val requiresNetwork: Boolean = false,
    val styles: List<String> = emptyList(),
    val roles: List<String> = emptyList(),
    val supportedModels: List<String> = emptyList(),
    val sampleRate: Int? = null,
)
