package io.legado.app.domain.usecase

import io.legado.app.domain.model.TextProcessAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelocateMarkingTargetUseCaseTest {
    private val useCase = RelocateMarkingTargetUseCase()

    @Test
    fun `uses surrounding context to find the current source position`() {
        val target = useCase.locate(
            TextProcessAnchor(2, 100, "同一句话", "门被推开，", "灯光亮起。", "hash"),
            listOf(
                RelocateMarkingTargetUseCase.Candidate(
                    5,
                    "序言。门被推开，同一句话灯光亮起。结尾。"
                )
            ),
        )
        assertEquals(RelocateMarkingTargetUseCase.Target(5, 8), target)
    }

    @Test
    fun `does not automatically choose equally scored duplicate text`() {
        val target = useCase.locate(
            TextProcessAnchor(2, 100, "同一句话", normalizedTextHash = "hash"),
            listOf(RelocateMarkingTargetUseCase.Candidate(2, "同一句话。中间。同一句话。")),
        )
        assertNull(target)
    }
}
