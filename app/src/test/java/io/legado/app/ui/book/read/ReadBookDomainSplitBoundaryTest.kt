package io.legado.app.ui.book.read

import io.legado.app.ui.book.read.ReadBookDomainSplitBoundaryTest.Companion.DOMAINS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

/**
 * 从 `ReadBookViewModel` 摘出的各域的边界不变式。
 *
 * 每摘一个域，在 [DOMAINS] 里加一条即可。三类会悄悄失效的边界：
 *
 * 1. 域状态被重新塞回 [ReadBookUiState]——该域每次刷新又开始 copy 整个阅读态；
 * 2. 域的实现回流进 `ReadBookViewModel`——god object 重新长回来；
 * 3. delegate 自己拿 DAO——`build.gradle.kts` 的 `legacyDaoInjectionBaseline` 只认
 *    **文件名含 `ViewModel`** 的文件，delegate 里的 DAO 直连会掉进宽松的
 *    `legacyUiDaoAccessBaseline`，等于把 VM 棘轮上的债洗白。章节等数据读取必须继续
 *    走各 delegate 的 `Host`——Host 背后是 `BookRepository`。
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

    /**
     * 验收线（现行上限见测试名）。不是为了追行数好看——超过这个数说明又有新的域直接
     * 长在 VM 里，而不是长成一个 delegate。要放宽必须先说明新增的是哪个域、为什么不能摘。
     * 行数只作粗棘轮：散落在 VM 的域内状态比总行数更能说明边界是否干净，各域另受
     * `域状态不回流进 ReadBookViewModel` 守卫约束。
     *
     * VM 里合法保留、逐行摘不掉的接线类型：
     * - 意图入口：`when` 分支 + 一行 delegate 转发（朗读定时、内容划分、退出继续后台
     *   朗读、下滑切书签、划线笔记编辑与返回原 sheet、角标选图、AI 档位、阅读锚点、
     *   `backToSpeakingPosition` / `ReadAloudFromHere` 等——意图入口只能在 VM）；
     * - Host 实现与状态投影：`_uiState` / `_effects` 只有 VM 能碰（bookKey 投影、
     *   `markingReturnSheet` 瞬态字段、`readAloudFollow`）；`_seekState` 与
     *   `refreshFromReadBook()` / `publishSeek()` 同属这类投影——定位字段必须从
     *   `ReadBook` 单例现算（`calculateSeekProgress` / `calculateSeekMax` 是 VM 私有），
     *   且写入点跟着 `syncFromReadBook` 的每个发布点走，没有可摘的 delegate；
     * - delegate 构造参数、装配与 import；
     * - `buildSheetConfig()` 投影表：页眉页脚的字体/字号/`applyHeaderStyle`/
     *   `tipDividerColor`/对齐项纯派生，一个字段一行，没有逻辑可摘；
     *   `useNewTocSheet` 两处复制粘贴已合并成 `openBookNavigation()`，不占额度；
     * - `buildStyleConfig()` 里的翻页动画速度挡位取值：挡位要和 `pageAnim` 落进同一份
     *   `ReadBookStyleConfig` 快照供 GlobalThemePage 反应式读取，快照构造点就在 VM；
     *   其余在 `ReaderPageTurnSpeed`、`ReaderCanvasSurface` 与配置链路，不占 VM 行；
     * - `stopReadAloudForClose()` 读 `keepReadAloudOnExit` 的短路判定：关闭朗读的
     *   决策点只能在 VM。
     *
     * 各域实现本体都不占额度：朗读定时模式与章数解析、内容划分与标点集合校验在
     * `ReadAloudDelegate` / `ReadAloudSettingsRepository`（`setContentSplit`、
     * `setTimerMode` / `setTimerChapters`）；`backToSpeakingPosition()` 本体、书签切换
     * 判定与页范围计算在 `ReadAloudDelegate` / `ReadBookmarkDelegate`；角标文件拷贝在
     * `BookmarkBadgeDelegate`；跳转校验在 `ReadBookmarkNavigateDelegate`；翻译状态观察
     * 在 `ReadAiDelegate`；划线笔记来源 sheet 在 `MarkingDelegate`。语义不同的设置
     * 不为压行数合并成一个载荷——那会让「只改章数」也必须带上模式。
     *
     * 官方链路（3.26.16-beta.41 / beta.43 等）在 VM 加的接线——朗读浮层兼容分支
     * （`MainRouteReadAloudPlayer`）、`locateAfterPagination` 提交路径的快照发布、
     * 页眉页脚字段等——不属于我们任一域，按官方行数计入棘轮，不替官方摘 delegate。
     * 主线既有、尚未清偿的约 58 行接线仍须按各域单独瘦身，不视为已豁免。划线笔记用
     * 独立 `book_marks` 表（与书签、AI 正文处理解耦），查看在目录 Sheet 的「笔记」页
     * （TocViewModel 自持 flow）。
     */
    @Test
    fun `ReadBookViewModel 不超过 2742 行`() {
        val lineCount = mainSourceFile("io/legado/app/ui/book/read/ReadBookViewModel.kt")
            .readLines().size
        assertTrue(
            "ReadBookViewModel 涨到了 $lineCount 行，超过上限 2742。\n" +
                "新功能请摘成 io/legado/app/ui/book/read/ 下的 XxxDelegate，" +
                "并在本测试的 DOMAINS 里加一条边界。",
            lineCount <= 2742,
        )
    }

    /**
     * 允许留在宿主 ViewModel 的私有状态，以及它必须留下的理由。
     *
     * 键必须与 VM 里实际声明的状态一一对应，双向校验：
     * - 出现未登记的字段 → 说明有域状态回流。能摘进 delegate 的就摘；
     * - 登记的字段已不存在 → 必须删条目，防止名单只增不减、变成永久豁免。
     *
     * 锚点字段（`_xxx` 流后备）与可变状态（`private var`）都算；`by lazy` 的 delegate
     * 字段与依赖注入不在此列。
     */
    private val hostOwnedState = mapOf(
        "_uiState" to "阅读页唯一的对外状态源，宿主渲染直接消费",
        "_effects" to "一次性效果与 toast 通道",
        "_readAloudProgress" to "朗读进度流，服务层与 UI 协作",
        "_readPreferences" to "阅读偏好流，多屏共用",
        "readBookSyncJob" to "UiState 全量重建的尾随合并，属于宿主渲染节流",
        "backupJob" to "退出/切后台触发的备份任务，绑定宿主生命周期",
        "pendingBooksDirReloadChapterList" to "跨 Activity Result 回调的参数，由宿主持有",
        "deferredReaderFeaturesStarted" to "入口动画结束后的多域启动协调，属于宿主编排",
        "composePagePosition" to "Compose 阅读页跨帧进度（待下沉：进度/排版域）",
        "composePageContext" to "Compose 跨帧渲染上下文（待下沉：进度/排版域）",
        "composeProgressJob" to "Compose 进度节流任务（待下沉：进度/排版域）",
        "_seekState" to "底栏进度条与锚点胶囊的定位流，见 ReadSeekUiState",
        "justInitData" to "加载域经 Host 暴露的状态（待下沉：加载域）",
        "closeReadBookKeepReadAloud" to "关闭阅读是否保留朗读的参数（待下沉：朗读域）",
    )

    /**
     * 已从 [ReadBookUiState] 摘出的字段，一律不许挂回去。
     *
     * 阅读屏在屏幕作用域读整份 `ReadBookUiState`，所以任何一个字段变化都会重组正文画布
     * 之外的全部 chrome。这些字段恰好都是高频刷新源：
     * - `seekProgress` / `seekMax` / `readingAnchorAvailable`：翻页、拖进度条、
     *   `upSeekBarThrottle`（200 ms）都会刷，已摘进 `ReadBookViewModel.seekState`，
     *   只有 `MenuBottomBar` 与 `ReadBookFloatingActionBar` 各自收集；
     * - `time` / `battery`：EventBus 每分钟广播，已摘成 VM 的 `@Volatile` 直读字段，
     *   消费方只有 `ReadBookController` 建 decoration 时；
     * - `durPageIndex`：只写不读（Canvas 页位置经 `composePagePosition` 同步），
     *   留在全屏 state 里等于每次翻页白付一次整屏重组。
     */
    private val screenWideStateFields = setOf(
        "seekProgress",
        "seekMax",
        "readingAnchorAvailable",
        "time",
        "battery",
        "durPageIndex",
    )

    @Test
    fun `高频定位与页眉字段不挂回 ReadBookUiState`() {
        val leaked = constructorParameterNames(ReadBookUiState::class).intersect(screenWideStateFields)
        assertTrue(
            "这些字段又挂回了 ReadBookUiState：${leaked.joinToString()}。\n" +
                "它们一刷新就让整个阅读屏重组，请回到各自的窄流 / 直读字段，" +
                "理由见本测试的文档注释。",
            leaked.isEmpty(),
        )
        assertEquals(
            "ReadSeekUiState 的字段变了，请同步 screenWideStateFields 与消费方",
            setOf("seekProgress", "seekMax", "readingAnchorAvailable"),
            constructorParameterNames(ReadSeekUiState::class),
        )
    }

    @Test
    fun `域状态不回流进 ReadBookViewModel`() {
        val source = mainSourceFile("io/legado/app/ui/book/read/ReadBookViewModel.kt").readText()
        val declared = buildSet {
            Regex("""^\s*(?:@\w+\s+)?private var (\w+)""", RegexOption.MULTILINE)
                .findAll(source)
                .forEach { add(it.groupValues[1]) }
            Regex("""^\s+private val (_\w+)""", RegexOption.MULTILINE)
                .findAll(source)
                .forEach { add(it.groupValues[1]) }
        }

        val unregistered = declared - hostOwnedState.keys
        assertTrue(
            "以下状态没有登记归属：$unregistered。\n" +
                    "域状态请摘进 io/legado/app/ui/book/read/ 下对应的 XxxDelegate；" +
                    "确实属于宿主编排的，在 hostOwnedState 里补一条并写明理由。",
            unregistered.isEmpty(),
        )

        val stale = hostOwnedState.keys - declared
        assertTrue(
            "hostOwnedState 里这些字段已不在 ViewModel 中，请删除条目：$stale",
            stale.isEmpty(),
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
                "书籍与目录的读写全部收在 BookRepository，" +
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
            // 多角色分配域：确认/创建/取消与收尾全在 delegate，VM 只剩三个意图分支转发。
            // 标记读写与角色表访问全收口 CastAssignmentStore（架构护栏）。
            DomainSplit(
                name = "多角色分配",
                delegateFile = "io/legado/app/ui/book/read/ReadAloudCastDelegate.kt",
                stateFields = emptySet(),
                stateTypes = listOf("CastAssignmentStore", "CastResult"),
            ),
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
            // 云端进度同步域无自持状态：投影目标 isReadingProgressSyncConfigured 是菜单
            // 可见性输入，仍在 UiState；靠 stateTypes 守「订阅云端可用性流 + 开菜单补
            // 初始化不回流 VM」——这两件事一回流，入口就会重新变成「读一次快照」。
            DomainSplit(
                name = "云端进度同步",
                delegateFile = "io/legado/app/ui/book/read/ReadingProgressSyncDelegate.kt",
                stateFields = emptySet(),
                stateTypes = listOf(
                    "isConfiguredFlow",
                    "useCase.ensureConfigured",
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
