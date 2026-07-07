package io.legado.app.domain.model

import io.legado.app.data.entities.BookContentProcess
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookContentProcessEngineTest {

    @Test
    fun `matches reader selection with layout whitespace`() {
        val selectedText = "　　他走进\n房间，看见桌上的信。"
        val process = process(
            selectedText = selectedText,
            replacement = "他推门进屋，看见桌上的信。",
        )

        val result = BookContentProcessEngine.apply(
            content = "他走进房间，看见桌上的信。\n窗外雨声很轻。",
            processes = listOf(process),
        )

        assertEquals(
            "他推门进屋，看见桌上的信。\n窗外雨声很轻。",
            result.text,
        )
        assertEquals(listOf(process), result.effectiveProcesses)
    }

    @Test
    fun `uses closest normalized match`() {
        val process = process(
            selectedText = "　　他走进\n房间，看见桌上的信。",
            replacement = "他推门进屋，看见桌上的信。",
            chapterPosition = 20,
        )

        val result = BookContentProcessEngine.apply(
            content = "他走进房间，看见桌上的信。\n他走进房间，看见桌上的信。",
            processes = listOf(process),
        )

        assertEquals(
            "他走进房间，看见桌上的信。\n他推门进屋，看见桌上的信。",
            result.text,
        )
        assertTrue(result.effectiveProcesses.isNotEmpty())
    }

    private fun process(
        selectedText: String,
        replacement: String,
        chapterPosition: Int = 0,
    ): BookContentProcess {
        val normalizedSelectedText = BookContentProcessEngine.normalizeProcessText(selectedText)
        return BookContentProcess(
            id = "test",
            bookUrl = "book",
            chapterIndex = 0,
            kind = BookContentProcess.KIND_AI_CLEAN,
            anchorJson = GSON.toJson(
                TextProcessAnchor(
                    chapterIndex = 0,
                    chapterPosition = chapterPosition,
                    selectedText = normalizedSelectedText,
                    normalizedTextHash = MD5Utils.md5Encode(normalizedSelectedText),
                )
            ),
            actionJson = GSON.toJson(TextProcessAction.replace(replacement)),
        )
    }
}
