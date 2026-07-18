package io.legado.app.help.readaloud.resolve

import io.legado.app.domain.model.readaloud.CanonicalSpeechParagraph
import io.legado.app.domain.model.readaloud.ChapterSpeechSegment
import io.legado.app.domain.model.readaloud.SpeakerCharacter
import io.legado.app.domain.model.readaloud.SpeechResolutionSource
import io.legado.app.domain.model.readaloud.SpeechRoleType

object LocalCharacterSpeakerResolver {

    const val VERSION = "local-character-resolver-v1"

    private const val CONTEXT_LENGTH = 64
    private val speechVerb =
        "(?:说道|说|问道|问|答道|答|喊道|喊|叫道|叫|喝道|笑道|低声道|沉声道|怒道|开口道)"
    private val thoughtVerb = "(?:心想|心道|暗道|想道)"

    fun resolve(
        paragraphs: List<CanonicalSpeechParagraph>,
        segments: List<ChapterSpeechSegment>,
        characters: List<SpeakerCharacter>,
    ): List<ChapterSpeechSegment> {
        if (segments.isEmpty() || characters.isEmpty()) return segments
        val paragraphsByIndex = paragraphs.associateBy(CanonicalSpeechParagraph::index)
        val aliases = buildAliasCandidates(characters)
        if (aliases.isEmpty()) return segments

        return segments.map { segment ->
            if (!segment.shouldResolve) return@map segment
            val paragraph = paragraphsByIndex[segment.paragraphIndex] ?: return@map segment
            val start = segment.start.coerceIn(0, paragraph.text.length)
            val end = segment.end.coerceIn(start, paragraph.text.length)
            val before = paragraph.text.substring(0, start).takeLast(CONTEXT_LENGTH)
            val after = paragraph.text.substring(end).take(CONTEXT_LENGTH)
            val matches = aliases.asSequence()
                .filter { candidate ->
                    candidate.matchesBefore(before, segment.roleType) ||
                        candidate.matchesAfter(after)
                }
                .map(AliasCandidate::character)
                .distinctBy(SpeakerCharacter::id)
                .take(2)
                .toList()
            val character = matches.singleOrNull() ?: return@map segment
            segment.copy(
                characterId = character.id,
                characterName = character.name,
                confidence = maxOf(segment.confidence, 0.94f),
                source = SpeechResolutionSource.Local,
            )
        }
    }

    private val ChapterSpeechSegment.shouldResolve: Boolean
        get() = !userLocked &&
            characterId == null &&
            (roleType == SpeechRoleType.Character || roleType == SpeechRoleType.Thought)

    private fun buildAliasCandidates(characters: List<SpeakerCharacter>): List<AliasCandidate> {
        val ownersByAlias = linkedMapOf<String, MutableList<SpeakerCharacter>>()
        characters.forEach { character ->
            (listOf(character.name) + character.aliases)
                .map { it.normalizeAlias() }
                .filter { it.isNotBlank() }
                .distinct()
                .forEach { alias -> ownersByAlias.getOrPut(alias, ::mutableListOf) += character }
        }
        return ownersByAlias.mapNotNull { (alias, owners) ->
            owners.distinctBy(SpeakerCharacter::id).singleOrNull()?.let { AliasCandidate(alias, it) }
        }.sortedByDescending { it.alias.length }
    }

    private fun String.normalizeAlias(): String = trim()
        .trim('“', '”', '‘', '’', '"', '\'', '《', '》', '，', ',', '。', '：', ':')
        .replace(Regex("\\s+"), "")

    private data class AliasCandidate(
        val alias: String,
        val character: SpeakerCharacter,
    ) {
        private val escapedAlias = Regex.escape(alias)
        private val beforeSpeechRegex = Regex(
            "(?<![\\p{L}\\p{N}_])$escapedAlias\\s*(?:$speechVerb|$thoughtVerb)?\\s*[：:]\\s*$"
        )
        private val beforeVerbRegex = Regex(
            "(?<![\\p{L}\\p{N}_])$escapedAlias\\s*(?:$speechVerb|$thoughtVerb)\\s*[，,\\s]*$"
        )
        private val afterRegex = Regex(
            "^[，,。.!！?？\\s]*(?<![\\p{L}\\p{N}_])$escapedAlias\\s*(?:$speechVerb|$thoughtVerb)"
        )

        fun matchesBefore(context: String, roleType: SpeechRoleType): Boolean {
            val regex = if (roleType == SpeechRoleType.Thought) {
                Regex(
                    "(?<![\\p{L}\\p{N}_])$escapedAlias\\s*$thoughtVerb\\s*[，,：:\\s]*$"
                )
            } else {
                null
            }
            return regex?.containsMatchIn(context) == true ||
                beforeSpeechRegex.containsMatchIn(context) ||
                beforeVerbRegex.containsMatchIn(context)
        }

        fun matchesAfter(context: String): Boolean =
            afterRegex.containsMatchIn(context)
    }
}
