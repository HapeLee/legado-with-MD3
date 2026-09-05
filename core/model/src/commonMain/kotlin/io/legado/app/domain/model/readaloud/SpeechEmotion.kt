package io.legado.app.domain.model.readaloud

enum class SpeechEmotion(val storageValue: String) {
    Neutral("neutral"),
    Cheerful("cheerful"),
    Sad("sad"),
    Angry("angry"),
    Fearful("fearful"),
    Surprised("surprised"),
    Disgusted("disgusted"),
    Whispering("whispering"),
    Calm("calm");

    companion object {
        fun fromStorage(value: String): SpeechEmotion =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: Neutral
    }
}
