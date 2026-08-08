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

    @Test
    fun `用户划线标记不改文本但计入 effectiveProcesses`() {
        val mark = markProcess(
            selectedText = "看见桌上的信",
            kind = BookContentProcess.KIND_USER_UNDERLINE,
        )

        val result = BookContentProcessEngine.apply(
            content = "他走进房间，看见桌上的信。",
            processes = listOf(mark),
        )

        // 文本不被改写
        assertEquals("他走进房间，看见桌上的信。", result.text)
        // 锚点能解析 ⇒ 标记有效，计入 effectiveProcesses 供渲染层应用样式
        assertEquals(listOf(mark), result.effectiveProcesses)
    }

    @Test
    fun `锚点解析不到的标记不进 effectiveProcesses`() {
        val mark = markProcess(
            selectedText = "不存在的文本",
            kind = BookContentProcess.KIND_USER_HIGHLIGHT,
        )

        val result = BookContentProcessEngine.apply(
            content = "他走进房间，看见桌上的信。",
            processes = listOf(mark),
        )

        assertTrue(result.effectiveProcesses.isEmpty())
    }

    @Test
    fun `resolveRange 返回锚点文本对应的字符区间`() {
        val anchor = TextProcessAnchor(
            chapterIndex = 0,
            chapterPosition = 4,
            selectedText = "看见桌上的信",
            normalizedTextHash = MD5Utils.md5Encode("看见桌上的信"),
        )

        val range = BookContentProcessEngine.resolveRange(
            content = "他走进房间，看见桌上的信。",
            anchor = anchor,
        )

        assertEquals(6 until 12, range)
    }

    private fun markProcess(
        selectedText: String,
        kind: String,
    ): BookContentProcess {
        val normalized = BookContentProcessEngine.normalizeProcessText(selectedText)
        return BookContentProcess(
            id = "mark-$kind",
            bookUrl = "book",
            chapterIndex = 0,
            kind = kind,
            stage = BookContentProcess.STAGE_CONTENT,
            target = BookContentProcess.TARGET_SELECTION,
            anchorJson = GSON.toJson(
                TextProcessAnchor(
                    chapterIndex = 0,
                    chapterPosition = 0,
                    selectedText = normalized,
                    normalizedTextHash = MD5Utils.md5Encode(normalized),
                )
            ),
            actionJson = GSON.toJson(
                TextProcessAction(
                    TextProcessAction.TYPE_MARK,
                    text = normalized
                )
            ),
            styleJson = GSON.toJson(
                TextProcessStyle(
                    underlineMode = 1,
                    underlineColor = 0xFFFF0000.toInt()
                )
            ),
        )
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
