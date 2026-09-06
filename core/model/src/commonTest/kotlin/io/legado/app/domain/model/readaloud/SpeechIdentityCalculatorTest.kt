package io.legado.app.domain.model.readaloud

import io.legado.app.core.platform.Digest
import kotlin.test.Test
import kotlin.test.assertEquals

class SpeechIdentityCalculatorTest {

    private val digest = RecordingDigest()
    private val calculator = SpeechIdentityCalculator(digest)

    @Test
    fun `voice and analysis identifiers preserve their persisted input layout`() {
        assertEquals("voice:$hexPrefix", calculator.voiceId("http", "engine", "speaker"))
        assertEquals("http\u0000engine\u0000speaker", digest.lastInput)

        assertEquals(
            "speech-analysis:$hexPrefix",
            calculator.analysisId("book", 7, "content", "resolver-v2"),
        )
        assertEquals("book\u00007\u0000content\u0000resolver-v2", digest.lastInput)

        assertEquals(
            "speech-segment:$hexPrefix",
            calculator.segmentId("analysis", 3, 10, 20),
        )
        assertEquals("analysis\u00003\u000010\u000020", digest.lastInput)
    }

    @Test
    fun `chapter content hash retains paragraph order and separators`() {
        calculator.chapterContentHash(
            listOf(
                CanonicalSpeechParagraph(index = 0, chapterPosition = 0, text = "First"),
                CanonicalSpeechParagraph(index = 1, chapterPosition = 5, text = "Second"),
            )
        )

        assertEquals("0\u00000\u0000First\u00011\u00005\u0000Second", digest.lastInput)
    }

    @Test
    fun `character revision sorts characters and aliases before hashing`() {
        calculator.characterRevision(
            listOf(
                SpeakerCharacter(id = "b", name = "Beta", aliases = listOf("z", "a"), updatedAt = 2),
                SpeakerCharacter(id = "a", name = "Alpha", aliases = listOf("c", "b"), updatedAt = 1),
            )
        )

        assertEquals(
            "a\u0000Alpha\u0000b\u0002c\u0000\u0000unknown\u0000unknown\u00001" +
                "\u0001b\u0000Beta\u0000a\u0002z\u0000\u0000unknown\u0000unknown\u00002",
            digest.lastInput,
        )
    }

    private class RecordingDigest : Digest {
        var lastInput = ""

        override fun sha256(data: ByteArray): ByteArray {
            lastInput = data.decodeToString()
            return ByteArray(32) { it.toByte() }
        }
    }

    private companion object {
        const val hexPrefix = "000102030405060708090a0b0c0d0e0f"
    }
}
