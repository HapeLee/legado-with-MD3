package io.legado.app.domain.model

import java.util.Locale

object TranslationDictionaryPolicy {

    private val honorifics = setOf(
        "mr", "mrs", "ms", "miss", "dr", "sir", "lady", "lord",
        "professor", "prof", "captain", "capt", "king", "queen",
        "prince", "princess",
    )
    private val splitMatchStopWords = honorifics + setOf(
        "the", "a", "an", "of", "and", "to", "or", "for", "in", "on", "at", "by",
    )
    private val whitespace = Regex("\\s+")
    private val tokenRegex = Regex("[\\p{L}\\p{N}]+")

    fun normalizeOriginal(value: String): String {
        val collapsed = value.trim().replace(whitespace, " ")
        val tokens = collapsed.split(' ').toMutableList()
        if (tokens.isNotEmpty()) {
            val first = tokens.first().removeSuffix(".")
            if (first.lowercase(Locale.ROOT) in honorifics) {
                tokens[0] = first
            }
        }
        return tokens.joinToString(" ").lowercase(Locale.ROOT)
    }

    fun isValidNewOriginal(value: String): Boolean {
        val normalized = value.trim().replace(whitespace, " ")
        if (normalized.isEmpty()) return false
        if (normalized.codePoints().toArray().none { Character.isLetter(it) }) return false
        if (!isPrimarilyLatin(normalized)) return true

        val tokens = tokenRegex.findAll(normalized).map { it.value }.toList()
        if (tokens.size == 2 &&
            tokens.first().lowercase(Locale.ROOT) in setOf("the", "a", "an") &&
            tokens.last().codePointCount() == 1 &&
            tokens.last().firstCodePointIsUppercase()
        ) {
            return true
        }
        val contentTokens = tokens.filterNot { it.lowercase(Locale.ROOT) in splitMatchStopWords }
        if (contentTokens.any(::isProperNameToken)) return true

        return tokens.size > 1 &&
            contentTokens.size == 1 &&
            contentTokens.single().codePointCount() == 1 &&
            contentTokens.single().firstCodePointIsUppercase()
    }

    fun selectRelevantPairs(pairs: List<DictPair>, chunk: String): List<DictPair> = pairs.filter { pair ->
        val original = pair.original.trim().replace(whitespace, " ")
        if (original.isEmpty()) return@filter false
        if (containsTokenSequence(chunk, original)) return@filter true

        val baseName = stripLeadingHonorific(original)
        if (baseName != null && containsTokenSequence(chunk, baseName)) return@filter true

        val tokens = tokenRegex.findAll(original)
            .map { it.value }
            .filterNot { it.lowercase(Locale.ROOT) in splitMatchStopWords }
            .filter(::isProperNameToken)
            .toList()
        tokens.size > 1 && tokens.any { containsTokenSequence(chunk, it) }
    }

    fun mergeDiscoveredPairs(existing: List<DictPair>, discovered: List<DictPair>): List<DictPair> {
        val merged = existing.toMutableList()
        discovered.forEach { rawPair ->
            val pair = DictPair(
                original = rawPair.original.trim().replace(whitespace, " "),
                translation = rawPair.translation.trim(),
            )
            if (pair.original.isEmpty() || pair.translation.isEmpty()) return@forEach

            val normalized = normalizeOriginal(pair.original)
            if (merged.any { normalizeOriginal(it.original) == normalized }) return@forEach

            val candidateBase = stripLeadingHonorific(pair.original)
            if (candidateBase != null) {
                val normalizedBase = normalizeOriginal(candidateBase)
                if (merged.any { existingPair ->
                        normalizeOriginal(existingPair.original) == normalizedBase ||
                            stripLeadingHonorific(existingPair.original)
                                ?.let(::normalizeOriginal) == normalizedBase
                    }
                ) {
                    return@forEach
                }
            } else {
                merged.removeAll { existingPair ->
                    stripLeadingHonorific(existingPair.original)
                        ?.let(::normalizeOriginal) == normalized
                }
            }
            merged += pair
        }
        return merged
    }

    private fun stripLeadingHonorific(value: String): String? {
        val normalized = value.trim().replace(whitespace, " ")
        val firstSpace = normalized.indexOf(' ')
        if (firstSpace <= 0) return null
        val first = normalized.substring(0, firstSpace).removeSuffix(".").lowercase(Locale.ROOT)
        if (first !in honorifics) return null
        return normalized.substring(firstSpace + 1).trim().takeIf { it.isNotEmpty() }
    }

    private fun containsTokenSequence(text: String, term: String): Boolean {
        val termPattern = term.split(whitespace).joinToString("\\s+") { Regex.escape(it) }
        return Regex(
            pattern = "(?<![\\p{L}\\p{N}])$termPattern(?![\\p{L}\\p{N}])",
            options = setOf(RegexOption.IGNORE_CASE),
        ).containsMatchIn(text)
    }

    private fun isPrimarilyLatin(value: String): Boolean {
        val letters = value.codePoints().toArray().filter { Character.isLetter(it) }
        if (letters.isEmpty()) return false
        val latinLetters = letters.count { Character.UnicodeScript.of(it) == Character.UnicodeScript.LATIN }
        return latinLetters * 2 >= letters.size
    }

    private fun isProperNameToken(token: String): Boolean {
        val codePoints = token.codePoints().toArray()
        if (codePoints.size <= 1) return false
        return Character.isUpperCase(codePoints.first()) ||
            codePoints.drop(1).any { Character.isUpperCase(it) } ||
            (codePoints.count { Character.isLetter(it) } > 1 &&
                codePoints.filter { Character.isLetter(it) }.all { Character.isUpperCase(it) })
    }

    private fun String.codePointCount(): Int = codePointCount(0, length)

    private fun String.firstCodePointIsUppercase(): Boolean =
        isNotEmpty() && Character.isUpperCase(codePointAt(0))
}
