package io.legado.app.domain.model.readaloud

import androidx.annotation.Keep

/**
 * 外部 httpTTS 源在 JSON 里自带的「发言人 / 情绪目录」草稿。
 *
 * 这里只描述源声明了什么，不含落库需要的引擎身份：落到 [ReadAloudVoice] 时由
 * `SyncHttpTtsCatalogUseCase` 补上 engineType / engineId。
 *
 * 目录的权威副本是 `read_aloud_voices` 表，草稿只活在导入这一次流程里，
 * 不写回 `HttpTTS` 行——否则同一个目录会有两个可编辑入口。
 */
@Keep
data class HttpTtsSpeakerDraft(
    val speakerName: String,
    val toneID: String,
    val tags: List<String> = emptyList(),
)

@Keep
data class HttpTtsEmotionDraft(
    val emotionName: String,
    val emotionTag: String,
)

data class HttpTtsCatalogDraft(
    val speakers: List<HttpTtsSpeakerDraft> = emptyList(),
    val emotions: List<HttpTtsEmotionDraft> = emptyList(),
) {

    val isEmpty: Boolean
        get() = speakers.isEmpty() && emotions.isEmpty()
}
