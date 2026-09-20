package io.legado.app.ui.book.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 锁定 [ConfigUpdate.actions] 与旧 View `EventBus.UP_CONFIG` 事件码的对应关系。
 *
 * 旧 View 证据：`git show 4c088448c^:app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt`
 * 的 `observeLiveBus()` 翻译表，以及各弹窗（FontConfigDialog / TipConfigDialog /
 * UnderlineConfigDialog / InfoConfigDialog / PaddingConfigDialog / MoreConfigDialog /
 * BgTextConfigDialog / ReadStyleDialog / ShadowSetDialog / ReadConfigViewModel）里的
 * `postEvent(EventBus.UP_CONFIG, arrayListOf(...))`。
 *
 * 事件码含义：0=UpdateSystemUi 1=UpdateBackground 2=UpdateStyle 3=UpdateBackgroundAlpha
 * 4=UpdatePageSlopSquare 5=ReloadContent 6=UpdateContent 8=UpdateChapterStyle
 * 9=InvalidateTextPage 10=UpdateLayout 11=SubmitRenderTask。
 *
 * 允许的 Compose 追加项（旧 View 无对应码）：
 * `RebuildWholeBookPageIndex`（整书页码估算）、`UpdateWholeBookPageDemand`（{FullPageIndex} 类
 * 页眉页脚）、`RefreshInlineImages`（样式方案/预设切换）、`UpdateSystemUi`（样式方案切换后刷新
 * 状态栏图标）。
 */
class LegacyConfigUpdateActionsTest {

    private val US = ConfigUpdateAction.UpdateStyle
    private val UC = ConfigUpdateAction.UpdateContent
    private val UBA = ConfigUpdateAction.UpdateBackgroundAlpha
    private val UB = ConfigUpdateAction.UpdateBackground
    private val USysUI = ConfigUpdateAction.UpdateSystemUi
    private val RC = ConfigUpdateAction.ReloadContent
    private val UCS = ConfigUpdateAction.UpdateChapterStyle
    private val ITP = ConfigUpdateAction.InvalidateTextPage
    private val UL = ConfigUpdateAction.UpdateLayout
    private val SRT = ConfigUpdateAction.SubmitRenderTask
    private val PA = ConfigUpdateAction.UpdatePageAnim
    private val RB = ConfigUpdateAction.RebuildWholeBookPageIndex
    private val DM = ConfigUpdateAction.UpdateWholeBookPageDemand
    private val RII = ConfigUpdateAction.RefreshInlineImages

    private data class Case(
        val legacy: String,
        val update: ConfigUpdate,
        val expected: Set<ConfigUpdateAction>,
    )

    private val cases = listOf(
        // ── 正文样式 ──
        Case("FontConfigDialog 缩进 8,5", ConfigUpdate.ParagraphIndent("　"), setOf(UCS, RC)),
        Case("FontConfigDialog 字重 8,9,6", ConfigUpdate.TextBold(600), setOf(UCS, ITP, UC)),
        Case("FontConfigDialog 字距 8,5", ConfigUpdate.LetterSpacing(0.1f), setOf(UCS, RC)),
        Case("FontConfigDialog 行距 8,5", ConfigUpdate.LineSpacing(3), setOf(UCS, RC)),
        Case("FontConfigDialog 段距 8,5", ConfigUpdate.ParagraphSpacing(2), setOf(UCS, RC)),
        Case("FontConfigDialog 斜体 8,5", ConfigUpdate.TextItalic(true), setOf(UCS, RC)),
        Case("ReadStyleDialog 字号 8,5", ConfigUpdate.TextSize(22), setOf(UCS, RC)),
        Case("TEXT_COLOR 2,6,9,11", ConfigUpdate.TextColor(0x112233), setOf(US, UC, ITP, SRT)),
        Case(
            "TEXT_ACCENT_COLOR 2,6,9,11",
            ConfigUpdate.TextAccentColor(0x112233),
            setOf(US, UC, ITP, SRT)
        ),

        // ── 阴影 ──
        Case("FontConfigDialog 阴影开关 8,5", ConfigUpdate.TextShadow(true), setOf(UCS, RC)),
        Case("ShadowSetDialog 阴影半径 8,5", ConfigUpdate.ShadowRadius(1f), setOf(UCS, RC)),
        Case("ShadowSetDialog 阴影 dx 8,5", ConfigUpdate.ShadowDx(1f), setOf(UCS, RC)),
        Case("ShadowSetDialog 阴影 dy 8,5", ConfigUpdate.ShadowDy(1f), setOf(UCS, RC)),
        Case("S_COLOR 2,6,9,11", ConfigUpdate.ShadowColor(0x112233), setOf(US, UC, ITP, SRT)),

        // ── 标题 ──
        Case("TipConfigDialog 标题字体 8,5", ConfigUpdate.TitleFont(""), setOf(UCS, RC)),
        Case("TipConfigDialog 标题字重 8,9,6", ConfigUpdate.TitleBold(600), setOf(UCS, ITP, UC)),
        Case("TipConfigDialog 标题模式 5(+RB)", ConfigUpdate.TitleMode(1), setOf(RB, RC)),
        Case("TipConfigDialog 分段类型 5(+RB)", ConfigUpdate.TitleSegType(1), setOf(RB, RC)),
        Case("TipConfigDialog 分段距离 5(+RB)", ConfigUpdate.TitleSegDistance(1), setOf(RB, RC)),
        Case("TipConfigDialog 分段标志 5(+RB)", ConfigUpdate.TitleSegFlag("　"), setOf(RB, RC)),
        Case("TipConfigDialog 标题缩放 8,5", ConfigUpdate.TitleSegScaling(1f), setOf(UCS, RC)),
        Case(
            "TipConfigDialog 标题行距(外) 8,5",
            ConfigUpdate.TitleLineSpacingExtra(1),
            setOf(UCS, RC)
        ),
        Case(
            "TipConfigDialog 标题行距(内) 8,5",
            ConfigUpdate.TitleLineSpacingSub(1),
            setOf(UCS, RC)
        ),
        Case("TipConfigDialog 标题字号 8,5", ConfigUpdate.TitleSize(24), setOf(UCS, RC)),
        Case("TipConfigDialog 标题上距 8,5", ConfigUpdate.TitleTopSpacing(1), setOf(UCS, RC)),
        Case("TipConfigDialog 标题下距 8,5", ConfigUpdate.TitleBottomSpacing(1), setOf(UCS, RC)),
        Case("TITLE_COLOR 8,5", ConfigUpdate.TitleColor(0x112233), setOf(UCS, RC)),
        Case("标题夜间色 同 TITLE_COLOR", ConfigUpdate.TitleColorNight(0x112233), setOf(UCS, RC)),

        // ── 页眉页脚 ──
        Case("InfoConfigDialog 页眉项 2,6(+DM)", ConfigUpdate.TipHeaderLeft(1), setOf(US, UC, DM)),
        Case("InfoConfigDialog 页脚项 2,6(+DM)", ConfigUpdate.TipFooterRight(1), setOf(US, UC, DM)),
        Case(
            "InfoConfigDialog 自定义页眉项 2,6(+DM)",
            ConfigUpdate.CustomTipHeaderLeft("{x}"),
            setOf(US, UC, DM)
        ),
        Case("InfoConfigDialog 页眉模式 2", ConfigUpdate.HeaderMode(1), setOf(US)),
        Case("InfoConfigDialog 页脚模式 2", ConfigUpdate.FooterMode(1), setOf(US)),
        Case("InfoConfigDialog 页眉字体 2", ConfigUpdate.HeaderFont(""), setOf(US)),
        Case("InfoConfigDialog 页眉字号 2", ConfigUpdate.HeaderFontSize(12), setOf(US)),
        Case("TIP_HEADER_COLOR 2", ConfigUpdate.TipHeaderColor(0x112233), setOf(US)),
        Case("TIP_FOOTER_COLOR 2", ConfigUpdate.TipFooterColor(0x112233), setOf(US)),
        Case("TIP_DIVIDER_COLOR 2", ConfigUpdate.TipDividerColor(0x112233), setOf(US)),

        // ── 下划线 ──
        Case(
            "UnderlineConfigDialog 下划线 6,9,11",
            ConfigUpdate.Underline(true),
            setOf(UC, ITP, SRT)
        ),
        Case(
            "UnderlineConfigDialog 点线 6,9,11",
            ConfigUpdate.DottedLine(true),
            setOf(UC, ITP, SRT)
        ),
        Case(
            "UnderlineConfigDialog 下划线延长 6,9,11",
            ConfigUpdate.UnderlineExtend(true),
            setOf(UC, ITP, SRT)
        ),
        Case(
            "UnderlineConfigDialog 下划线高度 8,9,6",
            ConfigUpdate.UnderlineHeight(2),
            setOf(UCS, ITP, UC)
        ),
        Case(
            "UnderlineConfigDialog 下划线间距 8,9,6",
            ConfigUpdate.UnderlinePadding(2),
            setOf(UCS, ITP, UC)
        ),
        Case(
            "BgTextConfigDialog 点线基准 6,8,10",
            ConfigUpdate.DottedBase(0.1f),
            setOf(UC, UCS, UL)
        ),
        Case(
            "BgTextConfigDialog 点线比例 6,9,11",
            ConfigUpdate.DottedRatio(0.1f),
            setOf(UC, ITP, SRT)
        ),
        Case("U_COLOR 2 + 6,9,11", ConfigUpdate.UnderlineColor(0x112233), setOf(US, UC, ITP, SRT)),

        // ── 内边距 ──
        Case("PaddingConfigDialog 上内边距 10,5", ConfigUpdate.PaddingTop(1), setOf(UL, RC)),
        Case("PaddingConfigDialog 下内边距 10,5", ConfigUpdate.PaddingBottom(1), setOf(UL, RC)),
        Case("PaddingConfigDialog 左内边距 10,5", ConfigUpdate.PaddingLeft(1), setOf(UL, RC)),
        Case("PaddingConfigDialog 右内边距 10,5", ConfigUpdate.PaddingRight(1), setOf(UL, RC)),
        Case("PaddingConfigDialog 页眉上内边距 2", ConfigUpdate.HeaderPaddingTop(1), setOf(US)),
        Case("PaddingConfigDialog 页脚右内边距 2", ConfigUpdate.FooterPaddingRight(1), setOf(US)),
        Case("PaddingConfigDialog 显示页眉分隔线 2", ConfigUpdate.ShowHeaderLine(true), setOf(US)),
        Case("PaddingConfigDialog 显示页脚分隔线 2", ConfigUpdate.ShowFooterLine(true), setOf(US)),

        // ── 背景与系统 UI ──
        Case("BgTextConfigDialog 背景色/图 1", ConfigUpdate.BgStr("#112233"), setOf(UB)),
        Case("BgTextConfigDialog 背景类型 1", ConfigUpdate.BgType(1), setOf(UB)),
        Case("BgTextConfigDialog 背景透明度 3", ConfigUpdate.BgAlpha(80), setOf(UBA)),
        Case(
            "BgTextConfigDialog 深色状态栏图标 0",
            ConfigUpdate.StatusIconDark(true),
            setOf(USysUI)
        ),
        Case(
            "MoreConfigDialog 隐藏状态栏 0,2",
            ConfigUpdate.HideStatusBar(true),
            setOf(USysUI, US)
        ),
        Case(
            "MoreConfigDialog 隐藏导航栏 0,2",
            ConfigUpdate.HideNavigationBar(true),
            setOf(USysUI, US)
        ),
        Case(
            "MoreConfigDialog 刘海屏内边距 2",
            ConfigUpdate.PaddingDisplayCutouts(true),
            setOf(US)
        ),

        // ── 排版布局 ──
        Case(
            "ReadStyleDialog 样式方案 1,2,5(+RII,RB,USysUI)",
            ConfigUpdate.StyleSelect(1),
            setOf(UB, US, RII, RB, RC, USysUI),
        ),
        Case(
            "ReadStyleDialog 分享排版 1,2,5(+RB)",
            ConfigUpdate.ShareLayout(true),
            setOf(UB, US, RB, RC)
        ),
        Case(
            "ReadStyleDialog 翻页动画 upPageAnim+5(+RB)",
            ConfigUpdate.PageAnim(2),
            setOf(PA, RB, RC)
        ),
        Case("MoreConfigDialog 两端对齐 5(+RB)", ConfigUpdate.TextFullJustify(true), setOf(RB, RC)),
        Case(
            "MoreConfigDialog 底部对齐 5(+RB)",
            ConfigUpdate.TextBottomJustify(true),
            setOf(RB, RC)
        ),
        Case("MoreConfigDialog 中文排版 5(+RB)", ConfigUpdate.UseZhLayout(true), setOf(RB, RC)),
        Case(
            "MoreConfigDialog 特殊样式适配 5(+RB)",
            ConfigUpdate.AdaptSpecialStyle(true),
            setOf(RB, RC)
        ),
        Case("MoreConfigDialog 全局下划线 5", ConfigUpdate.UseUnderlineGlobal(true), setOf(RC)),
        Case(
            "MoreConfigDialog 横屏双页 10,5",
            ConfigUpdate.DoubleHorizontalPage("1"),
            setOf(UL, RC)
        ),
        Case("MoreConfigDialog 优化渲染 8,5", ConfigUpdate.OptimizeRender(true), setOf(UCS, RC)),
        Case(
            "ReadStyleDialog 简繁转换 5(+RB)",
            ConfigUpdate.ChineseConverterType(1),
            setOf(RB, RC)
        ),
        Case(
            "MoreConfigDialog 正文延伸至刘海 recreate(+RB)",
            ConfigUpdate.ReadBodyToLh(true),
            setOf(RB, RC)
        ),
    )

    @Test
    fun `actions mirror the legacy View UP_CONFIG event codes`() {
        cases.forEach { case ->
            assertEquals(
                "${case.update.javaClass.simpleName}（旧 View ${case.legacy}）",
                case.expected,
                case.update.actions,
            )
        }
    }

    @Test
    fun `single color edits never re-download inline images`() {
        cases.asSequence()
            .filter { it.legacy.endsWith("COLOR 2,6,9,11") || it.legacy.startsWith("TITLE_COLOR") }
            .forEach { case ->
                assertFalse(
                    case.update.javaClass.simpleName,
                    RII in case.update.actions,
                )
            }
    }

    /**
     * 翻页动画速度是 Compose 时代新增项（旧 View 无对应事件码）：只缩放收尾动画时长，
     * 不改变动画种类、页高或排版，因此不得驱动任何正文副作用。
     */
    @Test
    fun `page turn speed never repaints the reading content`() {
        assertEquals(
            emptySet<ConfigUpdateAction>(),
            ConfigUpdate.PageAnimSpeed(2).actions,
        )
    }

    /**
     * 菜单颜色/边框是 Compose 时代新增项（旧 View 无对应事件码），只被阅读菜单读取。
     * 除 `MenuBgColor` 需要刷新状态栏图标外，不得驱动任何正文副作用——尤其不能带
     * `ReloadContent`（改个菜单颜色就重载正文）。
     */
    @Test
    fun `menu appearance updates never reload or repaint the reading content`() {
        listOf(
            ConfigUpdate.MenuBgColor(0x112233),
            ConfigUpdate.MenuBgColorNight(0x112233),
            ConfigUpdate.MenuAccentColor(0x112233),
            ConfigUpdate.MenuAccentColorNight(0x112233),
            ConfigUpdate.MenuContainerColor(0x112233),
            ConfigUpdate.MenuContainerColorNight(0x112233),
            ConfigUpdate.BorderWidth(1),
            ConfigUpdate.BorderColor(0x112233),
            ConfigUpdate.BorderColorNight(0x112233),
        ).forEach { update ->
            assertEquals(
                "${update.javaClass.simpleName} 最多只允许 UpdateSystemUi",
                emptySet<ConfigUpdateAction>(),
                update.actions - USysUI,
            )
        }
    }
}
