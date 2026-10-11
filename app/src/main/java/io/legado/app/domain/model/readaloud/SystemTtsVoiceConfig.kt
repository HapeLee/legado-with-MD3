package io.legado.app.domain.model.readaloud

import com.google.gson.annotations.SerializedName

/**
 * `traitsJson` 的落盘结构；键名写死在 [SerializedName]，否则 R8 改名后跨版本读不回来。
 */
data class SystemTtsVoiceConfig(
    @SerializedName("speechRate") val speechRate: Float? = null,
    @SerializedName("pitch") val pitch: Float? = null,
)
