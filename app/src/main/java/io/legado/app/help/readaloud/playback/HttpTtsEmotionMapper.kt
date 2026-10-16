package io.legado.app.help.readaloud.playback

import io.legado.app.domain.model.readaloud.HttpTtsEmotionDraft
import io.legado.app.utils.GSON

/** 只发送目录声明过的值；未知情绪交给源默认值，不猜测源的私有标签。 */
object HttpTtsEmotionMapper {
    const val VERSION = "http-emotion-v1"

    fun parseCatalog(json: String?): List<HttpTtsEmotionDraft> = runCatching {
        GSON.fromJson(json ?: "[]", Array<HttpTtsEmotionDraft>::class.java).toList()
    }.getOrDefault(emptyList())

    private val aliases = listOf(
        setOf("neutral", "default", "默认", "中性", "正常"),
        setOf("cheerful", "happy", "开心", "高兴", "快乐", "愉快"),
        setOf("sad", "sadness", "悲伤", "伤心", "难过"),
        setOf("angry", "anger", "愤怒", "生气"),
        setOf("fearful", "fear", "scared", "害怕", "恐惧"),
        setOf("surprised", "surprise", "惊讶", "吃惊"),
        setOf("disgusted", "disgust", "厌恶", "反感"),
        setOf("whispering", "whisper", "耳语", "小声", "低语"),
        setOf("calm", "平静", "冷静"),
    )

    fun map(emotion: String, catalog: List<HttpTtsEmotionDraft>): HttpTtsEmotionDraft {
        // 未声明目录的旧源继续接收原有变量，兼容它自己的 JS 映射。
        if (catalog.isEmpty()) return HttpTtsEmotionDraft("", emotion)
        val key = emotion.trim().lowercase()
        catalog.firstOrNull { it.emotionTag.equals(key, true) || it.emotionName.equals(key, true) }
            ?.let { return it }
        val synonyms = aliases.firstOrNull { key in it }
        catalog.firstOrNull {
            it.emotionTag.lowercase() in synonyms.orEmpty() ||
                it.emotionName.lowercase() in synonyms.orEmpty()
        }?.let { return it }
        return catalog.firstOrNull {
            it.emotionTag.lowercase() in aliases.first() || it.emotionName.lowercase() in aliases.first()
        } ?: HttpTtsEmotionDraft("", "")
    }
}
