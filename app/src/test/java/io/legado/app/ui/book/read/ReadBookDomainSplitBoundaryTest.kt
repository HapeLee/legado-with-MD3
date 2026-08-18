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

    /**
     * R2 的终态验收线。不是为了追行数好看——超过这个数就说明又有新的域直接长在 VM 里，
     * 而不是长成一个 delegate。要放宽必须先说明新增的是哪个域、为什么不能摘。
     *
     * 2500 → 2520：合上游后放宽 20 行。溢出的不是新域，是 `buildSheetConfig()` 这张
     * 投影表——上游给页眉页脚加了字体/字号/`applyHeaderStyle`/`tipDividerColor`，
     * 再加两个对齐项，一个字段就是一行，纯派生、没有逻辑可摘。上游同批带来的
     * `useNewTocSheet` 分支（书籍信息/目录改开 Sheet）本来是两处复制粘贴，
     * 已合并成 `openBookNavigation()`，那部分没占额度。
     *
     * 2520 → 2523：下滑手势切换书签。新增的不是域，书签域早已是
     * `ReadBookmarkDelegate`——切换判定、页范围计算、`ReaderBookmarkState` 快照的
     * 订阅与清理全在该 delegate 里。留在 VM 的是 8 行纯接线，逐行都摘不掉：
     * `bookKey` 的投影（`bookmarks` 表以书名+作者为关联键，只有 VM 持有 `_uiState`）、
     * `Host.emitEffect` 的实现（`_effects` 只有 VM 能碰）、`start()` 与
     * `ToggleBookmark` 的转发各一行，以及一个 `map` import。
     *
     * 2523 → 2533：分支基准移动了 10 行。2523 是 PR 基于更早的 main（VM 2515 行）定
     * 的线；合入当前 main 后 VM 已 2525 行（上游页眉页脚对齐、书籍信息/目录改 Sheet
     * 等），本 PR 的 8 行接线叠加为 2533。溢出全部来自上游合并，不是新长出来的域。
     *
     * 2533 → 2546：自定义书签角标域。2533 是上一条合入后 VM 恰好在线的值；后续上游
     * 又小幅增长到 2534，验收线本身已过时。本次新增 `BookmarkBadgeDelegate` 域留在
     * VM 的是 12 行纯接线——构造参数四个、两个意图分支各一行，逐行都摘不掉：文件
     * 拷贝需要 IO 协程与 context，只能住在 delegate，VM 只转发（与书签域同款）。
     *
     * 2546 → 2589：划线/高亮笔记域（初版联动了书签，`SaveBookmarkMarkingUseCase`
     * 同批注入书签/正文处理两个 delegate 做双向删除）。VM 的 43 行增量全是接线——
     * 新增构造参数与 import、delegate 装配（Host 三个方法）、三个意图分支。逐行都
     * 摘不掉：意图入口与 Host 实现只能在 VM。
     *
     * 2589：该域后改为独立 `book_marks` 表（与书签、AI 正文处理完全解耦），
     * `BookmarkMarking*` 更名为 `Marking*`，`SaveMarkingUseCase` 不再碰书签。查看迁到
     * 目录 Sheet 的「笔记」页（TocViewModel 自持 flow），正文处理域退回纯 AI，VM 无
     * bookMarkingGateway 注入，划线域接线总量反而下降，本线不缩。
     *
     * 2589 → 2595：划线笔记编辑入口。目录 Sheet 笔记页点标记项进 MarkingSheet 编辑，
     * 新增 `EditMarking`/`DeleteMarking` 两个意图分支共 6 行，纯接线——意图入口只能在 VM。
     *
     * 2595 → 2608：编辑后返回原 sheet。从目录 Sheet 进编辑，保存/删除/取消要回目录而
     * 非阅读页——`markingReturnSheet` 字段、恢复函数与三个意图分支各记几行，共 13 行。
     * activeSheet 在 UiState 里，只有 VM 能管，摘不成 delegate。
     *
     * 2608 → 2643：书签/笔记跳转校验域。新增 `ReadBookmarkNavigateDelegate` 域，VM 留
     * 35 行纯接线——构造 + Host（Host 的 jumpToChapter 要 onIntent 派发、setPendingTarget
     * 要写 UiState，只能 VM）、四个意图分支、构造参数与 import。校验逻辑全在 delegate。
     *
     * 2643 → 2651：验收线与当前已合入实现不一致；本次仅校准既有接线的实际行数，
     * 不放宽任何新增域的实现空间。
     *
     * 2651 → 2664：笔记/书签角标域接线 + AI 档位转发，两个提交叠加溢出 13 行，全部是
     * 纯接线，逐行都摘不掉：
     *
     * - `a130dddc4`（笔记功能和书签标识自定义）：`MarkingDelegate` / `ReadBookmarkNavigateDelegate`
     *   / `BookmarkBadgeDelegate` 三个域**早已登记在下方 DOMAINS 里**（划线笔记 / 跳转校验 /
     *   书签角标），边界守住了——VM 新增的百余行全是构造参数与 import、delegate 装配与
     *   Host 实现、七个意图/sheet 分支（OpenMarking / EditMarking / DismissMarking /
     *   SaveMarking / BookmarkBadgeImageSelected / ClearBookmarkBadgeImage / Marking），
     *   以及 `markingReturnSheet` 返回原 sheet 的瞬态字段——activeSheet 在 UiState 里，
     *   只有 VM 能管，摘不成 delegate。意图入口与 Host 实现与书签域同款，只能在 VM。
     * - `caafbfdde`（优化一些AI功能）：AI 域三个 reasoning level 意图分支转发给
     *   `aiDelegate`——意图入口只能在 VM，每档两行（分支 + 转发）。
     *
     * 2664 → 2668：朗读域新增两个意图分支（安卓媒体控制 / 定时到点后读完本章），
     * 合并自 PR #2024。朗读域早已是 `ReadAloudDelegate`——留在 VM 的只有两条 `when`
     * 分支转发，各两行（分支 + 转发），与上方 `SetReadAloudSystemMediaCompat` 等兄弟
     * 分支同款，逐行都摘不掉：意图入口只能在 VM。
     */
    @Test
    fun `ReadBookViewModel 不超过 R2 验收的 2668 行`() {
        val lineCount = mainSourceFile("io/legado/app/ui/book/read/ReadBookViewModel.kt")
            .readLines().size
        assertTrue(
            "ReadBookViewModel 涨到了 $lineCount 行，超过 R2 验收线 2668。\n" +
                "新功能请摘成 io/legado/app/ui/book/read/ 下的 XxxDelegate，" +
                "并在本测试的 DOMAINS 里加一条边界。",
            lineCount <= 2668,
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
