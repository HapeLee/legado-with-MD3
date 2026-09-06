package io.legado.app.domain.model.readaloud

import io.legado.app.core.platform.Digest

/**
 * Builds stable IDs for persisted read-aloud data without choosing a platform digest implementation.
 */
class SpeechIdentityCalculator(
    private val digest: Digest,
) {

    fun voiceId(engineType: String, engineId: String, speakerId: String): String =
        "voice:${sha256("$engineType\u0000$engineId\u0000$speakerId").take(32)}"

    fun chapterContentHash(paragraphs: List<CanonicalSpeechParagraph>): String = sha256(
        paragraphs.joinToString("\u0001") {
            "${it.index}\u0000${it.chapterPosition}\u0000${it.text}"
        }
    )

    fun analysisId(
        bookUrl: String,
        chapterIndex: Int,
        contentHash: String,
        resolverVersion: String,
    ): String = "speech-analysis:${sha256("$bookUrl\u0000$chapterIndex\u0000$contentHash\u0000$resolverVersion").take(32)}"

    fun segmentId(
        analysisId: String,
        paragraphIndex: Int,
        start: Int,
        end: Int,
    ): String = "speech-segment:${sha256("$analysisId\u0000$paragraphIndex\u0000$start\u0000$end").take(32)}"

    fun characterRevision(characters: List<SpeakerCharacter>): String = sha256(
        characters.sortedBy(SpeakerCharacter::id).joinToString("\u0001") { character ->
            listOf(
                character.id,
                character.name,
                character.aliases.sorted().joinToString("\u0002"),
                character.role,
                character.voiceGender,
                character.voiceAgeBand,
                character.updatedAt.toString(),
            ).joinToString("\u0000")
        }
    )

    private fun sha256(value: String): String = digest.sha256(value.encodeToByteArray())
        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
