package io.legado.app.help.ai

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal data class AiToolValidationResult(
    val ok: Boolean,
    val category: String,
    val message: String,
    val retryable: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("ok", ok)
            .put("category", category)
            .put("message", message)
            .put("retryable", retryable)
    }
}

internal object AiAgentValidator {

    private val writeToolPrefixes = listOf(
        "set_",
        "create_",
        "update_",
        "delete_",
        "manage_",
        "upsert_",
        "assign_",
        "batch_",
        "clear_"
    )

    fun validateToolResult(
        toolCall: AiAgentToolCall,
        result: String
    ): AiToolValidationResult {
        val trimmed = result.trim()
        if (trimmed.isBlank()) {
            return AiToolValidationResult(false, "empty_result", "工具返回为空", retryable = true)
        }
        val json = parseJson(trimmed) ?: return if (isWriteTool(toolCall.name)) {
            AiToolValidationResult(false, "invalid_json", "写入类工具必须返回可校验 JSON", retryable = false)
        } else {
            AiToolValidationResult(true, "text_result", "工具返回非 JSON 文本")
        }
        val explicitOk = when {
            json.has("ok") -> json.optBoolean("ok", false)
            json.has("success") -> json.optBoolean("success", false)
            else -> null
        }
        if (explicitOk == false) {
            return AiToolValidationResult(
                ok = false,
                category = "tool_reported_failure",
                message = json.optString("error")
                    .ifBlank { json.optString("message").ifBlank { "工具返回失败" } },
                retryable = isRetryableFailure(json)
            )
        }
        if (isWriteTool(toolCall.name)) {
            val writeValidation = validateWriteTool(toolCall.name, json)
            if (!writeValidation.ok) return writeValidation
        }
        return AiToolValidationResult(true, "ok", "工具结果通过校验")
    }

    fun wrapFailedResult(
        rawResult: String,
        validation: AiToolValidationResult,
        attempt: Int,
        maxAttempts: Int
    ): String {
        return JSONObject()
            .put("ok", false)
            .put("error", validation.message)
            .put("validation", validation.toJson())
            .put("attempt", attempt)
            .put("maxAttempts", maxAttempts)
            .put("rawResult", rawResult.take(8_000))
            .toString()
    }

    private fun validateWriteTool(name: String, json: JSONObject): AiToolValidationResult {
        if (name == "set_app_settings_batch") {
            val total = json.optInt("totalCount", -1)
            val success = json.optInt("successCount", -1)
            if (total >= 0 && success != total) {
                return AiToolValidationResult(
                    ok = false,
                    category = "partial_write",
                    message = "批量设置只成功 $success/$total",
                    retryable = false
                )
            }
        }
        if (name.startsWith("batch_")) {
            val total = json.optInt("total", json.optInt("totalCount", -1))
            val success = json.optInt("success", json.optInt("successCount", -1))
            if (total > 0 && success >= 0 && success < total) {
                return AiToolValidationResult(false, "partial_write", "批量工具只成功 $success/$total")
            }
        }
        val hasEvidence = json.has("item") ||
                json.has("result") ||
                json.has("results") ||
                json.has("id") ||
                json.has("count") ||
                json.has("successCount") ||
                json.has("worldBookId")
        if (!hasEvidence) {
            return AiToolValidationResult(
                ok = false,
                category = "weak_write_evidence",
                message = "写入类工具缺少可核对结果字段",
                retryable = false
            )
        }
        return AiToolValidationResult(true, "ok", "写入结果通过校验")
    }

    private fun parseJson(value: String): JSONObject? {
        return try {
            when {
                value.startsWith("{") -> JSONObject(value)
                value.startsWith("[") -> JSONObject().put("items", JSONArray(value))
                else -> null
            }
        } catch (_: JSONException) {
            null
        }
    }

    private fun isWriteTool(name: String): Boolean {
        return writeToolPrefixes.any { name.startsWith(it) } ||
                name.contains("_assign_") ||
                name.contains("_route")
    }

    private fun isRetryableFailure(json: JSONObject): Boolean {
        val error = json.optString("error").lowercase()
        return "timeout" in error ||
                "timed out" in error ||
                "connection" in error ||
                "network" in error ||
                "reset" in error ||
                "abort" in error ||
                "429" in error ||
                "rate" in error
    }
}
