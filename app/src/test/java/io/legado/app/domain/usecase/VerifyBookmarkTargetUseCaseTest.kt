package io.legado.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class VerifyBookmarkTargetUseCaseTest {

    private val useCase = VerifyBookmarkTargetUseCase()

    @Test
    fun `源相同且章节标题一致则直接跳`() {
        assertEquals(
            BookmarkTargetVerdict.Match,
            useCase.verify(
                currentBookUrl = "srcA",
                targetChapterTitle = "第三章",
                storedBookUrl = "srcA",
                storedChapterName = "第三章"
            ),
        )
    }

    @Test
    fun `源指纹不同则提示源变更`() {
        // 换源后书签/笔记位置可疑
        assertEquals(
            BookmarkTargetVerdict.SourceChanged("srcA"),
            useCase.verify(
                currentBookUrl = "srcB",
                targetChapterTitle = "第三章",
                storedBookUrl = "srcA",
                storedChapterName = "第三章"
            ),
        )
    }

    @Test
    fun `源未知且章节标题不符则提示标题变更`() {
        // 旧数据 bookUrl 为空 → 跳过源校验，落到标题校验
        assertEquals(
            BookmarkTargetVerdict.TitleMismatch,
            useCase.verify(
                currentBookUrl = "srcB",
                targetChapterTitle = "第二章",
                storedBookUrl = "",
                storedChapterName = "第三章"
            ),
        )
    }

    @Test
    fun `目标标题拿不到时只看源校验`() {
        // targetChapterTitle null（index 越界/未加载）→ 跳过标题校验
        assertEquals(
            BookmarkTargetVerdict.Match,
            useCase.verify(
                currentBookUrl = "srcA",
                targetChapterTitle = null,
                storedBookUrl = "srcA",
                storedChapterName = "第三章"
            ),
        )
        assertEquals(
            BookmarkTargetVerdict.SourceChanged("srcA"),
            useCase.verify(
                currentBookUrl = "srcB",
                targetChapterTitle = null,
                storedBookUrl = "srcA",
                storedChapterName = "第三章"
            ),
        )
    }
}
