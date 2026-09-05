package io.legado.app.domain.model.readaloud

data class CloudTtsVoiceConfig(
    val locale: String = "",
    val style: String = "",
    val role: String = "",
    val instructions: String = "",
    val automaticEmotion: Boolean? = null,
    val characterPersonality: Boolean? = null,
    val thoughtPerformance: Boolean? = null,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val volume: Float = 1f,
    val format: String = "mp3",
    val sampleRate: Int? = null,
)
