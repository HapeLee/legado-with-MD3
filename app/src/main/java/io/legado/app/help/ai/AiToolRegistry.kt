package io.legado.app.help.ai

import org.json.JSONObject

object AiToolRegistry {

    data class ToolMeta(
        val name: String,
        val label: String,
        val group: String
    )

    val defaultEnabledTools = setOf(
        "query_bookshelf",
        "get_bookshelf_book_info",
        "list_book_chapters",
        "read_book_chapter_content",
        "query_read_records",
        "list_book_sources",
        "search_book_source",
        "get_book_source",
        "fetch_source_html",
        "search_web_tavily",
        "generate_image",
        "list_book_characters",
        "upsert_book_character",
        "delete_book_character",
        "get_app_settings",
        "set_app_setting"
    )

    private val nativeToolLabels = mapOf(
        "query_bookshelf" to "查询书架书籍",
        "get_bookshelf_book_info" to "读取书籍详情",
        "list_book_chapters" to "读取章节目录",
        "read_book_chapter_content" to "读取章节正文",
        "query_read_records" to "查询阅读记录",
        "list_book_sources" to "列出书源",
        "search_book_source" to "搜索书源内容",
        "get_book_source" to "读取书源详情",
        "fetch_source_html" to "抓取网页源码",
        "search_web_tavily" to "联网搜索",
        "generate_image" to "生成图片",
        "list_book_characters" to "读取角色资料",
        "upsert_book_character" to "新增或更新角色",
        "delete_book_character" to "删除角色",
        "get_app_settings" to "读取应用设置",
        "set_app_setting" to "修改应用设置"
    )

    private val nativeToolGroups = mapOf(
        "query_bookshelf" to "书架",
        "get_bookshelf_book_info" to "书架",
        "list_book_chapters" to "阅读",
        "read_book_chapter_content" to "阅读",
        "query_read_records" to "书架",
        "list_book_sources" to "书源",
        "search_book_source" to "书源",
        "get_book_source" to "书源",
        "fetch_source_html" to "书源",
        "search_web_tavily" to "联网搜索",
        "generate_image" to "AI 生图",
        "list_book_characters" to "角色资料",
        "upsert_book_character" to "角色资料",
        "delete_book_character" to "角色资料",
        "get_app_settings" to "设置",
        "set_app_setting" to "设置"
    )

    fun groupLabelOfTool(name: String): String {
        return nativeToolGroups[name] ?: "其他"
    }

    fun displayNameOfTool(name: String): String {
        return nativeToolLabels[name] ?: name
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
        tools += AiBookSourceTool.resolvedTools()
        tools += AiImageTool.resolvedTools()
        tools += AiSettingsTool.resolvedTools()
        return tools.distinctBy { it.name }
    }

    suspend fun resolveAllToolNamesForManage(): List<String> {
        return defaultEnabledTools.toList().sorted()
    }

    suspend fun resolveAvailableTools(): List<AiResolvedTool> {
        val tools = nativeResolvedTools()
        return tools.distinctBy { it.name }
            .filter { it.name in defaultEnabledTools }
    }

    suspend fun resolveAllTools(): List<AiResolvedTool> {
        return nativeResolvedTools().distinctBy { it.name }
    }
}
