package io.legado.app.domain.usecase

/**
 * 书签/笔记跳转前的定位校验结果。
 */
sealed interface BookmarkTargetVerdict {
    /** 一切正常，直接跳。 */
    data object Match : BookmarkTargetVerdict

    /** 创建于其他书源（源指纹 != 当前源），章节定位很可能偏移。 */
    data class SourceChanged(val storedBookUrl: String) : BookmarkTargetVerdict

    /** 目标章节标题与存储的不符（同源目录重排，或换源后章节错位）。 */
    data object TitleMismatch : BookmarkTargetVerdict
}

/**
 * 跳转前校验书签/笔记的目标是否仍可靠。
 *
 * 换源后 `bookUrl` 会变、目录会重排，书签/笔记的 `chapterIndex`/`chapterPos` 是创建时
 * 源里的坐标，可能偏移。校验结果决定是否弹「仍跳转」确认框。
 */
class VerifyBookmarkTargetUseCase {

    /**
     * @param currentBookUrl      当前书源（当前书行 url）
     * @param targetChapterTitle  当前目录里目标章节的标题（调用方按 chapterIndex 解析，
     *                            拿不到（index 越界/未加载）传 null 跳过标题校验）
     * @param storedBookUrl       创建时的源指纹（旧数据为空 → 跳过源校验）
     * @param storedChapterName   创建时的章节标题
     */
    fun verify(
        currentBookUrl: String,
        targetChapterTitle: String?,
        storedBookUrl: String,
        storedChapterName: String,
    ): BookmarkTargetVerdict {
        if (storedBookUrl.isNotBlank() && storedBookUrl != currentBookUrl) {
            return BookmarkTargetVerdict.SourceChanged(storedBookUrl)
        }
        if (targetChapterTitle != null &&
            storedChapterName.isNotBlank() &&
            storedChapterName != targetChapterTitle
        ) {
            return BookmarkTargetVerdict.TitleMismatch
        }
        return BookmarkTargetVerdict.Match
    }
}
