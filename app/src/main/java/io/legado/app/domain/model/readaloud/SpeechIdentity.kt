package io.legado.app.domain.model.readaloud

import io.legado.app.core.platform.Digest
import io.legado.app.core.platform.JcaDigest

object SpeechIdentity {

    /**
     * 摘要委托：原直接用 `java.security.MessageDigest`（JVM-only，阻碍本 object 进入 commonMain）。
     * 走 [Digest] 契约后，P2 下沉 :core:model 时此依赖已是平台无关契约。
     */
    private val digest: Digest = JcaDigest

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

    private fun sha256(value: String): String = digest.sha256(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
