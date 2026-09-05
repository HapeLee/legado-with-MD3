package io.legado.app.ui.book.read

import io.legado.app.ui.book.read.ReadBookDomainSplitBoundaryTest.Companion.DOMAINS
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
    fun `Compose reader must remain the only production body renderer`() {
        listOf(
            "ui/book/read/page/ReadView.kt",
            "ui/book/read/page/PageView.kt",
            "ui/book/read/page/ContentTextView.kt",
            "ui/book/read/page/provider/TextChapterLayout.kt",
        ).forEach { relativePath ->
            assertTrue(
                "$relativePath must not be restored after the Compose Canvas migration",
                !mainSourcePath("io/legado/app/$relativePath").exists(),
            )
        }
        assertTrue(
            "view_book_page.xml must not be restored after the Compose Canvas migration",
            !projectPath("app/src/main/res/layout/view_book_page.xml").exists(),
        )
    }

    @Test
    fun `Canvas runtime must not recreate View page layout`() {
        val runtimeFiles = listOf(
            "model/ReadBook.kt",
            "ui/book/read/ReadBookController.kt",
            "ui/book/readaloud/player/ReadAloudPlayerCoordinator.kt",
            "service/BaseReadAloudService.kt",
            "service/TTSReadAloudService.kt",
            "service/HttpReadAloudService.kt",
        )
        runtimeFiles.forEach { path ->
            val source = mainSourceFile("io/legado/app/$path").readText()
            listOf(
                "import io.legado.app.ui.book.read.page.provider.ChapterProvider",
                "import io.legado.app.ui.book.read.page.entities.TextChapter",
                "getTextChapterAsync(",
                "ReadBook.curTextChapter",
            ).forEach { legacyDependency ->
                assertTrue("$path still depends on $legacyDependency", legacyDependency !in source)
            }
        }
    }

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
    fun `菜单书签保存章节内字符位置而不是页码`() {
        val source = mainSourceFile("io/legado/app/ui/book/read/ReadBookmarkDelegate.kt").readText()
        assertTrue(
            "书签 chapterPos 必须保存章节内字符位置，否则跳转时页码会被误当成字符偏移",
            "chapterPos = ReadBook.durChapterPos" in source &&
                "chapterPos = ReadBook.durPageIndex" !in source,
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
            // 开书域无自持状态：isInitFinish 是 Canvas 首帧的放行门闩，必须留在 UiState
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
                name = "阅读记录归属",
                delegateFile = "io/legado/app/ui/book/read/ReadRecordAliasDelegate.kt",
                stateFields = emptySet(),
                stateTypes = listOf("getUnknownAuthorRecords", "ReadRecordAliasDecision."),
            ),
            DomainSplit(
                name = "书签",
                delegateFile = "io/legado/app/ui/book/read/ReadBookmarkDelegate.kt",
                stateFields = emptySet(),
                // ReaderBookmarkState 是渲染层同步查角标用的快照：订阅 flowByBook 与
                // 退出时清理都归本域，VM 只投影 bookKey。
                stateTypes = listOf(
                    "bookmarkRepository.save",
                    "bookmarkRepository.delete",
                    "bookmarkRepository.flowByBook",
                    "ReaderBookmarkState",
                ),
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
            // 书签角标域无自持状态：图片拷贝落盘与解码缓存都在 delegate / 渲染层，
            // 靠 stateTypes 守「文件操作逻辑不回流 VM」（VM 只转发意图）
            DomainSplit(
                name = "书签角标",
                delegateFile = "io/legado/app/ui/book/read/BookmarkBadgeDelegate.kt",
                stateFields = emptySet(),
                stateTypes = listOf(
                    "copyToAppStorage",
                    "bookmark_badge.",
                ),
            ),
            // 划线笔记域自持临时会话状态：配置会话与落库都在 delegate / use case，
            // book_marks 表独立于书签与 AI 正文处理，靠 stateTypes 守「标记会话与
            // 保存逻辑不回流 VM」（VM 只转发意图并注入 use case）
            DomainSplit(
                name = "划线笔记",
                delegateFile = "io/legado/app/ui/book/read/MarkingDelegate.kt",
                stateFields = emptySet(),
                stateTypes = listOf(
                    "MarkingUiState",
                    "saveMarkingUseCase.save",
                    "highlightRuleRepository.load",
                ),
            ),
            // 跳转校验域无自持状态：校验逻辑在 delegate，确认框状态 pendingBookmarkTarget
            // 是瞬态对话框（同 activeDialog），留 UiState；靠 stateTypes 守「校验与跳转不回流 VM」
            DomainSplit(
                name = "跳转校验",
                delegateFile = "io/legado/app/ui/book/read/ReadBookmarkNavigateDelegate.kt",
                stateFields = emptySet(),
                stateTypes = listOf(
                    "verifyUseCase.verify",
                    "bookRepository.getChapterTitle",
                ),
            ),
        )

        val APP_DB_DAO = Regex("""\bappDb\.[A-Za-z0-9_]*Dao\b""")
        val DAO_IMPORT = Regex(
            """^import io\.legado\.app\.data\.dao\.[A-Za-z0-9_*]+$""",
            RegexOption.MULTILINE,
        )

        fun mainSourceFile(relativePath: String): File {
            val candidate = mainSourcePath(relativePath)
            if (candidate.isFile) return candidate
            error("从 ${File("").absolutePath} 向上找不到 $relativePath")
        }

        fun mainSourcePath(relativePath: String): File = locateProjectPath(
            candidates = listOf("src/main/java/$relativePath", "app/src/main/java/$relativePath"),
        )

        fun projectPath(relativePath: String): File = locateProjectPath(
            candidates = listOf(relativePath, relativePath.removePrefix("app/")),
        )

        private fun locateProjectPath(candidates: List<String>): File {
            var directory: File? = File("").absoluteFile
            while (directory != null) {
                candidates.forEach { relativePath ->
                    val candidate = File(directory, relativePath)
                    if (candidate.exists()) return candidate
                }
                if (File(directory, ".git").exists()) return File(directory, candidates.first())
                directory = directory.parentFile
            }
            return File(candidates.first())
        }
    }
}
