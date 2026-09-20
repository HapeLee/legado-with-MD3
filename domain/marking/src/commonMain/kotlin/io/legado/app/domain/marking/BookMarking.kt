package io.legado.app.domain.marking

import io.legado.app.core.platform.systemTimeMillis

/**
 * 用户划线/高亮笔记（M4-6 从 `io.legado.app.data.entities.BookMarking` 下沉）。
 *
 * 与书签、AI 正文处理完全独立：划线不建书签、删书签不碰划线。与书签一样认「书名+作者」
 * 跨源关联（换源后笔记仍可见可管理），[bookUrl] 只作源指纹（跳转校验用）。
 *
 * ⚠️ **逐字照抄 Room 实体**，连默认值一起：
 *  - 12 个字段全部 `val`（实体也是全 `val`），顺序与实体声明逐行对齐，便于日后比对；
 *  - [createdAt] / [updatedAt] 默认是 `systemTimeMillis()`（`:core:platform` 的 `expect fun`），
 *    与实体完全一致 —— 改掉它就会静默改变「新建标记」的时间戳生成规则；
 *  - **没有** `equals` / `hashCode` 覆写 ⇒ 判等是 data class 的**全字段**比较（与 M3-5 的
 *    `RuleSub` / M4-1 的 `AiPromptPreset` 同侧，与 M3-6 `TagGroupRule` 的 id-only 判等相反）。
 *    所以 `BookMarkingMapperTest` 里整对象 `assertEquals` 是成立的，但仍需一条反向用例
 *    （仅 `note` 不同的两条不相等）钉住它，免得后来者给模型补一个 id-only 判等。
 *  - **没有** `companion object`：实体也没有（对比 M4-3 的 `AiArtifact`，它的 `STATUS_*`
 *    常量被 DAO 的 SQL 插值引用，必须复刻；本域没有这种常量）。
 *
 * ⚠️ [anchorJson] / [styleJson] 是**不透明 JSON 字符串**，不是解析后的对象：锚点用
 * `TextProcessAnchor`、样式用 `TextProcessStyle`，编解码发生在 `:app` 的调用侧
 * （`SaveMarkingUseCase` / `ContentProcessor`），共享层不认识 Gson。
 *
 * ⚠️ 本域**不进备份/恢复**（`Backup.kt` / `Restore.kt` 里没有 `book_marks`），所以不存在
 * 「按实体反序列化」的兼容面，领域模型可以自由承载全部字段。
 */
data class BookMarking(
    val id: String,
    /** 创建时的源（源指纹）：跳转校验用。渲染仍按当前源查（见 [BookMarkingGateway.getForChapter]）。 */
    val bookUrl: String,
    /** 跨源关联键：与 bookmarks 一致，换源后笔记仍可见可管理。 */
    val bookName: String = "",
    val bookAuthor: String = "",
    val chapterIndex: Int? = null,
    val anchorJson: String,
    val styleJson: String? = null,
    val note: String = "",
    /** 章节标题，目录 Sheet 笔记页展示用（book_marks 无 toc 外键，故冗余存储）。 */
    val chapterName: String = "",
    val enabled: Boolean = true,
    val createdAt: Long = systemTimeMillis(),
    val updatedAt: Long = systemTimeMillis(),
)
