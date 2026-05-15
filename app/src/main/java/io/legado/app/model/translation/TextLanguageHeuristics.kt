package io.legado.app.model.translation

import io.legado.app.ui.config.translation.TranslationConfig
import java.util.regex.Pattern

/**
 * Heuristics to determine if text should be translated.
 * Returns true if text appears to need translation (non-Chinese, non-empty, etc.)
 */
object TextLanguageHeuristics {

    // Chinese character range
    private val chinesePattern = Pattern.compile("[\\u4e00-\\u9fff]")

    // Pattern for numbers and punctuation only
    private val numbersPunctuationPattern = Pattern.compile("^[0-9\\s.,!?;:()，。！？；：()\"'\\-]+$")

    // Pattern for blank/whitespace only
    private val blankPattern = Pattern.compile("^\\s*$")

    /**
     * Check if text needs translation.
     *
     * @param text The text to check
     * @param targetLanguage The target language (e.g., "zh")
     * @return true if text should be translated
     */
    fun needsTranslation(text: String, targetLanguage: String = TranslationConfig.llmTargetLanguage): Boolean {
        if (text.isBlank()) return false

        // Skip if already the target language (Chinese)
        if (targetLanguage == "zh" && isChinese(text)) return false

        // Skip numbers and punctuation only
        if (numbersPunctuationPattern.matcher(text).matches()) return false

        // Skip blank text
        if (blankPattern.matcher(text).matches()) return false

        return true
    }

    /**
     * Normalize text for cache key generation.
     * Lowercase and trim whitespace.
     */
    fun normalize(text: String): String {
        return text.lowercase().trim()
    }

    /**
     * Check if text contains Chinese characters.
     */
    fun isChinese(text: String): Boolean {
        return chinesePattern.matcher(text).find()
    }

    /**
     * Quick check if text might be translatable (non-Chinese).
     */
    fun mightBeTranslatable(text: String): Boolean {
        return !isChinese(text) && !blankPattern.matcher(text).matches()
    }
}