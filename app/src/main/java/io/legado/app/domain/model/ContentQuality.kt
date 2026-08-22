package io.legado.app.domain.model

data class ContentQualityConfig(
    val chapterSpec: String,
    val keywords: List<String>,
    val skipHeadChars: Int,
)

enum class ContentQualityStage {
    ChapterCount,
    ContentCleaning,
    KeywordMatching,
    Excluding,
    Completed,
}

data class ContentQualityProgress(
    val stage: ContentQualityStage,
    val processedBooks: Int,
    val totalBooks: Int,
    val currentBookName: String = "",
)

data class ContentQualityResult(
    val bookKey: String,
    val sourceName: String,
    val sampledChapterCount: Int,
    val matchedChapterCount: Int,
    val keywordHits: Int,
    val excluded: Boolean,
    val errorMessage: String? = null,
)
