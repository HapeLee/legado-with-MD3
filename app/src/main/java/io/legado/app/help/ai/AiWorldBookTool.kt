package io.legado.app.help.ai

import org.json.JSONArray
import org.json.JSONObject

object AiWorldBookTool {

    private const val TOOL_LIST_WORLD_BOOKS = "list_world_books"
    private const val TOOL_UPSERT_WORLD_BOOK = "upsert_world_book"
    private const val TOOL_DELETE_WORLD_BOOK = "delete_world_book"
    private const val TOOL_UPSERT_WORLD_BOOK_ENTRY = "upsert_world_book_entry"
    private const val TOOL_DELETE_WORLD_BOOK_ENTRY = "delete_world_book_entry"
    private const val TOOL_LIST_WORLD_BOOK_BINDINGS = "list_world_book_bindings"
    private const val TOOL_UPSERT_WORLD_BOOK_BINDING = "upsert_world_book_binding"
    private const val TOOL_DELETE_WORLD_BOOK_BINDING = "delete_world_book_binding"
    private const val TOOL_IMPORT_WORLD_BOOK_JSON = "import_world_book_json"
    private const val TOOL_EXPORT_WORLD_BOOK_JSON = "export_world_book_json"

    fun resolvedTools(): List<AiResolvedTool> {
        return listOf(
            AiResolvedTool(
                name = TOOL_LIST_WORLD_BOOKS,
                definition = listWorldBooksDefinition(),
                execute = { args -> AiWorldBookManager.listWorldBooks(args) }
            ),
            AiResolvedTool(
                name = TOOL_UPSERT_WORLD_BOOK,
                definition = upsertWorldBookDefinition(),
                execute = { args -> AiWorldBookManager.upsertWorldBook(args) }
            ),
            AiResolvedTool(
                name = TOOL_DELETE_WORLD_BOOK,
                definition = deleteWorldBookDefinition(),
                execute = { args -> AiWorldBookManager.deleteWorldBook(args) }
            ),
            AiResolvedTool(
                name = TOOL_UPSERT_WORLD_BOOK_ENTRY,
                definition = upsertWorldBookEntryDefinition(),
                execute = { args -> AiWorldBookManager.upsertWorldBookEntry(args) }
            ),
            AiResolvedTool(
                name = TOOL_DELETE_WORLD_BOOK_ENTRY,
                definition = deleteWorldBookEntryDefinition(),
                execute = { args -> AiWorldBookManager.deleteWorldBookEntry(args) }
            ),
            AiResolvedTool(
                name = TOOL_LIST_WORLD_BOOK_BINDINGS,
                definition = listWorldBookBindingsDefinition(),
                execute = { args -> AiWorldBookManager.listWorldBookBindings(args) }
            ),
            AiResolvedTool(
                name = TOOL_UPSERT_WORLD_BOOK_BINDING,
                definition = upsertWorldBookBindingDefinition(),
                execute = { args -> AiWorldBookManager.upsertWorldBookBinding(args) }
            ),
            AiResolvedTool(
                name = TOOL_DELETE_WORLD_BOOK_BINDING,
                definition = deleteWorldBookBindingDefinition(),
                execute = { args -> AiWorldBookManager.deleteWorldBookBinding(args) }
            ),
            AiResolvedTool(
                name = TOOL_IMPORT_WORLD_BOOK_JSON,
                definition = importWorldBookJsonDefinition(),
                execute = { args -> AiWorldBookManager.importWorldBookJson(args) }
            ),
            AiResolvedTool(
                name = TOOL_EXPORT_WORLD_BOOK_JSON,
                definition = exportWorldBookJsonDefinition(),
                execute = { args -> AiWorldBookManager.exportWorldBookJson(args) }
            )
        )
    }

    private fun listWorldBooksDefinition() = functionDefinition(
        name = TOOL_LIST_WORLD_BOOKS,
        description = "列出 AI 世界书配置。世界书用于长期设定、角色背景、写作规则、酒馆设定和固定知识注入。",
        properties = JSONObject()
            .put("includeEntries", JSONObject().put("type", "boolean").put("description", "是否返回条目内容"))
            .put("includeBindings", JSONObject().put("type", "boolean").put("description", "是否返回启用绑定，默认 true"))
    )

    private fun upsertWorldBookDefinition() = functionDefinition(
        name = TOOL_UPSERT_WORLD_BOOK,
        description = "新增或更新世界书。世界书默认只是资料库，不一定启用；需要启用到场景时调用 upsert_world_book_binding。",
        properties = JSONObject()
            .put("id", JSONObject().put("type", "string"))
            .put("name", JSONObject().put("type", "string"))
            .put("description", JSONObject().put("type", "string"))
            .put("version", JSONObject().put("type", "integer"))
            .put("type", JSONObject().put("type", "string").put("enum", JSONArray(listOf("lorebook"))))
            .put("scope", JSONObject().put("type", "string").put("enum", JSONArray(listOf("global", "book", "session"))))
            .put("bookKey", JSONObject().put("type", "string"))
            .put("enabled", JSONObject().put("type", "boolean"))
            .put("maxEntries", JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 40))
            .put("bindings", JSONObject().apply {
                put("type", "array")
                put("items", bindingSchema())
            })
            .put("order", JSONObject().put("type", "integer")),
        required = listOf("name")
    )

    private fun deleteWorldBookDefinition() = functionDefinition(
        name = TOOL_DELETE_WORLD_BOOK,
        description = "删除世界书。",
        properties = JSONObject()
            .put("id", JSONObject().put("type", "string")),
        required = listOf("id")
    )

    private fun upsertWorldBookEntryDefinition() = functionDefinition(
        name = TOOL_UPSERT_WORLD_BOOK_ENTRY,
        description = "新增或更新标准 lorebook 世界书条目。keywords 命中时按 position/role/injectDepth 注入，constantActive=true 时常驻注入。",
        properties = JSONObject()
            .put("worldBookId", JSONObject().put("type", "string"))
            .put("entry", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject()
                    .put("id", JSONObject().put("type", "string"))
                    .put("name", JSONObject().put("type", "string"))
                    .put("title", JSONObject().put("type", "string"))
                    .put("content", JSONObject().put("type", "string"))
                    .put("keywords", stringArraySchema())
                    .put("keys", stringArraySchema())
                    .put("secondaryKeys", stringArraySchema())
                    .put("excludeKeys", stringArraySchema())
                    .put("useRegex", JSONObject().put("type", "boolean"))
                    .put("regexEnabled", JSONObject().put("type", "boolean").put("description", "keys/secondaryKeys/excludeKeys 是否按正则匹配"))
                    .put("caseSensitive", JSONObject().put("type", "boolean"))
                    .put("enabled", JSONObject().put("type", "boolean"))
                    .put("constantActive", JSONObject().put("type", "boolean"))
                    .put("constant", JSONObject().put("type", "boolean"))
                    .put("priority", JSONObject().put("type", "integer").put("minimum", -9999).put("maximum", 9999))
                    .put("position", JSONObject().put("type", "string").put("enum", JSONArray(listOf("after_system_prompt", "before_prompt", "inject_depth", "before_last_user"))))
                    .put("injectDepth", JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 64))
                    .put("role", JSONObject().put("type", "string").put("enum", JSONArray(listOf("system", "user", "assistant"))))
                    .put("scanDepth", JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 64))
                    .put("maxMatches", JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 20))
                    .put("order", JSONObject().put("type", "integer"))
                )
                put("required", JSONArray(listOf("title", "content")))
                put("additionalProperties", false)
            }),
        required = listOf("worldBookId", "entry")
    )

    private fun deleteWorldBookEntryDefinition() = functionDefinition(
        name = TOOL_DELETE_WORLD_BOOK_ENTRY,
        description = "删除世界书条目。",
        properties = JSONObject()
            .put("worldBookId", JSONObject().put("type", "string"))
            .put("entryId", JSONObject().put("type", "string")),
        required = listOf("worldBookId", "entryId")
    )

    private fun listWorldBookBindingsDefinition() = functionDefinition(
        name = TOOL_LIST_WORLD_BOOK_BINDINGS,
        description = "列出世界书启用绑定。targetType 支持 global/companion，companion 需要 targetKey=助手或角色 ID。",
        properties = JSONObject()
            .put("worldBookId", JSONObject().put("type", "string"))
            .put("targetType", targetTypeSchema())
            .put("targetKey", JSONObject().put("type", "string"))
    )

    private fun upsertWorldBookBindingDefinition() = functionDefinition(
        name = TOOL_UPSERT_WORLD_BOOK_BINDING,
        description = "启用或更新世界书到指定场景。global=全局，companion=指定助手或角色，需要 targetKey=助手或角色 ID。",
        properties = JSONObject()
            .put("worldBookId", JSONObject().put("type", "string"))
            .put("bindingId", JSONObject().put("type", "string"))
            .put("targetType", targetTypeSchema())
            .put("targetKey", JSONObject().put("type", "string"))
            .put("enabled", JSONObject().put("type", "boolean"))
            .put("order", JSONObject().put("type", "integer")),
        required = listOf("worldBookId", "targetType")
    )

    private fun deleteWorldBookBindingDefinition() = functionDefinition(
        name = TOOL_DELETE_WORLD_BOOK_BINDING,
        description = "从指定场景移除世界书启用绑定。优先传 bindingId，也可传 targetType+targetKey。",
        properties = JSONObject()
            .put("worldBookId", JSONObject().put("type", "string"))
            .put("bindingId", JSONObject().put("type", "string"))
            .put("targetType", targetTypeSchema())
            .put("targetKey", JSONObject().put("type", "string")),
        required = listOf("worldBookId")
    )

    private fun importWorldBookJsonDefinition() = functionDefinition(
        name = TOOL_IMPORT_WORLD_BOOK_JSON,
        description = "导入标准 RikkaHub lorebook JSON，格式为 {version,type:'lorebook',data:{id,name,description,enabled,entries}}。",
        properties = JSONObject()
            .put("json", JSONObject().put("type", "string"))
            .put("copyOnConflict", JSONObject().put("type", "boolean").put("description", "id 冲突时是否生成副本，默认 true")),
        required = listOf("json")
    )

    private fun exportWorldBookJsonDefinition() = functionDefinition(
        name = TOOL_EXPORT_WORLD_BOOK_JSON,
        description = "导出指定世界书为标准 RikkaHub lorebook JSON。",
        properties = JSONObject()
            .put("id", JSONObject().put("type", "string")),
        required = listOf("id")
    )

    private fun functionDefinition(
        name: String,
        description: String,
        properties: JSONObject,
        required: List<String> = emptyList()
    ) = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", properties)
                if (required.isNotEmpty()) put("required", JSONArray(required))
                put("additionalProperties", false)
            })
        })
    }

    private fun stringArraySchema(): JSONObject {
        return JSONObject()
            .put("type", "array")
            .put("items", JSONObject().put("type", "string"))
    }

    private fun targetTypeSchema(): JSONObject {
        return JSONObject()
            .put("type", "string")
            .put("enum", JSONArray(listOf("global", "companion")))
    }

    private fun bindingSchema(): JSONObject {
        return JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject()
                .put("id", JSONObject().put("type", "string"))
                .put("targetType", targetTypeSchema())
                .put("targetKey", JSONObject().put("type", "string"))
                .put("enabled", JSONObject().put("type", "boolean"))
                .put("order", JSONObject().put("type", "integer"))
            )
            put("required", JSONArray(listOf("targetType")))
            put("additionalProperties", false)
        }
    }
}
