package io.legado.app.help.readaloud.playback

import io.legado.app.domain.model.readaloud.CloudTtsProviderType
import io.legado.app.domain.model.readaloud.SpeechEmotion

object CloudTtsEmotionMapper {
    const val VERSION = "emotion-control-v2"

    fun map(provider: CloudTtsProviderType, emotionValue: String): CloudTtsEmotionControl {
        val emotion = SpeechEmotion.fromStorage(emotionValue)
        if (emotion == SpeechEmotion.Neutral) return CloudTtsEmotionControl()
        return when (provider) {
            CloudTtsProviderType.OpenAiSpeech,
            CloudTtsProviderType.GeminiTts -> CloudTtsEmotionControl(
                instruction = emotion.toNaturalLanguageInstruction(),
            )
            CloudTtsProviderType.Mimo -> CloudTtsEmotionControl(style = emotion.toMimo())
            CloudTtsProviderType.AzureSpeech -> CloudTtsEmotionControl(style = emotion.toAzure())
            CloudTtsProviderType.AlibabaCloud -> CloudTtsEmotionControl(
                instruction = emotion.toAlibabaInstruction(),
            )
            CloudTtsProviderType.AwsPolly -> CloudTtsEmotionControl()
            CloudTtsProviderType.Volcengine -> CloudTtsEmotionControl(
                style = emotion.toVolcengine(),
            )
        }
    }

    private fun SpeechEmotion.toNaturalLanguageInstruction(): String = when (this) {
        SpeechEmotion.Cheerful -> "Speak in a cheerful and lively tone."
        SpeechEmotion.Sad -> "Speak in a sad and subdued tone."
        SpeechEmotion.Angry -> "Speak with controlled anger and intensity."
        SpeechEmotion.Fearful -> "Speak in a fearful and tense tone."
        SpeechEmotion.Surprised -> "Speak with genuine surprise."
        SpeechEmotion.Disgusted -> "Speak with clear disgust and displeasure."
        SpeechEmotion.Whispering -> "Speak quietly, almost in a whisper."
        SpeechEmotion.Calm -> "Speak in a calm and composed tone."
        SpeechEmotion.Neutral -> ""
    }

    private fun SpeechEmotion.toMimo(): String = when (this) {
        SpeechEmotion.Cheerful -> "开心"
        SpeechEmotion.Sad -> "悲伤"
        SpeechEmotion.Angry -> "愤怒"
        SpeechEmotion.Fearful -> "恐惧"
        SpeechEmotion.Surprised -> "惊讶"
        SpeechEmotion.Disgusted -> "厌恶"
        SpeechEmotion.Whispering -> "小声"
        SpeechEmotion.Calm -> "平静"
        SpeechEmotion.Neutral -> ""
    }

    private fun SpeechEmotion.toAzure(): String = when (this) {
        SpeechEmotion.Cheerful -> "cheerful"
        SpeechEmotion.Sad -> "sad"
        SpeechEmotion.Angry -> "angry"
        SpeechEmotion.Fearful -> "fearful"
        SpeechEmotion.Surprised -> "excited"
        SpeechEmotion.Disgusted -> "disgruntled"
        SpeechEmotion.Whispering -> "whispering"
        SpeechEmotion.Calm -> "calm"
        SpeechEmotion.Neutral -> ""
    }

    private fun SpeechEmotion.toAlibabaInstruction(): String = when (this) {
        SpeechEmotion.Cheerful -> "用开心、愉快的情绪朗读"
        SpeechEmotion.Sad -> "用悲伤、低落的情绪朗读"
        SpeechEmotion.Angry -> "用愤怒、强烈的情绪朗读"
        SpeechEmotion.Fearful -> "用恐惧、紧张的情绪朗读"
        SpeechEmotion.Surprised -> "用惊讶的情绪朗读"
        SpeechEmotion.Disgusted -> "用厌恶、不满的情绪朗读"
        SpeechEmotion.Whispering -> "压低声音，轻声朗读"
        SpeechEmotion.Calm -> "用平静、从容的语气朗读"
        SpeechEmotion.Neutral -> ""
    }

    private fun SpeechEmotion.toVolcengine(): String = when (this) {
        SpeechEmotion.Cheerful -> "happy"
        SpeechEmotion.Sad -> "sad"
        SpeechEmotion.Angry -> "angry"
        SpeechEmotion.Fearful -> "fear"
        SpeechEmotion.Surprised -> "surprise"
        SpeechEmotion.Disgusted -> "disgust"
        SpeechEmotion.Whispering -> ""
        SpeechEmotion.Calm -> "neutral"
        SpeechEmotion.Neutral -> ""
    }
}

data class CloudTtsEmotionControl(
    val style: String = "",
    val instruction: String = "",
)
