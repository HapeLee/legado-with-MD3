package io.legado.app.data.repository

import io.legado.app.domain.model.settings.ReadSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * Track E · E0 —— `ReadSettingsGateway.update {}` 的持久化覆盖面不变式。
 *
 * `ReadSettings` 是 101 字段的**读模型超集**，而 `update {}` 只落盘
 * `toGatewayPrefMap()` 声明的那 45 个键——这是 `ReadSettingsGateway.update` 的 KDoc
 * 明确记录的设计：其余字段须走各自的遗留 setter 写入。
 *
 * 所以本测试**不要求** 101 == 45（那会推翻既定设计），而是把「哪些字段走不通 `update {}`」
 * 冻结成基线，形成双向棘轮：
 * - 新增字段忘了接线 ⇒ 基线变大 ⇒ 红。作者必须显式选择「进 map」还是「加进基线并走 setter」。
 * - 字段补进了 map 却忘了从基线移除 ⇒ 红。
 *
 * 判定方式是行为性的：改一个字段的值，看 `toGatewayPrefMap()` 的输出是否随之变化。
 */
class ReadSettingsGatewayCoverageTest {

    @Test
    fun `update 写不进去的 ReadSettings 字段集合与基线一致`() {
        val actual = fieldsNotPersistedByUpdate()

        val newlyBroken = (actual - UNPERSISTED_BASELINE).sorted()
        assertTrue(
            "以下 ReadSettings 字段无法通过 ReadSettingsGateway.update {} 落盘——" +
                "在 update {} 里 copy 它们会被静默丢弃：\n" +
                newlyBroken.joinToString("\n") { "  - $it" } +
                "\n\n请把它加进 ReadSettingsRepository.toGatewayPrefMap()；" +
                "若确实只走遗留 setter 写入，则加进本测试的 UNPERSISTED_BASELINE。",
            newlyBroken.isEmpty(),
        )

        val fixed = (UNPERSISTED_BASELINE - actual).sorted()
        assertTrue(
            "以下字段已经能通过 update {} 落盘，请从 UNPERSISTED_BASELINE 移除（基线只能下调）：\n" +
                fixed.joinToString("\n") { "  - $it" },
            fixed.isEmpty(),
        )
    }

    @Test
    fun `反射确实枚举到了 ReadSettings 的字段`() {
        val count = ReadSettings::class.primaryConstructor?.parameters?.size ?: 0
        assertTrue("ReadSettings 只枚举到 $count 个字段，反射可能失效", count > 90)
    }

    @Test
    fun `toGatewayPrefMap 的键没有重复`() {
        val constructor = requireNotNull(ReadSettings::class.primaryConstructor)
        assertEquals(
            "toGatewayPrefMap 出现重复的 PreferKey，会让某个字段被另一个覆盖",
            ReadSettings().toGatewayPrefMap().size,
            ReadSettings().toGatewayPrefMap().keys.size,
        )
        // 顺带确保 map 不是空的（避免下面的行为判定整体假阳）
        assertTrue(constructor.parameters.isNotEmpty())
    }

    private fun fieldsNotPersistedByUpdate(): Set<String> {
        val constructor = requireNotNull(ReadSettings::class.primaryConstructor)
        val properties = ReadSettings::class.memberProperties.associateBy { it.name }
        val defaults = ReadSettings()
        val defaultMap = defaults.toGatewayPrefMap()

        return constructor.parameters.mapNotNullTo(mutableSetOf()) { parameter ->
            val name = parameter.name ?: return@mapNotNullTo null
            val property = properties[name] ?: return@mapNotNullTo null
            val mutated = mutate(name, property.get(defaults))
            val instance = constructor.callBy(mapOf(parameter to mutated))
            name.takeIf { instance.toGatewayPrefMap() == defaultMap }
        }
    }

    private fun mutate(name: String, value: Any?): Any = when (value) {
        is Boolean -> !value
        is Int -> value + 1
        is Long -> value + 1L
        is Float -> value + 1f
        is Double -> value + 1.0
        is String -> value + "_probe"
        else -> error("ReadSettings.$name 是未支持的类型 ${value?.let { it::class }}，请在本测试补充变异规则")
    }

    private companion object {
        /**
         * 只能通过遗留 setter 写入、走不通 `update {}` 的字段。基线只允许下调。
         * 主体是阅读菜单/标题栏外观一族（它们在 `ConfigUpdate` 侧也是同一个「无渲染副作用」
         * 集群，见 `ConfigUpdateActionsInvariantTest`）。
         */
        val UNPERSISTED_BASELINE = setOf(
            "menuAlpha",
            "expandTextMenu",
            "showSelectMenuIcon",
            "autoReadSpeed",
            "tocUiUseReplace",
            "tocCountWords",
            "readStyleSelect",
            "comicStyleSelect",
            "shareLayout",
            "readBarStyleFollowPage",
            "readBarStyle",
            "clickActionTL",
            "clickActionTC",
            "clickActionTR",
            "clickActionML",
            "clickActionMC",
            "clickActionMR",
            "clickActionBL",
            "clickActionBC",
            "clickActionBR",
            "readMenuBgColor",
            "readMenuAccentColor",
            "readMenuContainerColor",
            "readMenuBgColorNight",
            "readMenuAccentColorNight",
            "readMenuContainerColorNight",
            "readMenuTextColor",
            "readMenuTextColorNight",
            "readMenuColorMode",
            "readMenuIconShowText",
            "readMenuIconStyle",
            "titleBarIconStyle",
            "readMenuIconItemsPerRow",
            "readMenuIconRowCount",
            "readMenuBottomCornerRadius",
            "readMenuFloatingBottomBar",
            "readMenuTopBarBlurMode",
            "readMenuBottomBarBlurMode",
            "readMenuTopBarLiquidGlassButtons",
            "readMenuTopBarTitleCapsule",
            "readMenuBottomBarLiquidGlassButtons",
            "readMenuTopBarBlurStyle",
            "readMenuBottomBarBlurStyle",
            "readMenuBlurRadius",
            "readMenuBlurColor",
            "readMenuBlurColorNight",
            "readMenuPaletteStyle",
            "readMenuLensRadius",
            "readMenuBorderWidth",
            "readMenuBorderColor",
            "readMenuBorderColorNight",
            "readMenuCustomIcons",
            "titleBarCustomIcons",
            "titleBarIconPosition",
            "showTitleBarIcons",
            "chineseConverterType",
        )
    }
}
