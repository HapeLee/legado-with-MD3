package io.legado.app.domain.usecase

import io.legado.app.data.entities.BookMarking
import io.legado.app.domain.gateway.BookMarkingGateway
import io.legado.app.domain.model.BookContentProcessEngine
import io.legado.app.domain.model.TextProcessAnchor
import io.legado.app.domain.model.TextProcessStyle
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 用户划线/高亮笔记的落库（book_marks 表）。与书签、AI 正文处理完全独立：
 * 划线不建书签、删书签不碰划线。
 *
 * 与书签一样认「书名+作者」跨源关联：换源后笔记仍可见可管理；[bookUrl] 只作源指纹
 * （跳转校验用），渲染仍按当前源查。
 *
 * 同锚点（章节 + 章节内位置 + 归一化文本）已存在标记时**原地更新**（保留 id 与
 * createdAt，更新 style / note / 锚点 / 源指纹），而不是删了重建。
 *
 * 没有 kind 参数：book_marks 无 kind 列，样式（[TextProcessStyle]）即类型——
 * 单实线/波浪线/虚线/背景色/字体色 全部由 style 表达。
 */
class SaveMarkingUseCase(
    private val bookMarkingGateway: BookMarkingGateway,
) {

    /**
     * 串行化「查旧 → 更新/新增」整段：快速双击保存会并发执行，各自查到「无旧标记」
     * 就会重复划线。锁住后两次保存退化成正确的先更新再更新。
     */
    private val saveMutex = Mutex()

    /**
     * 保存一条标记。同锚点已有则更新，否则新增。
     *
     * @param bookName/bookAuthor 跨源关联键（与书签一致）
     * @param bookUrl            当前源（源指纹）。编辑旧源笔记时由调用方传入原值以保留源
     * @param chapterPosition    章节内字符位置（与普通书签 chapterPos 同语义，排版坐标）
     * @param selectedText       选中的原文（会做 trim 归一化）
     * @param chapterName        章节标题（目录 Sheet 笔记页展示用）
     * @param note               用户备注（笔记）
     */
    suspend fun save(
        bookName: String,
        bookAuthor: String,
        bookUrl: String,
        chapterIndex: Int,
        chapterPosition: Int,
        selectedText: String,
        style: TextProcessStyle,
        chapterName: String = "",
        note: String = "",
        contextBefore: String = "",
        contextAfter: String = "",
    ): BookMarking = withContext(Dispatchers.IO) {
        saveMutex.withLock {
            val normalized = BookContentProcessEngine.normalizeProcessText(selectedText)
            require(normalized.isNotBlank()) { "Selected text is empty" }
            val existing = find(
                bookName = bookName,
                bookAuthor = bookAuthor,
                chapterIndex = chapterIndex,
                chapterPosition = chapterPosition,
                selectedText = normalized,
            )
            val anchor = TextProcessAnchor(
                chapterIndex = chapterIndex,
                chapterPosition = chapterPosition,
                selectedText = normalized,
                contextBefore = contextBefore,
                contextAfter = contextAfter,
                normalizedTextHash = MD5Utils.md5Encode(normalized),
            )
            val now = System.currentTimeMillis()
            if (existing != null) {
                val updated = existing.copy(
                    bookUrl = bookUrl,
                    anchorJson = GSON.toJson(anchor),
                    styleJson = GSON.toJson(style),
                    note = note,
                    chapterName = chapterName.ifBlank { existing.chapterName },
                    enabled = true,
                    updatedAt = now,
                )
                bookMarkingGateway.upsert(updated)
                return@withContext updated
            }
            val mark = BookMarking(
                id = UUID.randomUUID().toString(),
                bookUrl = bookUrl,
                bookName = bookName,
                bookAuthor = bookAuthor,
                chapterIndex = chapterIndex,
                anchorJson = GSON.toJson(anchor),
                styleJson = GSON.toJson(style),
                note = note,
                chapterName = chapterName,
                enabled = true,
                createdAt = now,
                updatedAt = now,
            )
            bookMarkingGateway.upsert(mark)
            mark
        }
    }

    /**
     * 按锚点查找已有标记（供划线 Sheet 打开时预填样式与备注）。
     */
    suspend fun find(
        bookName: String,
        bookAuthor: String,
        chapterIndex: Int,
        chapterPosition: Int,
        selectedText: String,
    ): BookMarking? = withContext(Dispatchers.IO) {
        val normalized = BookContentProcessEngine.normalizeProcessText(selectedText)
        bookMarkingGateway.getByBook(bookName, bookAuthor, chapterIndex)
            .firstOrNull { mark ->
                val anchor = mark.anchor()
                anchor != null &&
                        anchor.chapterPosition == chapterPosition &&
                        anchor.selectedText == normalized
            }
    }

    /**
     * 按 id 取一条标记（编辑模式：从目录 Sheet 进入时用）。
     */
    suspend fun findById(id: String): BookMarking? = withContext(Dispatchers.IO) {
        bookMarkingGateway.getById(id)
    }

    suspend fun delete(id: String) {
        bookMarkingGateway.delete(id)
    }

    private fun BookMarking.anchor(): TextProcessAnchor? =
        GSON.fromJsonObject<TextProcessAnchor>(anchorJson).getOrNull()
}
