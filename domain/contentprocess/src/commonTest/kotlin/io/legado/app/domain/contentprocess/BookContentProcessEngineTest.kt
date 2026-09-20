package io.legado.app.domain.contentprocess

import io.legado.app.core.platform.JsonCodec
import io.legado.app.domain.model.TextProcessAction
import io.legado.app.domain.model.TextProcessAnchor
import io.legado.app.domain.model.TextProcessStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 正文处理引擎的用例（M4-8：从 `:app/src/test/.../domain/model/` 随被测对象搬到本模块
 * `commonTest`）。
 *
 * 搬迁时改了四处，都是"目标模块拿不到"造成的**等价替换**，断言与输入逐字未动：
 *  1. `org.junit.Test` / `org.junit.Assert` → `kotlin.test`（本模块是 KMP，commonTest 只有后者）；
 *  2. `GSON.toJson(...)` → `JsonCodec.toJson(...)`：`GSON` 门面住 `:core:data` 的 androidMain，
 *     本模块只能看见 `:core:platform` 的 `JsonCodec`；两者写出配置一致，且引擎本身就用
 *     `JsonCodec.fromJsonObject` 读回 ⇒ 用同一个编解码器构造输入反而更贴实。
 *  3. `MD5Utils.md5Encode(...)` → 常量：`normalizedTextHash` 在引擎里**从未被读取**
 *     （`findTargetRange` 只用 `selectedText` 与 `chapterPosition`），原测试填它也只是为了
 *     构造一个"完整"的锚点。这里显式写明，免得后人以为它参与匹配。
 *  4. `BookContentProcess` 从 Room 实体换成领域模型（同包），常量解析到模型自己的 companion。
 */
class BookContentProcessEngineTest {

    @Test
    fun matchesReaderSelectionWithLayoutWhitespace() {
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
    fun usesClosestNormalizedMatch() {
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
    fun userMarkDoesNotRewriteTextButCountsAsEffective() {
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
    fun markWithUnresolvableAnchorIsDropped() {
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
    fun resolveRangeReturnsCharacterRangeOfAnchorText() {
        val anchor = anchor(selectedText = "看见桌上的信", chapterPosition = 4)

        val range = BookContentProcessEngine.resolveRange(
            content = "他走进房间，看见桌上的信。",
            anchor = anchor,
        )

        assertEquals(6 until 12, range)
    }

    /** 非 `STATUS_ACTIVE` 的处理项被过滤掉（引擎的第一个 filter）。 */
    @Test
    fun disabledOrDraftProcessIsFilteredOut() {
        val draft = process(
            selectedText = "他走进房间",
            replacement = "X",
        ).copy(status = BookContentProcess.STATUS_DRAFT)
        val disabled = process(
            selectedText = "他走进房间",
            replacement = "Y",
        ).copy(enabled = false)

        val result = BookContentProcessEngine.apply(
            content = "他走进房间，看见桌上的信。",
            processes = listOf(draft, disabled),
        )

        assertEquals("他走进房间，看见桌上的信。", result.text)
        assertTrue(result.effectiveProcesses.isEmpty())
    }

    private fun anchor(
        selectedText: String,
        chapterPosition: Int,
    ) = TextProcessAnchor(
        chapterIndex = 0,
        chapterPosition = chapterPosition,
        selectedText = BookContentProcessEngine.normalizeProcessText(selectedText),
        // 引擎不读这个字段，填常量即可（见文件头第 3 点）。
        normalizedTextHash = "unused-by-engine",
    )

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
            anchorJson = JsonCodec.toJson(anchor(selectedText, 0)),
            actionJson = JsonCodec.toJson(
                TextProcessAction(
                    TextProcessAction.TYPE_MARK,
                    text = normalized
                )
            ),
            styleJson = JsonCodec.toJson(
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
        return BookContentProcess(
            id = "test",
            bookUrl = "book",
            chapterIndex = 0,
            kind = BookContentProcess.KIND_AI_CLEAN,
            anchorJson = JsonCodec.toJson(anchor(selectedText, chapterPosition)),
            actionJson = JsonCodec.toJson(TextProcessAction.replace(replacement)),
        )
    }
}
