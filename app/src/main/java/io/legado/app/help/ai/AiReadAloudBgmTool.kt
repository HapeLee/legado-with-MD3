package io.legado.app.help.ai

import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import org.json.JSONObject

object AiReadAloudBgmTool {

    private const val TOOL_LIST_BGM_CATALOG = "list_read_aloud_bgm_catalog"

    fun resolvedTools(): List<AiResolvedTool> {
        return listOf(
            AiResolvedTool(TOOL_LIST_BGM_CATALOG, listCatalogDefinition()) {
                listCatalog()
            },
            AiResolvedTool(
                AiReadAloudBgmService.TOOL_ASSIGN_BGM_RANGES,
                AiReadAloudBgmService.assignmentToolDefinition()
            ) { args ->
                assignRanges(args)
            }
        )
    }

    private fun listCatalogDefinition(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", TOOL_LIST_BGM_CATALOG)
                put("description", "读取朗读智能音频资源库，返回可用配乐和音效的 trackId、分组、标签、类型和时长，不返回本地文件路径。")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                    put("additionalProperties", false)
                })
            })
        }
    }

    private suspend fun listCatalog(): String = withContext(IO) {
        AiReadAloudBgmService.catalogJson().toString()
    }

    private suspend fun assignRanges(args: JSONObject?): String = withContext(IO) {
        AiReadAloudBgmService.toolAssign(args)
    }
}
