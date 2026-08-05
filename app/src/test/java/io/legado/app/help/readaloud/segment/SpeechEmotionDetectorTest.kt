package io.legado.app.help.readaloud.segment

import io.legado.app.domain.model.readaloud.SpeechEmotion
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechEmotionDetectorTest {
    @Test
    fun `uses context cue when spoken text has no emotion word`() {
        assertEquals(
            SpeechEmotion.Whispering,
            SpeechEmotionDetector.detect("不要出声。", "她压低声音说道："),
        )
    }

    @Test
    fun `returns neutral for ambiguous punctuation`() {
        assertEquals(
            SpeechEmotion.Neutral,
            SpeechEmotionDetector.detect("你要去哪里？"),
        )
    }
}
