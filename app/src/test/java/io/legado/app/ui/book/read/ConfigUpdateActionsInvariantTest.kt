package io.legado.app.ui.book.read

import io.legado.app.ui.book.read.ConfigUpdateActionsInvariantTest.Companion.NO_RENDER_EFFECT
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

/**
 * Track E · E0 —— `ConfigUpdate` 渲染副作用的完备性不变式。
 *
 * `ConfigUpdate` 的每个成员都手写一组 [ConfigUpdateAction]，描述改这项设置后要驱动哪些渲染
 * 副作用。手写表的失败模式是「漏填/填错 ⇒ 改了不生效」——已经出过两例（`StatusIconDark`
 * 缺 `UpdateSystemUi`、`UnderlineColor` 缺 `UpdateContent+InvalidateTextPage+SubmitRenderTask`）。
 * 本测试把「漏填」从代码评审兜底降级为测试期兜底。
 *
 * [NO_RENDER_EFFECT] 是**临时**白名单：这些成员写 DataStore、由 `readPreferences` StateFlow
 * 反应式生效，不需要命令式渲染副作用。Track E · E1 会把它们迁到独立的 `ReadPreferenceUpdate`
 * 族——那之后它们在类型上就没有 `actions` 概念，本白名单随之删除。
 *
 * 双向棘轮：新增成员漏填 actions 会红；白名单条目补上了 actions 却忘了从白名单移除，也会红。
 */
class ConfigUpdateActionsInvariantTest {

    @Test
    fun `每个 ConfigUpdate 成员都声明了渲染副作用，或在无副作用白名单里`() {
        val offenders = emptyActionMembers()
            .filterNot { it in NO_RENDER_EFFECT }
            .sorted()

        assertTrue(
            "以下 ConfigUpdate 成员的 actions 为空集，改了不会驱动任何渲染副作用：\n" +
                offenders.joinToString("\n") { "  - ConfigUpdate.$it" } +
                "\n\n若它确实只写 DataStore、靠 readPreferences 反应式生效，请加入本测试的 " +
                "NO_RENDER_EFFECT；否则请补上正确的 ConfigUpdateAction 集。",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `无副作用白名单里没有失效条目`() {
        val current = emptyActionMembers().toSet()
        val stale = (NO_RENDER_EFFECT - current).sorted()

        assertTrue(
            "以下条目已不再是空 actions（或成员已删除），请从 NO_RENDER_EFFECT 移除：\n" +
                stale.joinToString("\n") { "  - $it" },
            stale.isEmpty(),
        )
    }

    @Test
    fun `反射确实枚举到了 ConfigUpdate 的成员`() {
        // 防止 sealedSubclasses 因运行环境退化成空集，让上面两条断言假绿。
        val count = ConfigUpdate::class.sealedSubclasses.size
        assertTrue("ConfigUpdate 只枚举到 $count 个成员，反射可能失效", count > 100)
    }

    private fun emptyActionMembers(): List<String> =
        ConfigUpdate::class.sealedSubclasses.mapNotNull { type ->
            type.simpleName?.takeIf { instantiate(type).actions.isEmpty() }
        }

    private fun instantiate(type: KClass<out ConfigUpdate>): ConfigUpdate {
        type.objectInstance?.let { return it }
        val constructor = type.primaryConstructor
            ?: error("ConfigUpdate.${type.simpleName} 既不是 object 也没有主构造函数")
        val args: List<Any> = constructor.parameters.map { parameter ->
            when (parameter.type.classifier) {
                Int::class -> 0
                Long::class -> 0L
                Float::class -> 0f
                Double::class -> 0.0
                Boolean::class -> false
                String::class -> ""
                else -> error(
                    "ConfigUpdate.${type.simpleName} 的参数 ${parameter.name} 是未支持的类型 " +
                        "${parameter.type}，请在本测试补充对应的占位值"
                )
            }
        }
        return constructor.call(*args.toTypedArray())
    }

    private companion object {
        /** 只写 DataStore、由 `readPreferences` StateFlow 反应式生效的成员。E1 后删除。 */
        val NO_RENDER_EFFECT = setOf(
            // 阅读菜单外观
            "MenuTextColor",
            "MenuTextColorNight",
            "MenuIconShowText",
            "MenuIconStyle",
            "MenuIconItemsPerRow",
            "MenuIconRowCount",
            "MenuFloatingIconLiquidGlass",
            "MenuBottomCornerRadius",
            "MenuBlurRadius",
            "MenuBlurAlpha",
            "MenuBlurColor",
            "MenuBlurColorNight",
            "MenuPaletteStyle",
            "MenuLensRadius",
            "MenuCustomIcon",
            "MenuTopBarBlurMode",
            "MenuTopBarBlurSelection",
            "MenuTopBarLiquidGlassButtons",
            "MenuTopBarMergeButtons",
            "MenuTopBarTitleCapsule",
            "MenuBottomBarBlurMode",
            "MenuBottomBarBlurStyle",
            "MenuBottomBarLiquidGlassButtons",
            "FloatingBottomBar",
            "ShowMenuIcon",
            // 标题栏
            "TitleBarIconStyle",
            "TitleBarIconPosition",
            "TitleBarCustomIcon",
            "TitleBarCompact",
            "TitleBarMode",
            "ShowTitleBarIcons",
            // 亮度 / 进度条
            "ShowBrightnessView",
            "BrightnessVwPos",
            "BrightnessAuto",
            "ReadSliderMode",
            "ProgressBarBehavior",
            // 手势 / 按键
            "MouseWheelPage",
            "VolumeKeyPage",
            "VolumeKeyPageOnPlay",
            "KeyPageOnLongPress",
            "SwipeToAddBookmark",
            "BookmarkBadgeSize",
            "SliderVibrator",
            "SelectVibrator",
            "ClickImgWay",
            "DisableReturnKey",
            "NoAnimScrollPage",
            // 文本选择菜单
            "SelectText",
            "ExpandTextMenu",
            "ShowSelectMenuIcon",
            // 其它纯业务/纯菜单项
            "StyleName",
            "DefaultSourceChangeAll",
            "AutoChangeSource",
            "AutoSuggestDayNight",
            "ShowReadTitleAddition",
            "AutoReadSpeed",
            // 只决定点击目录时开新 Sheet 还是旧 Activity，取用时读设置，无渲染副作用
            "UseNewTocSheet",
            // 只影响下次目录解析时的无规则章节切分长度，取用时读设置，无渲染副作用
            "MaxLengthWithNoToc",
        )
    }
}
