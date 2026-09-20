package io.legado.app.domain.contentprocess

import io.legado.app.core.platform.systemTimeMillis

/**
 * 正文处理项（M4-8 从 `io.legado.app.data.entities.BookContentProcess` 下沉）。
 *
 * 一张表 `book_content_processes` 承担两类来源：**AI 产物**（净化 / 改写，带
 * `aiArtifactId`）与**用户划线/高亮的渲染合成项**（由 `ContentProcessor.toRenderProcess()`
 * 从 `BookMarking` 转换而来，id 加 `mark:` 前缀避免与真实记录冲突）。
 *
 * ⚠️ **逐字照抄 Room 实体**，连默认值一起：18 个字段全部 `val`，顺序与实体声明逐行对齐；
 * `createdAt` / `updatedAt` 默认 `systemTimeMillis()`。**没有** `equals` / `hashCode` 覆写
 * ⇒ 判等是 data class 的**全字段**比较（与 M3-5 / M4-1 / M4-6 / M4-7 同侧）。
 *
 * ⚠️⚠️ **本类有一份"看不见的第二副本"：下面的 15 个常量。** 实体那一侧的同名常量被
 * `BookContentProcessDao` 的 `@Query` **字符串插值**（`status != ${'$'}{BookContentProcess.STATUS_DELETED}`
 * 直接变成 SQL 字面量），而 `:app` 的 `when` 分支与引擎判断用的是本模型这一侧。两处定义
 * 并存且**取值漂移时不会有任何编译错误** —— 表现是「已删除的记录仍被查出来」或
 * 「某个 kind 不显示中文名」。故：
 *  1. 这里必须复刻**全部** 15 个常量（M4-3 `AiArtifact` 的配方）；
 *  2. `BookContentProcessMapperTest` 有一条用例把每个常量**同时**与实体的同名常量和字面量比对。
 *
 * ⚠️ `anchorJson` / `actionJson` / `styleJson` 是不透明 JSON 串（对应 `TextProcessAnchor` /
 * `TextProcessAction` / `TextProcessStyle`，住 `:core:model`），映射层不解析、不重排。
 */
data class BookContentProcess(
    val id: String,
    val bookUrl: String,
    val chapterIndex: Int? = null,
    val kind: String,
    val stage: String = STAGE_CONTENT,
    val target: String = TARGET_SELECTION,
    val anchorJson: String,
    val actionJson: String,
    val styleJson: String? = null,
    val source: String = SOURCE_USER,
    val aiArtifactId: String? = null,
    val sourceContentHash: String? = null,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val status: Int = STATUS_ACTIVE,
    val schemaVersion: Int = 1,
    val createdAt: Long = systemTimeMillis(),
    val updatedAt: Long = systemTimeMillis(),
) {
    companion object {
        const val KIND_AI_CLEAN = "ai_clean"
        const val KIND_AI_REWRITE = "ai_rewrite"

        // 用户划线/高亮标记：book_marks 无 kind 列（样式即类型），渲染桥从 styleJson
        // 推导出这两个合成 kind 之一，供引擎/渲染层识别「这是标记、别改文本」。
        const val KIND_USER_UNDERLINE = "user_underline"
        const val KIND_USER_HIGHLIGHT = "user_highlight"

        const val STAGE_CONTENT = "content"
        const val STAGE_STYLE = "style"

        const val TARGET_SELECTION = "selection"
        const val TARGET_PARAGRAPH = "paragraph"
        const val TARGET_CHAPTER = "chapter"

        const val SOURCE_USER = "user"
        const val SOURCE_AI = "ai"

        const val STATUS_DRAFT = 0
        const val STATUS_ACTIVE = 1
        const val STATUS_DISABLED = 2
        const val STATUS_DELETED = 3
    }
}
