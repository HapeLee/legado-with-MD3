package io.legado.app.domain.model.readaloud

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object SpeechIdentity {

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

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
