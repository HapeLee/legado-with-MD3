package io.legado.app.service

import android.support.v4.media.session.PlaybackStateCompat
import io.legado.app.R
import io.legado.app.constant.IntentAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知按钮与系统媒体控件必须表达同一个语义（issue #2604）：
 * 切章用 SKIP_TO_NEXT/⏭，段落快进用 FAST_FORWARD/⏩，不能声明一个执行另一个。
 */
class ReadAloudSkipActionTest {

    @Test
    fun paragraphModeUsesFastForwardSemantics() {
        val next = readAloudSkipAction(forward = true, navigateByChapter = false)
        val previous = readAloudSkipAction(forward = false, navigateByChapter = false)

        assertEquals(IntentAction.nextParagraph, next.intentAction)
        assertEquals(R.drawable.ic_fast_forward, next.iconRes)
        assertEquals(R.string.next_sentence, next.labelRes)
        assertEquals(PlaybackStateCompat.ACTION_FAST_FORWARD, next.playbackAction)

        assertEquals(IntentAction.prevParagraph, previous.intentAction)
        assertEquals(R.drawable.ic_fast_rewind, previous.iconRes)
        assertEquals(R.string.prev_sentence, previous.labelRes)
        assertEquals(PlaybackStateCompat.ACTION_REWIND, previous.playbackAction)

        // 段落模式绝不能声明「跳到下一曲目」，否则系统会画 ⏭ 却执行下一段。
        assertEquals(0L, next.playbackAction and PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
        assertEquals(0L, previous.playbackAction and PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
    }

    @Test
    fun chapterModeUsesSkipToNextSemantics() {
        val next = readAloudSkipAction(forward = true, navigateByChapter = true)
        val previous = readAloudSkipAction(forward = false, navigateByChapter = true)

        assertEquals(IntentAction.next, next.intentAction)
        assertEquals(R.drawable.ic_skip_next, next.iconRes)
        assertEquals(R.string.next_chapter, next.labelRes)
        assertEquals(PlaybackStateCompat.ACTION_SKIP_TO_NEXT, next.playbackAction)

        assertEquals(IntentAction.prev, previous.intentAction)
        assertEquals(R.drawable.ic_skip_previous, previous.iconRes)
        assertEquals(R.string.previous_chapter, previous.labelRes)
        assertEquals(PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS, previous.playbackAction)
    }

    @Test
    fun forwardAndBackwardNeverShareAnAction() {
        listOf(false, true).forEach { navigateByChapter ->
            val next = readAloudSkipAction(forward = true, navigateByChapter = navigateByChapter)
            val previous = readAloudSkipAction(forward = false, navigateByChapter = navigateByChapter)

            assertTrue(next.playbackAction != previous.playbackAction)
            assertTrue(next.intentAction != previous.intentAction)
            assertTrue(next.iconRes != previous.iconRes)
        }
    }
}
