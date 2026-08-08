package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 用户划线/高亮笔记（标记）。与书签、AI 正文处理完全独立：
 * - 书签只做页面导航，不产生视觉标记；
 * - AI 改写净化仍走 [BookContentProcess]，本表只承载用户划线/高亮。
 *
 * [anchorJson] 复用 [io.legado.app.domain.model.TextProcessAnchor] 做章节内定位，
 * [styleJson] 复用 [io.legado.app.domain.model.TextProcessStyle] 存线段/颜色，
 * [note] 是用户备注。渲染时由 `ContentProcessor` 把启用行转成合成
 * [BookContentProcess] 混进现有渲染管线，TextChapterLayout 无需感知本表。
 *
 * 没有 kind 列：样式（[TextProcessStyle]）即类型，单实线/波浪线/虚线/背景色/
 * 字体色 全部由 styleJson 表达，渲染桥按需推导合成 [BookContentProcess] 的 kind。
 */
@Entity(
    tableName = "book_marks",
    indices = [
        Index(value = ["bookUrl", "chapterIndex"]),
    ]
)
data class BookMarking(
    @PrimaryKey
    val id: String,
    /** 创建时的源（源指纹）：跳转校验用。book_marks 与书签一样认「书名+作者」跨源关联。 */
    val bookUrl: String,
    /** 跨源关联键：与 bookmarks 一致，换源后笔记仍可见可管理。 */
    @ColumnInfo(defaultValue = "")
    val bookName: String = "",
    @ColumnInfo(defaultValue = "")
    val bookAuthor: String = "",
    val chapterIndex: Int? = null,
    val anchorJson: String,
    val styleJson: String? = null,
    val note: String = "",
    /** 章节标题，目录 Sheet 笔记页展示用（book_marks 无 toc 外键，故冗余存储）。 */
    val chapterName: String = "",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
