package io.legado.app.domain.model

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `AiMessagePartJson` 的编解码护栏。
 *
 * ⚠️ 这个文件的存在本身就是一条教训。`AiMessageParts.kt` 原住 `:app`（该模块 apply 了
 * `kotlin.plugins.serialization`）；`86c7428d24` 把它移进 `core:model` 时**没有**给目标
 * 模块 apply 插件 ⇒ 六个子类的 `$$serializer` 一个都没生成，`encode` / `decode` 从那天起
 * 在运行期抛 `SerializationException: Serializer for subclass 'X' is not found in the
 * polymorphic scope of 'AiMessagePart'`。
 *
 * 编译期完全无感（`serializer<T>()` 有反射回落，能编过），而 `decode` 又把异常
 * `runCatching` 吞成 `emptyList()` ⇒ **聊天记录会静默丢失全部内容**，不崩不报错。
 * 本文件是那条路径唯一的测试覆盖。
 *
 * 因此第一条用例（往返）就是缺失的护栏：**它红了先去看 `core:model` 的
 * `kotlin.serialization` 插件是不是掉了**，而不是去改这段 JSON 逻辑。
 */
class AiMessagePartJsonTest {

    @Test
    fun `往返编解码保留子类型与顺序`() {
        val parts: List<AiMessagePart> = listOf(
            AiMessagePart.Text("你好"),
            AiMessagePart.Reasoning("先想一下"),
            AiMessagePart.Tool(toolCallId = "t1", toolName = "search", input = "q=1"),
        )

        assertEquals(parts, AiMessagePartJson.decode(AiMessagePartJson.encode(parts)))
    }

    @Test
    fun `多态判别字段是 type`() {
        val encoded = AiMessagePartJson.encode(listOf(AiMessagePart.Text("x")))

        assertContains(encoded, "\"type\":\"text\"")
    }

    @Test
    fun `legacy ToolCall 解码后迁移成统一的 Tool`() {
        val legacy = """[{"type":"tool_call","id":"c1","name":"search","arguments":"q=1"}]"""

        val tool = AiMessagePartJson.decode(legacy).single() as AiMessagePart.Tool

        assertEquals("c1", tool.toolCallId)
        assertEquals("search", tool.toolName)
        assertEquals("q=1", tool.input)
        assertEquals("", tool.output)
    }

    @Test
    fun `legacy ToolResult 解码后迁移成统一的 Tool`() {
        val legacy = """[{"type":"tool_result","callId":"c2","name":"search","content":"42"}]"""

        val tool = AiMessagePartJson.decode(legacy).single() as AiMessagePart.Tool

        assertEquals("c2", tool.toolCallId)
        assertEquals("42", tool.output)
        assertEquals("", tool.input)
    }

    @Test
    fun `可空字段不写出且往返等价`() {
        val parts = listOf(AiMessagePart.BookResult(bookUrl = "u", name = "n", author = "a"))

        val encoded = AiMessagePartJson.encode(parts)

        // explicitNulls = false ⇒ 全 null 的可选字段不该出现在 JSON 里。
        assertFalse(encoded.contains("origin"), "explicitNulls=false 时应省略 null 字段：$encoded")
        assertEquals(parts, AiMessagePartJson.decode(encoded))
    }

    @Test
    fun `未知字段被忽略`() {
        val json = """[{"type":"text","text":"hi","futureField":123}]"""

        assertEquals(listOf(AiMessagePart.Text("hi")), AiMessagePartJson.decode(json))
    }

    @Test
    fun `空白输入解码为空列表`() {
        assertTrue(AiMessagePartJson.decode("").isEmpty())
        assertTrue(AiMessagePartJson.decode("   ").isEmpty())
    }

    /**
     * 钉住**当前**行为：`decode` 把解析失败吞成空列表（`runCatching`）。
     * 这是有争议的设计——坏数据静默变「空消息」而非报错——但它确实是现状，
     * 且 [AiChatViewModel] 依赖它不抛。改动它需要是一个自觉的决定。
     */
    @Test
    fun `残缺 JSON 解码为空列表而不抛异常`() {
        assertTrue(AiMessagePartJson.decode("{not json").isEmpty())
    }
}
