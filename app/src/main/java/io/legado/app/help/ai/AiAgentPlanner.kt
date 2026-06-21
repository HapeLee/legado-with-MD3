package io.legado.app.help.ai

import io.legado.app.ui.main.ai.AiChatMessage
import org.json.JSONArray
import org.json.JSONObject

data class AiAgentPlanStep(
    val id: String,
    val title: String,
    val status: String = STATUS_PENDING,
    val dependsOn: List<String> = emptyList(),
    val validator: String = ""
) {
    companion object {
        const val STATUS_PENDING = "pending"
    }
}

data class AiAgentPlan(
    val goal: String,
    val steps: List<AiAgentPlanStep>
) {
    fun toSystemPrompt(): String {
        if (goal.isBlank() || steps.isEmpty()) return ""
        return buildString {
            append("本轮 Agent 执行计划由客户端生成，用于约束执行顺序：\n")
            append("目标：").append(goal.take(1_000)).append('\n')
            steps.forEachIndexed { index, step ->
                append(index + 1)
                append(". ")
                append(step.title)
                if (step.dependsOn.isNotEmpty()) {
                    append("；依赖：")
                    append(step.dependsOn.joinToString(","))
                }
                if (step.validator.isNotBlank()) {
                    append("；校验：")
                    append(step.validator)
                }
                append('\n')
            }
            append("最终回复前必须确认：工具结果已满足目标，写入类工具返回 ok=true，多步骤任务没有遗漏；如果校验失败，先重试或说明失败原因。")
        }.trim()
    }

    fun toTraceJson(): JSONObject {
        val array = JSONArray()
        steps.forEach { step ->
            array.put(
                JSONObject()
                    .put("id", step.id)
                    .put("title", step.title)
                    .put("status", step.status)
                    .put("dependsOn", JSONArray(step.dependsOn))
                    .put("validator", step.validator)
            )
        }
        return JSONObject()
            .put("goal", goal)
            .put("steps", array)
    }
}

object AiAgentPlanner {

    fun create(
        messages: List<AiChatMessage>,
        tools: List<AiResolvedTool>
    ): AiAgentPlan {
        val goal = messages.lastOrNull { it.role == AiChatMessage.Role.USER }
            ?.content
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
        if (goal.isBlank()) return AiAgentPlan("", emptyList())
        val toolNames = tools.mapTo(hashSetOf()) { it.name }
        val steps = mutableListOf(
            AiAgentPlanStep(
                id = "understand_goal",
                title = "理解用户目标、约束和当前上下文",
                validator = "目标和约束必须在最终回复中被覆盖"
            )
        )
        if (needsLocalData(goal) && toolNames.isNotEmpty()) {
            steps += AiAgentPlanStep(
                id = "read_local_context",
                title = "调用本地工具读取必要数据，不凭空推断",
                dependsOn = listOf("understand_goal"),
                validator = "工具结果必须为 ok=true 或给出明确失败原因"
            )
        }
        if (needsWrite(goal) && toolNames.isNotEmpty()) {
            steps += AiAgentPlanStep(
                id = "apply_changes",
                title = "执行创建、修改、删除或批量写入操作",
                dependsOn = listOf(if (needsLocalData(goal)) "read_local_context" else "understand_goal"),
                validator = "写入类工具必须返回 ok=true，并且数量、id 或目标字段可核对"
            )
        } else if (toolNames.isNotEmpty()) {
            steps += AiAgentPlanStep(
                id = "execute_tools",
                title = "按需调用工具获取事实、生成内容或执行用户请求",
                dependsOn = listOf("understand_goal"),
                validator = "工具结果 JSON 必须完整，失败时先重试或说明限制"
            )
        }
        steps += AiAgentPlanStep(
            id = "validate_result",
            title = "校验工具结果是否满足目标，并检查多步骤任务是否遗漏",
            dependsOn = steps.lastOrNull()?.let { listOf(it.id) } ?: emptyList(),
            validator = "校验失败时不要直接给成功结论"
        )
        steps += AiAgentPlanStep(
            id = "final_response",
            title = "输出简洁结论、结果和必要的风险说明",
            dependsOn = listOf("validate_result"),
            validator = "最终回复必须基于已验证结果"
        )
        return AiAgentPlan(goal, steps)
    }

    private fun needsLocalData(goal: String): Boolean {
        val keywords = listOf(
            "书架", "书籍", "章节", "阅读记录", "书源", "角色", "配音", "工具",
            "设置", "查询", "查看", "读取", "搜索", "调试", "生成图片", "生图"
        )
        return keywords.any { goal.contains(it, ignoreCase = true) }
    }

    private fun needsWrite(goal: String): Boolean {
        val keywords = listOf(
            "创建", "新增", "修改", "更新", "删除", "移除", "设置", "分配", "导入",
            "保存", "清空", "批量", "生成头像", "生成图片", "重绘"
        )
        return keywords.any { goal.contains(it, ignoreCase = true) }
    }
}
