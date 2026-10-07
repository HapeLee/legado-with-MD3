package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index

/**
 * 多角色分配：一句话（章节内第 N 个开引号引导的对话）与角色的绑定。
 *
 * 权威存储。构建章节内容时注入 `<<角色名（声音池）>>` 标记（见 CastMarkers），
 * 阅读器渲染成胶囊、朗读链路直接读到标记文本。分配音色本身仍走
 * [BookVoiceBindingEntity]（subjectType=character），这里只记「哪句话归谁」。
 *
 * 锚点是章内开引号序号（对内容处理后的文本计数）：重新从源站拉取正文后，
 * 只要段落引号结构不变分配就仍然有效；构建时越界的序号自动忽略。
 */
@Entity(
    tableName = "chapter_role_assignments",
    primaryKeys = ["bookUrl", "chapterIndex", "quoteOrdinal"],
    indices = [
        Index(value = ["bookUrl", "chapterIndex"]),
        Index(value = ["characterId"]),
    ],
)
data class ChapterRoleAssignment(
    val bookUrl: String,
    val chapterIndex: Int,
    /** 章内开引号序号，从 0 开始（“ ‘ 「 『 " ' 计数，注入发生在 ContentProcessor 之后）。 */
    val quoteOrdinal: Int,
    val characterId: String,
    /** 冗余显示名：角色被删除时标记与胶囊仍能显示。 */
    val characterName: String = "",
    /** 冗余声音池标签（女青年等），同上。 */
    val voicePoolLabel: String = "",
    /**
     * **只作用于这一句**的变声器预设名，空 = 不变声。
     *
     * 与 [CastCharacter.voiceEffect] 分开存：那一列是角色级的全局值（只有「人物与角色配音」
     * 页会写），正文胶囊确认的是用户对着这一句话点的，作用域就得停在这一句。
     * 朗读时按「段级 → 角色全局」的顺序取，见
     * [io.legado.app.help.readaloud.effect.VoiceEffectStore]。
     */
    @androidx.room.ColumnInfo(defaultValue = "''")
    val voiceEffect: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
