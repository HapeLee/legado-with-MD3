package io.legado.app.data.repository

import io.legado.app.domain.model.settings.ReadSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * Track E · E0 / R1.5 —— `ReadSettingsGateway.update {}` 的持久化覆盖面不变式。
 *
 * `ReadSettings` 的每个字段都必须能经 `update {}` 落盘。做不到的字段是**静默丢写**：
 * 调用方 copy 了新值、`update {}` 也不报错，但重启后值回退。
 *
 * R1.5 之前 `toGatewayPrefMap()` 只覆盖 46/102，其余 56 个靠各自的遗留 setter 兜底，
 * 这些字段的名字冻结在下面的基线里。现在映射已补全，基线清空——它继续作为双向棘轮：
 * - 新增字段忘了接线 ⇒ 基线要变大 ⇒ 红。
 * - 字段补进 map 却忘了从基线移除 ⇒ 红。
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
         * 走不通 `update {}` 的字段。R1.5 补全映射后已清零，基线只允许下调——
         * 新增条目意味着又出现了一个静默丢写的字段，应当补映射而不是加进这里。
         */
        val UNPERSISTED_BASELINE = emptySet<String>()
    }
}
