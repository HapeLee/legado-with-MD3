package io.legado.app.help.ai

import io.legado.app.help.config.AppConfig
import org.json.JSONObject

data class AiResolvedTool(
    val name: String,
    val definition: JSONObject,
    val execute: suspend (JSONObject?) -> String
)

object AiToolRegistry {

    private const val TOOL_SETTINGS_VERSION = 9
    private val version2AddedDefaultTools = setOf(
        "list_speech_catalogs",
        "assign_character_speech_route",
        "batch_assign_character_speech_routes",
        "clear_character_speech_routes"
    )
    private val version3AddedDefaultTools = setOf(
        "list_read_aloud_bgm_catalog",
        "assign_read_aloud_bgm_ranges"
    )
    private val version4AddedDefaultTools = setOf(
        "list_speech_voice_groups",
        "upsert_speech_voice_group",
        "delete_speech_voice_group"
    )
    private val version5AddedDefaultTools = setOf(
        "list_world_books",
        "upsert_world_book",
        "delete_world_book",
        "upsert_world_book_entry",
        "delete_world_book_entry"
    )
    private val version6AddedDefaultTools = setOf(
        "list_world_book_bindings",
        "upsert_world_book_binding",
        "delete_world_book_binding"
    )
    private val version7AddedDefaultTools = setOf(
        "import_world_book_json",
        "export_world_book_json"
    )
    private val version8AddedDefaultTools = setOf(
        "search_book_chapter_content"
    )
    private val version9AddedDefaultTools = setOf(
        "batch_manage_speech_voice_groups"
    )

    val characterCompanionToolNames = setOf(
        "query_bookshelf",
        "get_bookshelf_book_info",
        "list_book_chapters",
        "search_book_chapter_content",
        "read_book_chapter_content",
        "generate_image"
    )

    val readSafeToolNames = setOf(
        "query_bookshelf",
        "get_bookshelf_book_info",
        "list_book_chapters",
        "search_book_chapter_content",
        "read_book_chapter_content",
        "query_read_records",
        "generate_image",
        "list_book_characters",
        "upsert_book_character",
        "list_book_character_relations",
        "upsert_book_character_relation",
        "list_ai_gallery_images",
        "set_book_character_avatar_from_gallery",
        "generate_book_character_avatar",
        "list_speech_catalogs",
        "assign_character_speech_route",
        "batch_assign_character_speech_routes",
        "list_speech_voice_groups",
        "list_read_aloud_bgm_catalog",
        "assign_read_aloud_bgm_ranges",
        "list_world_books",
        "list_world_book_bindings",
        "get_app_settings"
    )

    data class ToolMeta(
        val name: String,
        val label: String,
        val group: String
    )

    val defaultEnabledTools = setOf(
        "query_bookshelf",
        "get_bookshelf_book_info",
        "manage_bookshelf_group",
        "manage_bookshelf_tag",
        "set_bookshelf_book_group",
        "set_bookshelf_book_tags",
        "list_book_chapters",
        "search_book_chapter_content",
        "read_book_chapter_content",
        "query_read_records",
        "list_book_sources",
        "search_book_source",
        "search_web_tavily",
        "create_book_source",
        "get_book_source",
        "update_book_source",
        "fetch_source_html",
        "debug_book_source",
        "reading_ajax",
        "reading_webview",
        "capture_web_requests",
        "generate_image",
        "list_book_characters",
        "upsert_book_character",
        "delete_book_character",
        "list_book_character_relations",
        "upsert_book_character_relation",
        "delete_book_character_relation",
        "list_ai_gallery_images",
        "set_book_character_avatar_from_gallery",
        "generate_book_character_avatar",
        "list_speech_catalogs",
        "assign_character_speech_route",
        "batch_assign_character_speech_routes",
        "clear_character_speech_routes",
        "list_speech_voice_groups",
        "upsert_speech_voice_group",
        "delete_speech_voice_group",
        "batch_manage_speech_voice_groups",
        "list_read_aloud_bgm_catalog",
        "assign_read_aloud_bgm_ranges",
        "list_world_books",
        "upsert_world_book",
        "delete_world_book",
        "upsert_world_book_entry",
        "delete_world_book_entry",
        "list_world_book_bindings",
        "upsert_world_book_binding",
        "delete_world_book_binding",
        "import_world_book_json",
        "export_world_book_json",
        "get_app_settings",
        "set_app_setting",
        "set_app_settings_batch"
    )

    private val nativeToolLabels = mapOf(
        "query_bookshelf" to "查询书架书籍",
        "get_bookshelf_book_info" to "读取书籍详情",
        "manage_bookshelf_group" to "管理书架分组",
        "manage_bookshelf_tag" to "管理书架标签",
        "set_bookshelf_book_group" to "设置书籍分组",
        "set_bookshelf_book_tags" to "设置书籍标签",
        "query_read_records" to "查询阅读记录",
        "list_book_chapters" to "读取章节目录",
        "search_book_chapter_content" to "搜索章节正文",
        "read_book_chapter_content" to "读取章节正文",
        "list_book_sources" to "列出书源",
        "search_book_source" to "搜索书源内容",
        "create_book_source" to "创建书源",
        "get_book_source" to "读取书源详情",
        "update_book_source" to "更新书源",
        "fetch_source_html" to "抓取网页源码",
        "debug_book_source" to "调试书源规则",
        "reading_ajax" to "阅读网络请求",
        "reading_webview" to "阅读 WebView",
        "capture_web_requests" to "抓包网络请求",
        "search_web_tavily" to "联网搜索",
        "generate_image" to "生成图片",
        "list_book_characters" to "读取角色资料",
        "upsert_book_character" to "新增或更新角色",
        "delete_book_character" to "删除角色",
        "list_book_character_relations" to "读取角色关系网",
        "upsert_book_character_relation" to "新增或更新角色关系",
        "delete_book_character_relation" to "删除角色关系",
        "list_ai_gallery_images" to "读取 AI 图片库",
        "set_book_character_avatar_from_gallery" to "设置角色图库头像",
        "generate_book_character_avatar" to "生成角色头像",
        "list_speech_catalogs" to "读取配音目录",
        "assign_character_speech_route" to "设置角色配音",
        "batch_assign_character_speech_routes" to "批量分配角色配音",
        "clear_character_speech_routes" to "清空角色配音",
        "list_speech_voice_groups" to "读取发言人分组",
        "upsert_speech_voice_group" to "新增或更新发言人分组",
        "delete_speech_voice_group" to "删除发言人分组",
        "list_read_aloud_bgm_catalog" to "读取朗读配乐曲库",
        "assign_read_aloud_bgm_ranges" to "分配朗读配乐范围",
        "list_world_books" to "读取世界书",
        "upsert_world_book" to "新增或更新世界书",
        "delete_world_book" to "删除世界书",
        "upsert_world_book_entry" to "新增或更新世界书条目",
        "delete_world_book_entry" to "删除世界书条目",
        "list_world_book_bindings" to "读取世界书启用",
        "upsert_world_book_binding" to "启用世界书",
        "delete_world_book_binding" to "移除世界书启用",
        "import_world_book_json" to "导入世界书 JSON",
        "export_world_book_json" to "导出世界书 JSON",
        "get_app_settings" to "读取应用设置",
        "set_app_setting" to "修改应用设置",
        "set_app_settings_batch" to "批量修改设置"
    )

    private val nativeToolGroups = mapOf(
        "query_bookshelf" to "书架",
        "get_bookshelf_book_info" to "书架",
        "manage_bookshelf_group" to "书架",
        "manage_bookshelf_tag" to "书架",
        "set_bookshelf_book_group" to "书架",
        "set_bookshelf_book_tags" to "书架",
        "query_read_records" to "书架",
        "list_book_chapters" to "阅读",
        "search_book_chapter_content" to "阅读",
        "read_book_chapter_content" to "阅读",
        "list_book_sources" to "书源",
        "search_book_source" to "书源",
        "create_book_source" to "书源",
        "get_book_source" to "书源",
        "update_book_source" to "书源",
        "fetch_source_html" to "书源",
        "debug_book_source" to "书源",
        "reading_ajax" to "阅读网络",
        "reading_webview" to "阅读网络",
        "capture_web_requests" to "阅读网络",
        "search_web_tavily" to "联网搜索",
        "generate_image" to "AI 生图",
        "list_book_characters" to "角色资料",
        "upsert_book_character" to "角色资料",
        "delete_book_character" to "角色资料",
        "list_book_character_relations" to "角色资料",
        "upsert_book_character_relation" to "角色资料",
        "delete_book_character_relation" to "角色资料",
        "list_ai_gallery_images" to "AI 图片库",
        "set_book_character_avatar_from_gallery" to "角色资料",
        "generate_book_character_avatar" to "角色资料",
        "list_speech_catalogs" to "角色配音",
        "assign_character_speech_route" to "角色配音",
        "batch_assign_character_speech_routes" to "角色配音",
        "clear_character_speech_routes" to "角色配音",
        "list_speech_voice_groups" to "角色配音",
        "upsert_speech_voice_group" to "角色配音",
        "delete_speech_voice_group" to "角色配音",
        "list_read_aloud_bgm_catalog" to "智能配乐",
        "assign_read_aloud_bgm_ranges" to "智能配乐",
        "list_world_books" to "世界书",
        "upsert_world_book" to "世界书",
        "delete_world_book" to "世界书",
        "upsert_world_book_entry" to "世界书",
        "delete_world_book_entry" to "世界书",
        "list_world_book_bindings" to "世界书",
        "upsert_world_book_binding" to "世界书",
        "delete_world_book_binding" to "世界书",
        "import_world_book_json" to "世界书",
        "export_world_book_json" to "世界书",
        "get_app_settings" to "设置",
        "set_app_setting" to "设置",
        "set_app_settings_batch" to "设置"
    )

    fun groupLabelOfTool(name: String): String {
        return when {
            name.startsWith("mcp_") -> "MCP 工具"
            else -> nativeToolGroups[name] ?: "其他"
        }
    }

    fun displayNameOfTool(name: String): String {
        return when {
            name.startsWith("mcp_") -> name.removePrefix("mcp_").ifBlank { name }
            else -> nativeToolLabels[name] ?: name
        }
    }

    fun metaOfTool(name: String): ToolMeta {
        return ToolMeta(
            name = name,
            label = displayNameOfTool(name),
            group = groupLabelOfTool(name)
        )
    }

    private fun nativeResolvedTools(): List<AiResolvedTool> {
        val tools = AiBookshelfTool.resolvedTools().toMutableList()
        tools += AiLibraryTool.resolvedTools()
        tools += AiTavilyTool.resolvedTools()
        tools += AiBookSourceTool.resolvedTools()
        tools += AiReadingNetworkTool.resolvedTools()
        tools += AiSettingsTool.resolvedTools()
        tools += AiImageTool.resolvedTools()
        tools += AiBookCharacterTool.resolvedTools()
        tools += AiReadAloudBgmTool.resolvedTools()
        tools += AiWorldBookTool.resolvedTools()
        return tools.distinctBy { it.name }
    }

    suspend fun resolveAllToolNamesForManage(): List<String> {
        val dynamic = mutableSetOf<String>()
        dynamic += nativeResolvedTools().map { it.name }
        dynamic += AiMcpClient.resolveTools(AppConfig.aiEnabledMcpServers).map { it.name }
        dynamic += AppConfig.aiEnabledToolNames
        dynamic += defaultEnabledTools
        return dynamic.toList().sorted()
    }

    suspend fun resolveAvailableTools(includeMcp: Boolean = false): List<AiResolvedTool> {
        val tools = nativeResolvedTools().toMutableList()
        if (includeMcp) {
            tools += AiMcpClient.resolveTools(AppConfig.aiEnabledMcpServers)
        }
        val enabled = effectiveEnabledToolNames()
        return tools
            .distinctBy { it.name }
            .filter { it.name in enabled }
    }

    fun effectiveEnabledToolNames(): Set<String> {
        val stored = AppConfig.aiEnabledToolNames
        if (AppConfig.aiEnabledToolNamesVersion < TOOL_SETTINGS_VERSION) {
            val additions = buildSet {
                if (AppConfig.aiEnabledToolNamesVersion < 2) addAll(version2AddedDefaultTools)
                if (AppConfig.aiEnabledToolNamesVersion < 3) addAll(version3AddedDefaultTools)
                if (AppConfig.aiEnabledToolNamesVersion < 4) addAll(version4AddedDefaultTools)
                if (AppConfig.aiEnabledToolNamesVersion < 5) addAll(version5AddedDefaultTools)
                if (AppConfig.aiEnabledToolNamesVersion < 6) addAll(version6AddedDefaultTools)
                if (AppConfig.aiEnabledToolNamesVersion < 7) addAll(version7AddedDefaultTools)
                if (AppConfig.aiEnabledToolNamesVersion < 8) addAll(version8AddedDefaultTools)
                if (AppConfig.aiEnabledToolNamesVersion < 9) addAll(version9AddedDefaultTools)
            }
            val migrated = (stored.ifEmpty { defaultEnabledTools } + additions)
                .filter { it.isNotBlank() }
                .toSet()
            AppConfig.aiEnabledToolNames = migrated
            AppConfig.aiEnabledToolNamesVersion = TOOL_SETTINGS_VERSION
            return migrated
        }
        return stored.ifEmpty { defaultEnabledTools }
    }

    suspend fun resolveReadTools(includeMcp: Boolean = false): List<AiResolvedTool> {
        return when (AppConfig.aiReadToolMode) {
            AppConfig.AI_READ_TOOL_MODE_ALL -> resolveAllTools(includeMcp)
            AppConfig.AI_READ_TOOL_MODE_SAFE -> {
                val tools = nativeResolvedTools().toMutableList()
                if (includeMcp) {
                    tools += AiMcpClient.resolveTools(AppConfig.aiEnabledMcpServers)
                }
                tools.distinctBy { it.name }.filter { it.name in readSafeToolNames }
            }
            else -> resolveAvailableTools(includeMcp)
        }
    }

    fun resolveNativeTools(names: Set<String>): List<AiResolvedTool> {
        return nativeResolvedTools()
            .filter { it.name in names }
    }

    suspend fun resolveMcpTools(serverIds: Set<String>): List<AiResolvedTool> {
        if (serverIds.isEmpty()) return emptyList()
        val servers = AppConfig.aiMcpServerList.filter { server ->
            server.enabled && server.id in serverIds
        }
        if (servers.isEmpty()) return emptyList()
        return AiMcpClient.resolveTools(servers)
    }

    suspend fun resolveAllTools(includeMcp: Boolean = false): List<AiResolvedTool> {
        val tools = nativeResolvedTools().toMutableList()
        if (includeMcp) {
            tools += AiMcpClient.resolveTools(AppConfig.aiEnabledMcpServers)
        }
        return tools.distinctBy { it.name }
    }
}
