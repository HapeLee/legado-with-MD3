package io.legado.app.feature.reader.core.transition

import io.legado.app.feature.reader.core.model.ReaderPageId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderCommittedTurnPolicyTest {
    private val origin = ReaderPageId(0, 0)
    private val committed = ReaderPageId(0, 1)
    private val elsewhere = ReaderPageId(1, 0)

    @Test fun withoutACommittedTurnThereIsNothingToHold() {
        assertFalse(ReaderCommittedTurnPolicy.shouldRelease(origin, null, null))
        assertFalse(ReaderCommittedTurnPolicy.shouldRelease(committed, null, origin))
    }

    @Test fun holdsTheFrameWhileTheOutgoingWindowIsStillComposed() {
        assertFalse(ReaderCommittedTurnPolicy.shouldRelease(origin, committed, origin))
    }

    @Test fun releasesWhenTheCommittedWindowArrives() {
        assertTrue(ReaderCommittedTurnPolicy.shouldRelease(committed, committed, origin))
    }

    @Test fun releasesWhenTheWindowMovesSomewhereElse() {
        // 重新排版 / 外部跳转 / 边界情况：换窗结果与本次翻页无关，必须释放保留帧，
        // 否则 committedPageId 永远等不到命中，画布会永久停在提交后的偏移状态。
        assertTrue(ReaderCommittedTurnPolicy.shouldRelease(elsewhere, committed, origin))
    }

    @Test fun withoutAnOriginItOnlyReleasesOnTheCommittedPage() {
        assertFalse(ReaderCommittedTurnPolicy.shouldRelease(origin, committed, null))
        assertFalse(ReaderCommittedTurnPolicy.shouldRelease(elsewhere, committed, null))
        assertTrue(ReaderCommittedTurnPolicy.shouldRelease(committed, committed, null))
    }
}
