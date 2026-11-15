package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户安装的 AI Skill（对应 Agent Skills 的 SKILL.md）。
 *
 * 渐进式加载：系统提示词里只放 name/description 作索引，模型命中后通过
 * `load_skill` 工具取 [content] 全文，避免长文本常驻上下文。
 * 与 provider / model / preset 一样放在 data 层，由 AiProfileDao 持久化。
 */
@Entity(tableName = "ai_skill_profiles")
data class AiSkillProfile(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String = "",
    val content: String = "",
    val sourceUrl: String = "",
    val enabled: Boolean = true,
    val sortNumber: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
