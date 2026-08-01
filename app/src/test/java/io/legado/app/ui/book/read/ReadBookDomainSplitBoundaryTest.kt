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
 *    走各 delegate 的 `Host`——R2.1 之后 Host 背后是 `BookRepository`。
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

    /**
     * R2 的终态验收线。不是为了追行数好看——超过这个数就说明又有新的域直接长在 VM 里，
     * 而不是长成一个 delegate。要放宽必须先说明新增的是哪个域、为什么不能摘。
     *
     * 2500 → 2520：合上游后放宽 20 行。溢出的不是新域，是 `buildSheetConfig()` 这张
     * 投影表——上游给页眉页脚加了字体/字号/`applyHeaderStyle`/`tipDividerColor`，
     * 再加两个对齐项，一个字段就是一行，纯派生、没有逻辑可摘。上游同批带来的
     * `useNewTocSheet` 分支（书籍信息/目录改开 Sheet）本来是两处复制粘贴，
     * 已合并成 `openBookNavigation()`，那部分没占额度。
     */
    @Test
    fun `ReadBookViewModel 不超过 R2 验收的 2520 行`() {
        val lineCount = mainSourceFile("io/legado/app/ui/book/read/ReadBookViewModel.kt")
            .readLines().size
        assertTrue(
            "ReadBookViewModel 涨到了 $lineCount 行，超过 R2 验收线 2520。\n" +
                "新功能请摘成 io/legado/app/ui/book/read/ 下的 XxxDelegate，" +
                "并在本测试的 DOMAINS 里加一条边界。",
            lineCount <= 2520,
        )
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

    @Test
    fun `ReadBookViewModel 不再直连 DAO`() {
        val source = mainSourceFile("io/legado/app/ui/book/read/ReadBookViewModel.kt").readText()
        val violations = buildList {
            APP_DB_DAO.findAll(source).forEach { add(it.value) }
            DAO_IMPORT.findAll(source).forEach { add(it.value) }
        }
        assertTrue(
            "ReadBookViewModel 又出现了 DAO 直连：${violations.joinToString()}。\n" +
                "R2.1 已把书籍/目录读写全部收进 BookRepository，" +
                "`legacyDaoInjectionBaseline` 里这个文件的基线是 0——" +
                "章节读取请用 currentChapter() 或 bookRepository 的方法。",
            violations.isEmpty(),
        )
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
            DomainSplit(
                name = "正文处理",
                delegateFile = "io/legado/app/ui/book/read/ReadContentProcessDelegate.kt",
                stateFields = setOf("contentProcessConfig"),
                stateTypes = listOf("ContentProcessConfigUiState", "ContentProcessItemUi"),
            ),
            // 开书域无自持状态：isInitFinish 是 ReadView 首帧的放行门闩，必须留在 UiState
            DomainSplit(
                name = "开书/换源",
                delegateFile = "io/legado/app/ui/book/read/ReadBookLoadDelegate.kt",
                stateFields = emptySet(),
                // 用「调用点」而不是「依赖名」当标记：依赖名在 VM 的 delegate 装配处
                // 本来就会出现，那是正当接线，不是逻辑回流。
                stateTypes = listOf(
                    "changeBookSourceUseCase.changeTo",
                    "WebBook.getChapterListAwait",
                    "uploadReadingProgressUseCase.execute",
                ),
            ),
            DomainSplit(
                name = "书签",
                delegateFile = "io/legado/app/ui/book/read/ReadBookmarkDelegate.kt",
                stateFields = emptySet(),
                stateTypes = listOf("bookmarkRepository.save", "bookmarkRepository.delete"),
            ),
            // 样式域无自持状态：styleConfig 的重建由 VM 的 collectReadStyle() 统一驱动，
            // activeReminder / eyeProtection 被菜单栏直读；靠 stateTypes 守
            // 「取色、日夜提醒判定、样式导入导出不回流 VM」
            DomainSplit(
                name = "阅读样式",
                delegateFile = "io/legado/app/ui/book/read/ReadStyleDelegate.kt",
                stateFields = emptySet(),
                stateTypes = listOf(
                    "ReadBookColorPickerIds",
                    "ReminderType.DayNightReminder",
                    "importCurrentStyle",
                    "saveBackgroundImage",
                ),
            ),
            // 朗读域无自持状态：20 来个朗读字段被四个 composable 直读，搬出去要改四处入参；
            // 靠 stateTypes 守「设置写入与合成管线重启逻辑不回流 VM」
            DomainSplit(
                name = "朗读",
                delegateFile = "io/legado/app/ui/book/read/ReadAloudDelegate.kt",
                stateFields = emptySet(),
                stateTypes = listOf(
                    "readAloudSettingsRepository.update",
                    "VoiceCatalogEntry",
                    "refreshReadAloudClass",
                ),
            ),
            // 按钮配置域无自持状态：按钮列表仍在 menuConfig 里，靠 stateTypes 守
            // 「SharedPreferences 读写和归一化逻辑不回流 VM」。上游曾把「更多操作」的
            // 归一化/解析直接长在 VM（MoreActionIds 是它的标记），已并回本域。
            DomainSplit(
                name = "菜单按钮配置",
                delegateFile = "io/legado/app/ui/book/read/ReadButtonConfigDelegate.kt",
                stateFields = emptySet(),
                stateTypes = listOf("ReadBookButtonIds", "getSharedPreferences", "MoreActionIds"),
            ),
            // 净化规则域无自持状态：allReplaceRules 被 TextProcessingSheet 直读，仍在
            // UiState；靠 stateTypes 守「规则读写与净化管线刷新不回流 VM」
            DomainSplit(
                name = "净化规则",
                delegateFile = "io/legado/app/ui/book/read/ReadReplaceRuleDelegate.kt",
                stateFields = emptySet(),
                stateTypes = listOf(
                    "replaceRuleRepository.flowAll",
                    "replaceRuleRepository.setEnabled",
                    "replaceRuleRepository.moveReplaceRule",
                    "replaceRuleRepository.insert",
                    "upReplaceRules",
                ),
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
