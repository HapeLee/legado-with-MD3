package io.legado.app.ui.config.themeConfig

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * COUI / Miuix 主题设置对齐契约。
 *
 * 用户定案：COUI 与 Miuix 同属「关闭自定义主题色时由引擎内置配色决定」的引擎，
 * 两者共用 `useBuiltInPaletteEngine` 门控 —— 都不显示项目自绘色板预览卡、都提供
 * 「自定义主题色」开关、关闭开关时都隐藏主题配色列表与自定义颜色入口。开启后首项
 * 恒为「动态取色」：COUI 不再把 `appTheme == "0"` 改名成「COUI 默认」，且必须真正
 * 走系统动态取色（内置预设只在关闭开关且选中首项时接管）。主题风格项与同页下拉项
 * 一样走 `DropdownListSettingItem`（包装器负责拼接分隔线与 Miuix 原生行），靠新增的
 * `commitAfterDismiss` 透传保住 Mochi 浮层收起后再切根主题的行为。
 *
 * 静态契约只锁结构与文案；四引擎真机外观、TalkBack 未复核。
 */
class ThemeConfigCouiThemeContractTest {

    @Test
    fun couiAndMiuixShareTheBuiltInPaletteGate() {
        val source = sourceOf(THEME_CONFIG)
        assertTrue(
            "COUI 未纳入内置配色引擎判断",
            source.contains("val useBuiltInPaletteEngine = isMiuixEngine || isCouiEngine"),
        )
        assertTrue(
            "预览卡未按引擎门控隐藏（COUI 顶部仍有示例组件）",
            source.contains("if (!useBuiltInPaletteEngine) {"),
        )
        assertTrue(
            "「自定义主题色」开关未对 COUI 开放",
            source.contains("if (useBuiltInPaletteEngine) {"),
        )
        assertTrue(
            "关闭自定义主题色时 COUI 仍显示配色列表",
            source.contains("if (!useBuiltInPaletteEngine || theme.useMiuixMonet) {"),
        )
        assertTrue(
            "自定义颜色入口未使用同一门控",
            source.contains("theme.appTheme == \"12\" && (!useBuiltInPaletteEngine || theme.useMiuixMonet)"),
        )
        assertFalse(
            "仍残留按 isMiuixEngine 单独门控的旧条件",
            source.contains("!isMiuixEngine || theme.useMiuixMonet"),
        )
    }

    @Test
    fun couiFirstItemKeepsDynamicColorLabel() {
        val source = sourceOf(THEME_CONFIG)
        assertFalse(
            "配色列表 / 色板按钮仍把首项替换成 COUI 默认",
            source.contains("R.string.coui_default_palette"),
        )
        assertFalse(
            "仍按引擎改写 value == \"0\" 的首项",
            source.contains("isCouiEngine && value == \"0\""),
        )
        assertTrue(
            "下拉列表未直接使用 themes 原始标签（首项为动态取色）",
            source.contains("displayEntries = visibleThemes.map { it.first }.toTypedArray()"),
        )
        val array = repoFileOf(THEMES_ARRAY_CN)
            .substringAfter("<string-array name=\"themes_item\">")
            .substringBefore("</string-array>")
        assertTrue("themes_item 首项应为动态取色", array.contains("<item>动态取色</item>"))
    }

    @Test
    fun couiBuiltInPaletteFollowsCustomColorSwitch() {
        val source = sourceOf(APP_THEME)
        assertTrue(
            "COUI 内置配色未改由「自定义主题色」开关决定",
            source.contains("!themeSettings.useMiuixMonet"),
        )
        assertTrue(
            "内置配色未限制在首项 Dynamic（无条件接管会破坏显式配色覆盖）",
            source.contains("appThemeMode == AppThemeMode.Dynamic"),
        )
        assertTrue(
            "未把内置配色开关传给 ThemeEngine",
            source.contains("useBuiltInPalette = useBuiltInPalette,"),
        )
        assertTrue(
            "colorScheme 缓存键未纳入开关，切换开关不会重算配色",
            source.contains("composeEngine, useBuiltInPalette,"),
        )
        assertTrue(
            "themeColors 缓存键未纳入开关",
            source.contains("appThemeMode, useBuiltInPalette"),
        )
        assertTrue(
            "开启开关后 useDynamicColor 未恢复",
            source.contains("useDynamicColor = appThemeMode == AppThemeMode.Dynamic && !useBuiltInPalette"),
        )
        assertTrue(
            "COUI 原生控件未跟随开关",
            source.contains("ProvideCouiTheme(themeColors.isDark, useBuiltInPalette)"),
        )
        assertFalse(
            "仍残留按 Dynamic 无条件接管配色的旧变量",
            source.contains("useCouiDefaultColors"),
        )
    }

    @Test
    fun themeEngineHonorsBuiltInPaletteFlag() {
        val source = sourceOf(THEME_ENGINE)
        assertTrue(
            "ThemeEngine 未提供内置配色开关",
            source.contains("useBuiltInPalette: Boolean = false"),
        )
        assertTrue(
            "COUI 预设分支未受开关控制",
            source.contains("composeEngine == ComposeEngine.Coui && useBuiltInPalette &&"),
        )
        assertTrue(
            "系统动态取色解析路径不应被删除",
            source.contains("resolveDynamicColorScheme(context = context, darkTheme = darkTheme)"),
        )
        assertTrue(
            "dynamic color scheme 实现不应被替换",
            source.contains("dynamicDarkColorScheme(context)"),
        )
    }

    @Test
    fun themeStyleRowUsesTheSameDropdownAsOtherSettings() {
        val row = sourceOf(THEME_CONFIG)
            .substringAfter("SearchableSetting(\"theme.compose_engine\")")
            .substringBefore("SearchableSetting(\"theme.hyper_os4_components\")")
        assertFalse(
            "主题风格项仍在用顶层 AppChoiceDropdown（与同页下拉项形态不一致）",
            row.contains("AppChoiceDropdown("),
        )
        assertFalse(
            "主题风格项仍是 interactive = false 的纯展示 trigger",
            row.contains("interactive = false"),
        )
        assertFalse(
            "主题风格项仍直接调底层 SettingItem（缺拼接分隔线与 Miuix 原生行）",
            row.contains("choiceOptions ="),
        )
        assertFalse(
            "主题风格项仍在手工构造 AppChoiceOption 列表",
            row.contains("AppChoiceOption("),
        )
        assertTrue(
            "未改用与导航动画相同的 DropdownListSettingItem",
            row.contains("DropdownListSettingItem("),
        )
        assertTrue(
            "丢失了切换根主题所需的延迟提交",
            row.contains("commitAfterDismiss = true"),
        )
        assertTrue(
            "丢失了 COUI 低于 Android 14 的不可用档位",
            row.contains("unavailableValues = if (couiSupported) emptySet() else setOf(coui)"),
        )
    }

    @Test
    fun blurIsMandatoryForCouiAndHyperOs4() {
        val themeConfig = sourceOf(THEME_CONFIG)
        assertTrue(
            "HyperOS 4 开关未按模糊能力门控",
            themeConfig.contains("enabled = ThemeResolver.isBlurSupported(),"),
        )
        assertTrue(
            "不支持模糊时开关仍显示为已开启",
            themeConfig.contains("checked = theme.useHyperOs4Components && ThemeResolver.isBlurSupported(),"),
        )
        assertTrue(
            "模糊开关未只在 Material / Mochi 下保留",
            themeConfig.contains(
                "if (composeEngine == ComposeEngine.Material || composeEngine == ComposeEngine.Mochi) {",
            ),
        )
        assertFalse(
            "COUI / Miuix 仍可通过模糊开关关闭材质",
            themeConfig.contains("ComposeEngine.Coui -> ThemeResolver.isCouiSupported()"),
        )

        val resolver = sourceOf(THEME_RESOLVER)
        assertTrue("未提供统一的模糊能力判定", resolver.contains("internal fun isBlurSupported()"))
        assertTrue(
            "COUI 未建立在模糊能力之上",
            resolver.contains("isBlurSupported() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE"),
        )

        val scaffold = sourceOf(APP_SCAFFOLD)
        assertFalse(
            "COUI 顶栏仍被 enableBlur 门控",
            scaffold.contains("nativeSamplingSupported && topBar != null && themeSettings.enableBlur"),
        )
        assertFalse(
            "Miuix 顶栏模糊仍被 enableBlur 门控",
            scaffold.contains("miuixEngine && nativeSamplingSupported && hasBars && themeSettings.enableBlur"),
        )
    }

    @Test
    fun couiFloatingToolbarOnlyRunsInTheActivityWindow() {
        val bar = sourceOf(SELECTION_BAR)
        assertTrue(
            "COUI 胶囊操作栏未限制在页面窗口",
            bar.contains(
                "val inActivityWindow = LocalView.current.rootView === LocalActivity.current?.window?.decorView",
            ),
        )
        assertTrue(
            "COUI 分支未使用窗口判断",
            bar.contains("if (LegadoTheme.composeEngine == ComposeEngine.Coui && inActivityWindow) {"),
        )
    }

    @Test
    fun hyperOs4TopBarIsAlwaysProgressive() {
        val topBar = sourceOf(MIUIX_TOP_BAR)
        assertFalse("OS4 顶栏仍受渐进模糊开关控制", topBar.contains("enableProgressiveBlur"))

        val effects = sourceOf(MIUIX_BAR_EFFECTS)
        assertTrue("OS4 顶栏未使用渐进模糊", effects.contains("ProgressiveBlur.Top.copy(curve = 2.2f)"))
        assertFalse("OS4 顶栏仍保留整块模糊分支", effects.contains("val progressive ="))
    }

    @Test
    fun dropdownListSettingItemForwardsCommitAfterDismiss() {
        val source = sourceOf(LIST_SETTING_ITEM)
        assertTrue(
            "DropdownListSettingItem 未提供 commitAfterDismiss 参数",
            source.contains("commitAfterDismiss: Boolean = false"),
        )
        assertTrue(
            "未透传给底层 SettingItem",
            source.contains("choiceCommitAfterDismiss = commitAfterDismiss"),
        )
        assertTrue(
            "包装器未绘制拼接分隔线（与同页其它设置项不一致）",
            source.contains("SplicedColumnDivider()"),
        )
    }

    @Test
    fun settingItemForwardsCommitAfterDismiss() {
        val source = sourceOf(SETTING_ITEM)
        assertTrue(
            "SettingItem 未提供 choiceCommitAfterDismiss 参数",
            source.contains("choiceCommitAfterDismiss: Boolean = false"),
        )
        assertTrue(
            "新参数未透传给受控 AppChoiceDropdown",
            source.contains("commitAfterDismiss = choiceCommitAfterDismiss"),
        )
    }

    @Test
    fun textAnimationPreviewRowIsRemoved() {
        val source = sourceOf(THEME_CONFIG)
        assertFalse(
            "文字动画示例行仍在（Miuix 下 BasicComponent + ListItem 双重内边距来源）",
            source.contains("R.string.text_animation_preview"),
        )
        assertFalse("示例切换文案仍在", source.contains("R.string.text_animation_example_"))
        assertFalse("预览行仍手动画拼接分隔线", source.contains("SplicedColumnDivider"))
        assertFalse("预览行仍直调 AnimatedTextLine", source.contains("AnimatedTextLine"))
        assertTrue(
            "文字动画等级下拉项不应被一起删掉",
            source.contains("SearchableSetting(\"theme.text_animation_level\")"),
        )
        listOf(STRINGS, STRINGS_ZH_CN, STRINGS_ZH_TW, STRINGS_ZH_HK).forEach { path ->
            assertFalse("$path 仍定义示例文案", repoFileOf(path).contains("text_animation_preview"))
        }
    }

    @Test
    fun monetSummaryNoLongerNamesMiuix() {
        listOf(STRINGS, STRINGS_ZH_CN, STRINGS_ZH_TW, STRINGS_ZH_HK).forEach { path ->
            val text = repoFileOf(path)
            val line = text.lines().first { "name=\"miuix_monet_summary\"" in it }
            assertFalse("$path 的开关说明仍点名 Miuix，COUI 下会显示错", line.contains("Miuix"))
        }
    }

    private fun sourceOf(path: String): String = repoFileOf("app/src/main/java/$path")

    private fun repoFileOf(path: String): String {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
        }
        error("从 ${File("").absolutePath} 向上找不到 $path")
    }

    private companion object {
        const val THEME_CONFIG = "io/legado/app/ui/config/themeConfig/ThemeConfigScreen.kt"
        const val SETTING_ITEM = "io/legado/app/ui/widget/components/settingItem/SettingItem.kt"
        const val LIST_SETTING_ITEM = "io/legado/app/ui/widget/components/settingItem/ListSettingItem.kt"
        const val APP_THEME = "io/legado/app/ui/theme/AppTheme.kt"
        const val THEME_ENGINE = "io/legado/app/ui/theme/ThemeEngine.kt"
        const val THEME_RESOLVER = "io/legado/app/ui/theme/ThemeResolver.kt"
        const val APP_SCAFFOLD = "io/legado/app/ui/widget/components/AppScaffold.kt"
        const val SELECTION_BAR = "io/legado/app/ui/widget/components/SelectionBottomBar.kt"
        const val MIUIX_TOP_BAR = "io/legado/app/ui/widget/components/topbar/MiuixNativeTopBar.kt"
        const val MIUIX_BAR_EFFECTS = "io/legado/app/ui/theme/MiuixBarEffects.kt"
        const val THEMES_ARRAY_CN = "app/src/main/res/values-zh-rCN/arrays.xml"
        const val STRINGS = "app/src/main/res/values/strings.xml"
        const val STRINGS_ZH_CN = "app/src/main/res/values-zh-rCN/strings.xml"
        const val STRINGS_ZH_TW = "app/src/main/res/values-zh-rTW/strings.xml"
        const val STRINGS_ZH_HK = "app/src/main/res/values-zh-rHK/strings.xml"
    }
}
