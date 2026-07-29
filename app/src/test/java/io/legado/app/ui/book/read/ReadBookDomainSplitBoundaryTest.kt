package io.legado.app.ui.book.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

/**
 * R2.2 —— 从 `ReadBookViewModel` 摘出的各域的边界不变式。
 *
 * 每摘一个域，在 [DOMAINS] 里加一条即可。三类会悄悄失效的边界：
 *
 * 1. 域状态被重新塞回 [ReadBookUiState]——该域每次刷新又开始 copy 整个阅读态；
 * 2. 域的实现回流进 `ReadBookViewModel`——god object 重新长回来；
 * 3. delegate 自己拿 DAO——`build.gradle.kts` 的 `legacyDaoInjectionBaseline` 只认
 *    **文件名含 `ViewModel`** 的文件，delegate 里的 DAO 直连会掉进宽松的
 *    `legacyUiDaoAccessBaseline`，等于把 VM 棘轮上的债洗白。章节等数据读取必须继续
 *    走各 delegate 的 `Host`，随 R2.1 的 `ReaderSession` 溶解一并清理。
 */
class ReadBookDomainSplitBoundaryTest {

    @Test
    fun `已摘出的域状态不再挂在 ReadBookUiState 上`() {
        val readBookFields = constructorParameterNames(ReadBookUiState::class)
        DOMAINS.forEach { domain ->
            val leaked = readBookFields.intersect(domain.stateFields)
            assertTrue(
                "${domain.name}域的状态又挂回了 ReadBookUiState：${leaked.joinToString()}。\n" +
                    "该域每次刷新都会让整个 ReadBookUiState 反复 copy——" +
                    "请放进 ${domain.delegateSimpleName} 自持的 state。",
                leaked.isEmpty(),
            )
        }
    }

    @Test
    fun `ReadAiUiState 完整覆盖 AI 的四个子状态`() {
        // AI 域是唯一有包装类型的域；这条保证下面 stateFields 的名单不会因改名而失真。
        assertEquals(
            "ReadAiUiState 的字段变了，请同步 DOMAINS 里 AI 域的 stateFields",
            setOf("chapterSummary", "aiTextClean", "aiTextRewrite", "aiRewritePresetConfig"),
            constructorParameterNames(ReadAiUiState::class),
        )
    }

    @Test
    fun `ReadBookViewModel 不再持有各域的实现`() {
        val source = mainSourceFile("io/legado/app/ui/book/read/ReadBookViewModel.kt").readText()
        DOMAINS.forEach { domain ->
            val leaked = domain.stateTypes.filter { it in source }
            assertTrue(
                "ReadBookViewModel 里又出现了${domain.name}域的状态类型：${leaked.joinToString()}。\n" +
                    "该域的逻辑属于 ${domain.delegateSimpleName}，" +
                    "VM 只做 `xxxDelegate.yyy()` 转发和 Host 实现。",
                leaked.isEmpty(),
            )
        }
    }

    @Test
    fun `各 delegate 不自带 DAO 直连`() {
        DOMAINS.forEach { domain ->
            val source = mainSourceFile(domain.delegateFile).readText()
            val violations = buildList {
                if (APP_DB_DAO.containsMatchIn(source)) add("appDb.xxxDao 直连")
                if (DAO_IMPORT.containsMatchIn(source)) add("import io.legado.app.data.dao.*")
            }
            assertTrue(
                "${domain.delegateSimpleName} 出现了 ${violations.joinToString()}。\n" +
                    "legacyDaoInjectionBaseline 只统计文件名含 `ViewModel` 的文件，" +
                    "delegate 里的 DAO 直连会掉进宽松的 legacyUiDaoAccessBaseline，" +
                    "等于把 VM 棘轮上的债洗白。请改走该 delegate 的 Host。",
                violations.isEmpty(),
            )
        }
    }

    private fun constructorParameterNames(type: KClass<*>): Set<String> =
        type.primaryConstructor?.parameters?.mapNotNull { it.name }?.toSet().orEmpty()

    private data class DomainSplit(
        val name: String,
        val delegateFile: String,
        /** 不允许再出现在 ReadBookUiState 里的字段名。 */
        val stateFields: Set<String>,
        /** 不允许再出现在 ReadBookViewModel.kt 里的状态类型名。 */
        val stateTypes: List<String>,
    ) {
        val delegateSimpleName: String get() = delegateFile.substringAfterLast('/').removeSuffix(".kt")
    }

    private companion object {
        val DOMAINS = listOf(
            DomainSplit(
                name = "AI",
                delegateFile = "io/legado/app/ui/book/read/ReadAiDelegate.kt",
                stateFields = setOf(
                    "chapterSummary",
                    "aiTextClean",
                    "aiTextRewrite",
                    "aiRewritePresetConfig",
                ),
                stateTypes = listOf(
                    "ChapterSummaryUiState",
                    "AiTextCleanUiState",
                    "AiTextRewriteUiState",
                    "AiRewritePresetConfigUiState",
                    "AiRewritePresetUi",
                    "AiRewriteHistoryUi",
                ),
            ),
            DomainSplit(
                name = "高亮规则",
                delegateFile = "io/legado/app/ui/book/read/ReadHighlightRuleDelegate.kt",
                stateFields = setOf("highlightRuleConfig"),
                stateTypes = listOf("HighlightRuleConfigUiState"),
            ),
            DomainSplit(
                name = "正文编辑",
                delegateFile = "io/legado/app/ui/book/read/ReadContentEditDelegate.kt",
                stateFields = setOf(
                    "contentEditLoading",
                    "contentEditText",
                    "contentEditTitle",
                    "contentEditCursorOffset",
                    "contentEditIsLocalTxt",
                    "contentEditSaveToSource",
                ),
                stateTypes = listOf("ContentEditUiState"),
            ),
            // 配置分发域无自持状态：stateFields 为空，靠 stateTypes 守「158 分支不回流 VM」
            DomainSplit(
                name = "配置更新分发",
                delegateFile = "io/legado/app/ui/book/read/ReadConfigUpdateDelegate.kt",
                stateFields = emptySet(),
                stateTypes = listOf("is ConfigUpdate."),
            ),
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
