package io.legado.app.help.readaloud.speech

import com.google.gson.JsonArray
import io.legado.app.data.entities.HttpTTS
import io.legado.app.domain.model.readaloud.HttpTtsSpeakerDraft
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.utils.GSON

/** 外部格式只传目录；enabled/excluded 等本机状态由标准备份单独保存。 */
object HttpTtsCatalogExporter {
    fun export(sources: List<HttpTTS>, voices: List<ReadAloudVoice>): String {
        val byEngine = voices.filter {
            it.engineType == ReadAloudVoice.ENGINE_HTTP &&
                it.managedBy == ReadAloudVoice.MANAGED_BY_HTTP_SOURCE_CATALOG
        }.groupBy { it.engineId }
        return JsonArray().apply {
            sources.forEach { source ->
                val json = GSON.toJsonTree(source).asJsonObject
                val catalog = byEngine[source.id.toString()].orEmpty()
                if (catalog.isNotEmpty()) {
                    val speakers = catalog.map { voice ->
                        HttpTtsSpeakerDraft(
                            speakerName = voice.displayName,
                            toneID = voice.speakerId,
                            tags = runCatching {
                                GSON.fromJson(voice.traitsJson, Array<String>::class.java).toList()
                            }.getOrDefault(emptyList()),
                        )
                    }
                    json.addProperty("speakersJson", GSON.toJson(speakers))
                    json.addProperty("emotionsJson", catalog.first().emotionCatalogJson)
                }
                add(json)
            }
        }.toString()
    }
}
