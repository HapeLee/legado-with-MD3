package io.legado.app.domain.model

/**
 * AI 工具分组。用户可以在 AI 配置页按组开关工具：
 * 关闭的分组不会进入模型请求的 tools 列表，用来降低上下文占用、减少误调用。
 *
 * key 会写进偏好并落盘，改动 key 等于让老用户的开关状态失效，不要随手重命名。
 */
enum class AiToolGroup(val key: String) {
    Reading("reading"),
    Knowledge("knowledge"),
    Writeback("writeback"),
    Memory("memory"),
    Skill("skill"),
    BookSource("source");

    companion object {
        fun fromKey(key: String): AiToolGroup? = values().firstOrNull { it.key == key }

        /** 解析逗号分隔的 key 串；无法识别的 key 直接丢弃，不做兼容处理。 */
        fun parse(raw: String?): Set<AiToolGroup> {
            if (raw.isNullOrBlank()) return emptySet()
            return raw.split(',')
                .mapNotNull { fromKey(it.trim()) }
                .toSet()
        }
    }
}
