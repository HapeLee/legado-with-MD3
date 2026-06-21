package io.legado.app.help.readaloud.role

import io.legado.app.help.config.AppConfig
import org.json.JSONArray
import org.json.JSONObject

data class ReadAloudQuotePair(
    val open: String,
    val close: String
)

data class ReadAloudPreprocessRuleConfig(
    val quotePairs: List<ReadAloudQuotePair> = defaultQuotePairs,
    val sentencePunctuation: String = "。！？…!?",
    val thoughtCuePatterns: List<String> = listOf("心道", "暗道", "想道", "心想"),
    val dialogueMinLength: Int = 6,
    val emphasisMaxLength: Int = 8,
    val colonCueMaxLength: Int = 24,
    val mergeCrossParagraphQuote: Boolean = true,
    val soundEffectCuePatterns: List<String> = defaultSoundEffectCuePatterns,
    val soundEffectExcludePatterns: List<String> = defaultSoundEffectExcludePatterns,
    val soundEffectContextChars: Int = 28
) {

    fun toJsonString(): String {
        return JSONObject().apply {
            put("quotePairs", JSONArray().apply {
                quotePairs.forEach { pair ->
                    put(JSONObject().apply {
                        put("open", pair.open)
                        put("close", pair.close)
                    })
                }
            })
            put("sentencePunctuation", sentencePunctuation)
            put("thoughtCuePatterns", JSONArray().apply {
                thoughtCuePatterns.forEach(::put)
            })
            put("dialogueMinLength", dialogueMinLength.coerceIn(1, 80))
            put("emphasisMaxLength", emphasisMaxLength.coerceIn(1, 80))
            put("colonCueMaxLength", colonCueMaxLength.coerceIn(1, 80))
            put("mergeCrossParagraphQuote", mergeCrossParagraphQuote)
            put("soundEffectCuePatterns", JSONArray().apply {
                soundEffectCuePatterns.forEach(::put)
            })
            put("soundEffectExcludePatterns", JSONArray().apply {
                soundEffectExcludePatterns.forEach(::put)
            })
            put("soundEffectContextChars", soundEffectContextChars.coerceIn(8, 120))
        }.toString(2)
    }

    companion object {
        val defaultQuotePairs = listOf(
            ReadAloudQuotePair("“", "”"),
            ReadAloudQuotePair("‘", "’"),
            ReadAloudQuotePair("\"", "\""),
            ReadAloudQuotePair("'", "'"),
            ReadAloudQuotePair("「", "」"),
            ReadAloudQuotePair("『", "』")
        )

        val defaultSoundEffectCuePatterns = listOf(
            "吱呀", "砰", "咚", "哐", "啪", "轰", "咔嚓", "咯吱", "哗啦", "嗡",
            "铃声", "脚步声", "敲门声", "开门声", "关门声", "枪声", "雷声", "风声", "雨声"
        )

        val defaultSoundEffectExcludePatterns = listOf(
            "心声", "名声", "声音很", "声音低", "声音冷", "声音沙哑"
        )

        fun current(): ReadAloudPreprocessRuleConfig {
            return parse(AppConfig.aiReadAloudRolePreprocessRules)
        }

        fun parse(json: String): ReadAloudPreprocessRuleConfig {
            return runCatching {
                val root = JSONObject(json)
                ReadAloudPreprocessRuleConfig(
                    quotePairs = root.optJSONArray("quotePairs").toQuotePairs().ifEmpty { defaultQuotePairs },
                    sentencePunctuation = root.optString("sentencePunctuation").ifBlank { "。！？…!?" },
                    thoughtCuePatterns = root.optJSONArray("thoughtCuePatterns").toStringList()
                        .ifEmpty { listOf("心道", "暗道", "想道", "心想") },
                    dialogueMinLength = root.optInt("dialogueMinLength", 6).coerceIn(1, 80),
                    emphasisMaxLength = root.optInt("emphasisMaxLength", 8).coerceIn(1, 80),
                    colonCueMaxLength = root.optInt("colonCueMaxLength", 24).coerceIn(1, 80),
                    mergeCrossParagraphQuote = root.optBoolean("mergeCrossParagraphQuote", true),
                    soundEffectCuePatterns = root.optJSONArray("soundEffectCuePatterns").toStringList()
                        .ifEmpty { defaultSoundEffectCuePatterns },
                    soundEffectExcludePatterns = root.optJSONArray("soundEffectExcludePatterns").toStringList()
                        .ifEmpty { defaultSoundEffectExcludePatterns },
                    soundEffectContextChars = root.optInt("soundEffectContextChars", 28).coerceIn(8, 120)
                )
            }.getOrDefault(ReadAloudPreprocessRuleConfig())
        }

        private fun JSONArray?.toQuotePairs(): List<ReadAloudQuotePair> {
            if (this == null) return emptyList()
            val result = mutableListOf<ReadAloudQuotePair>()
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val open = item.optString("open").takeIf { it.isNotBlank() } ?: continue
                val close = item.optString("close").takeIf { it.isNotBlank() } ?: continue
                result += ReadAloudQuotePair(open.take(1), close.take(1))
            }
            return result
        }

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            val result = mutableListOf<String>()
            for (index in 0 until length()) {
                optString(index).trim().takeIf { it.isNotBlank() }?.let(result::add)
            }
            return result.distinct()
        }
    }
}
