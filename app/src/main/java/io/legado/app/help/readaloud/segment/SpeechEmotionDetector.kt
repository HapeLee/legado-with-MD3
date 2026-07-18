package io.legado.app.help.readaloud.segment

import io.legado.app.domain.model.readaloud.SpeechEmotion

/** Conservative deterministic emotion detection for explicit prose cues. */
object SpeechEmotionDetector {
    private val angry = Regex("怒道|怒吼|吼道|咆哮|暴喝|厉声|愤怒|恼怒|气急|混蛋|该死")
    private val fearful = Regex("惊恐|恐惧|害怕|畏惧|颤声|颤抖|发抖|毛骨悚然|不寒而栗")
    private val sad = Regex("哭道|哭喊|抽泣|哽咽|悲伤|悲痛|伤心|泪流|落泪|啜泣")
    private val disgusted = Regex("厌恶|嫌恶|恶心|鄙夷|不屑|嗤之以鼻")
    private val cheerful = Regex("笑道|笑着|大笑|轻笑|开心|高兴|欣喜|兴奋|欢呼|喜悦")
    private val surprised = Regex("惊讶|震惊|惊呼|失声道|难以置信|怎么可能|竟然|居然")
    private val whispering = Regex("低声|轻声|小声|耳语|喃喃|呢喃|悄声|压低声音")
    private val calm = Regex("平静|淡淡地|淡然|从容|冷静|不紧不慢")

    fun detect(text: String, context: String = ""): SpeechEmotion {
        val sample = "$context$text"
        return when {
            angry.containsMatchIn(sample) -> SpeechEmotion.Angry
            fearful.containsMatchIn(sample) -> SpeechEmotion.Fearful
            sad.containsMatchIn(sample) -> SpeechEmotion.Sad
            disgusted.containsMatchIn(sample) -> SpeechEmotion.Disgusted
            cheerful.containsMatchIn(sample) -> SpeechEmotion.Cheerful
            surprised.containsMatchIn(sample) -> SpeechEmotion.Surprised
            whispering.containsMatchIn(sample) -> SpeechEmotion.Whispering
            calm.containsMatchIn(sample) -> SpeechEmotion.Calm
            else -> SpeechEmotion.Neutral
        }
    }
}
