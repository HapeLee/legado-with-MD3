package io.legado.app.ui.book.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.reflect.full.primaryConstructor

/**
 * R2.2 —— AI 域摘除后的边界不变式。
 *
 * AI 域（章节摘要 / 划词净化 / 划词重写 / 重写预设）已从 `ReadBookViewModel` 摘成
 * [ReadAiDelegate]，状态独立成 [ReadAiUiState]。三条会悄悄失效的边界：
 *
 * 1. AI 子状态被重新塞回 [ReadBookUiState]——流式输出又开始 copy 整个阅读态；
 * 2. AI 实现回流进 `ReadBookViewModel`——god object 重新长回来；
 * 3. `ReadAiDelegate` 自己拿 DAO——`legacyDaoInjectionBaseline` 只认文件名含 `ViewModel`
 *    的文件，delegate 里的 DAO 直连会掉进宽松的 `legacyUiDaoAccessBaseline`，
 *    等于把 VM 棘轮上的债洗白。章节读取必须继续走 `ReadAiDelegate.Host`。
 */
class ReadAiDomainBoundaryTest {

    @Test
    fun `AI 子状态只挂在 ReadAiUiState 上`() {
        assertEquals(
            "ReadAiUiState 的字段与 AI 子状态不一致——新增/改名 AI 子状态时请同步本测试",
            AI_STATE_FIELDS,
            constructorParameterNames(ReadAiUiState::class),
        )

        val leaked = constructorParameterNames(ReadBookUiState::class)
            .intersect(AI_STATE_FIELDS)
        assertTrue(
            "AI 子状态又挂回了 ReadBookUiState：${leaked.joinToString()}。\n" +
                "AI 流式输出每秒刷新多次，挂在阅读态上会让整个 ReadBookUiState 反复 copy——" +
                "请放进 ReadAiUiState，由 ReadAiDelegate 持有。",
            leaked.isEmpty(),
        )
    }

    @Test
    fun `ReadBookViewModel 不再持有 AI 域实现`() {
        val source = mainSourceFile("io/legado/app/ui/book/read/ReadBookViewModel.kt").readText()
        val leaked = AI_STATE_TYPES.filter { it in source }
        assertTrue(
            "ReadBookViewModel 里又出现了 AI 域状态类型：${leaked.joinToString()}。\n" +
                "AI 逻辑属于 ReadAiDelegate，VM 只做 `aiDelegate.xxx()` 转发和 Host 实现。",
            leaked.isEmpty(),
        )
    }

    @Test
    fun `ReadAiDelegate 不自带 DAO 直连`() {
        val source = mainSourceFile("io/legado/app/ui/book/read/ReadAiDelegate.kt").readText()
        val violations = buildList {
            if (APP_DB_DAO.containsMatchIn(source)) add("appDb.xxxDao 直连")
            if (DAO_IMPORT.containsMatchIn(source)) add("import io.legado.app.data.dao.*")
        }
        assertTrue(
            "ReadAiDelegate 出现了 ${violations.joinToString()}。\n" +
                "build.gradle.kts 的 legacyDaoInjectionBaseline 只统计文件名含 `ViewModel` 的文件，" +
                "delegate 里的 DAO 直连会掉进宽松的 legacyUiDaoAccessBaseline，等于把 VM 棘轮上的债洗白。\n" +
                "章节读取请继续走 ReadAiDelegate.Host，等 R2.1 随 ReaderSession 一起溶解。",
            violations.isEmpty(),
        )
    }

    private fun constructorParameterNames(type: kotlin.reflect.KClass<*>): Set<String> =
        type.primaryConstructor?.parameters?.mapNotNull { it.name }?.toSet().orEmpty()

    private companion object {
        val AI_STATE_FIELDS = setOf(
            "chapterSummary",
            "aiTextClean",
            "aiTextRewrite",
            "aiRewritePresetConfig",
        )

        val AI_STATE_TYPES = listOf(
            "ChapterSummaryUiState",
            "AiTextCleanUiState",
            "AiTextRewriteUiState",
            "AiRewritePresetConfigUiState",
            "AiRewritePresetUi",
            "AiRewriteHistoryUi",
        )

        val APP_DB_DAO = Regex("""\bappDb\.[A-Za-z0-9_]*Dao\b""")
        val DAO_IMPORT = Regex(
            """^import io\.legado\.app\.data\.dao\.[A-Za-z0-9_*]+$""",
            RegexOption.MULTILINE,
        )

        fun mainSourceFile(relativePath: String): File {
            var directory: File? = File("").absoluteFile
            while (directory != null) {
                for (prefix in listOf("src/main/java", "app/src/main/java")) {
                    val candidate = File(directory, "$prefix/$relativePath")
                    if (candidate.isFile) return candidate
                }
                directory = directory.parentFile
            }
            error("从 ${File("").absolutePath} 向上找不到 $relativePath")
        }
    }
}
