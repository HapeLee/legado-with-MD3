package io.legado.app.domain.model.readaloud

import io.legado.app.core.platform.JcaDigest

/** Android/JVM compatibility entry point; shared formatting lives in [SpeechIdentityCalculator]. */
object SpeechIdentity {

    private val delegate = SpeechIdentityCalculator(JcaDigest)

    fun voiceId(engineType: String, engineId: String, speakerId: String): String =
        delegate.voiceId(engineType, engineId, speakerId)

    fun chapterContentHash(paragraphs: List<CanonicalSpeechParagraph>): String =
        delegate.chapterContentHash(paragraphs)

    fun analysisId(
        bookUrl: String,
        chapterIndex: Int,
        contentHash: String,
        resolverVersion: String,
    ): String = delegate.analysisId(bookUrl, chapterIndex, contentHash, resolverVersion)

    fun segmentId(
        analysisId: String,
        paragraphIndex: Int,
        start: Int,
        end: Int,
    ): String = delegate.segmentId(analysisId, paragraphIndex, start, end)

    fun characterRevision(characters: List<SpeakerCharacter>): String = delegate.characterRevision(characters)
}
