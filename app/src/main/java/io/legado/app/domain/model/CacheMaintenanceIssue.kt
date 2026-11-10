package io.legado.app.domain.model

import androidx.compose.runtime.Stable

/**
 * 孤立章节缓存：磁盘上有缓存条目，但该书章节列表里已查不到对应章节。
 *
 * 常见成因是章节标题改名（`BookChapter.getFileName()` 里的 titleMD5 随之变化）
 * 或换源后目录重建，旧缓存留在旧 `%05d-<titleMD5>` 名下，后续不会再被任何章节命中。
 *
 * [entryNames] 是相对该书缓存目录的条目名，删除时由 Repository 还原绝对路径。
 */
@Stable
data class OrphanedChapterGroup(
    val bookUrl: String,
    val bookName: String,
    val entryNames: List<String>,
    val bytes: Long,
)

/**
 * 一次删除孤立章节缓存的结果。
 *
 * [skippedBooks] 因正在下载/正在阅读、或读不到章节列表而整本跳过的书数。
 * 这些书并没有被删，调用方要把它们留在列表里，否则会显示成"已删除"。
 */
@Stable
data class DeletedChapterCache(
    val deletedEntries: Int,
    val freedBytes: Long,
    /** 因正在下载/阅读或读不到章节列表而整本跳过的书数。 */
    val skippedBooks: Int = 0,
    /** 因图片文件租约仍活跃而未能删除的条目数 —— 这些条目必须留在列表里。 */
    val skippedEntries: Int = 0,
)

/** 一次批量删除请求：一本书及其待删的孤立条目名。 */
@Stable
data class OrphanDeleteRequest(
    val bookUrl: String,
    val entryNames: Set<String>,
)

/**
 * 删除过程的逐本回报。
 *
 * [accumulated] 是「到目前为止」的结果，[removedBookUrl] 是本次确认可以从列表移除的书；
 * 任务被取消时 ViewModel 仍能用最后一次回报写回已提交的部分，而不是丢掉全部。
 */
@Stable
data class DeletionProgress(
    val handled: Int,
    val total: Int,
    val accumulated: DeletedChapterCache,
    val removedBookUrl: String?,
)

/**
 * 字数重算候选。
 *
 * [unsolvedChapters] 非 0 时无法得出可信的整书字数：这些章节既没有缓存供重算，
 * 库里存的字数也无法还原成数值（可能是书源给的任意字符串）。此时整书字数字段保持为空，
 * 不能写一个会系统性偏小的假数字。
 */
@Stable
data class WordCountRecountTarget(
    val bookUrl: String,
    val bookName: String,
    /** 本次将要重算的章节数：有缓存且当前无字数。 */
    val recountableChapters: Int,
    /** 无法得到字数的章节数：既无缓存、库存字数也无法解析。非 0 则整书字数不更新。 */
    val unsolvedChapters: Int,
)

/** 一次字数重算的结果。 */
@Stable
data class RecountOutcome(
    val updatedChapters: Int,
    val updatedBooks: Int,
    /**
     * 本次真正进入重算流程的书。被正在下载/阅读、书已删除或并非在线文字书等条件跳过的书
     * 不在其中，调用方必须把它们留在列表里，否则会表现为「勾了、执行了、书消失却没补字数」。
     */
    val handledBookUrls: Set<String> = emptySet(),
)
