package io.legado.app.data.entities

import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TxtTocRule] 下沉 :core:data 后，`@SerializedName(alternate = ["rule"])`
 * 由 app 侧 [txtTocRuleJsonDeserializer] 承接。本测试锁定该兼容的语义，
 * 防止「老用户备份恢复后目录规则静默变空」。
 */
class TxtTocRuleDeserializerTest {

    @Test
    fun newFormat_readsChapterRule() {
        val rule = GSON.fromJsonObject<TxtTocRule>(
            """{"id":1,"name":"新格式","chapterRule":"^第.+章"}"""
        ).getOrThrow()

        assertEquals("^第.+章", rule.chapterRule)
    }

    @Test
    fun legacyBackup_ruleKeyIsPromotedToChapterRule() {
        val rule = GSON.fromJsonObject<TxtTocRule>(
            """{"id":1,"name":"旧备份","rule":"^第.+章"}"""
        ).getOrThrow()

        assertEquals("^第.+章", rule.chapterRule)
    }

    @Test
    fun chapterRuleWinsWhenBothKeysPresent() {
        val rule = GSON.fromJsonObject<TxtTocRule>(
            """{"id":1,"name":"两者都有","chapterRule":"新规则","rule":"旧规则"}"""
        ).getOrThrow()

        assertEquals("新规则", rule.chapterRule)
    }

    @Test
    fun nullRuleKeyDoesNotOverride() {
        val rule = GSON.fromJsonObject<TxtTocRule>(
            """{"id":1,"name":"空rule","rule":null}"""
        ).getOrThrow()

        assertEquals("", rule.chapterRule)
    }

    @Test
    fun serializationAlwaysWritesChapterRule() {
        val json = GSON.toJson(TxtTocRule(id = 1, name = "写出", chapterRule = "^序章"))

        assertTrue(json.contains("\"chapterRule\""))
        assertTrue(!json.contains("\"rule\""))
    }

    @Test
    fun legacyArrayRoundTrips() {
        val list = GSON.fromJsonArray<TxtTocRule>(
            """[{"id":1,"name":"a","rule":"^第一章"},{"id":2,"name":"b","chapterRule":"^卷"}]"""
        ).getOrThrow()

        assertEquals(2, list.size)
        assertEquals("^第一章", list[0].chapterRule)
        assertEquals("^卷", list[1].chapterRule)
    }
}
