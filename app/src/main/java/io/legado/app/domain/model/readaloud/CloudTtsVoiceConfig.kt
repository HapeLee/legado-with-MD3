package io.legado.app.domain.model.readaloud

import com.google.gson.annotations.SerializedName

/**
 * `traitsJson` 的落盘结构；键名写死在 [SerializedName]，否则 R8 改名后跨版本读不回来。
 */
data class CloudTtsVoiceConfig(
    @SerializedName("locale") val locale: String = "",
    @SerializedName("style") val style: String = "",
    @SerializedName("role") val role: String = "",
    @SerializedName("instructions") val instructions: String = "",
    @SerializedName("automaticEmotion") val automaticEmotion: Boolean? = null,
    @SerializedName("characterPersonality") val characterPersonality: Boolean? = null,
    @SerializedName("thoughtPerformance") val thoughtPerformance: Boolean? = null,
    @SerializedName("speed") val speed: Float = 1f,
    @SerializedName("pitch") val pitch: Float = 1f,
    @SerializedName("volume") val volume: Float = 1f,
    @SerializedName("format") val format: String = "mp3",
    @SerializedName("sampleRate") val sampleRate: Int? = null,
)
