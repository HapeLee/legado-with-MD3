package io.legado.app.ui.book.read.pageestimate

data class PageEstimateConfig(
    val readerType: Int,
    val textSizePx: Float,
    val textHeightPx: Float,
    val lineSpacingPx: Float,
    val paragraphSpacingPx: Float,
    val titleTextSizePx: Float,
    val titleTextHeightPx: Float,
    val titleLineSpacingPx: Float,
    val titleTopSpacingPx: Float,
    val titleBottomSpacingPx: Float,
    val endPaddingPx: Float,
    val contentWidthPx: Int,
    val contentHeightPx: Int,
    val fontKey: String = "",
    val titleFontKey: String = "",
    val letterSpacing: Float = 0f,
    val paragraphIndent: String = "",
    val titleMode: Int = 0,
    val doublePage: Boolean = false,
    val useZhLayout: Boolean = false,
    val textFullJustify: Boolean = false,
    val contentKey: String = "",
    val textEngineVersion: Int = TEXT_ENGINE_VERSION,
) {
    init {
        require(contentWidthPx > 0)
        require(contentHeightPx > 0)
    }

    val layoutSignature: Long
        get() = stableHash(includeContentKey = true)

    val calibrationBucket: Long
        get() = stableHash(includeContentKey = false)

    private fun stableHash(includeContentKey: Boolean): Long = StablePageHash().apply {
        add(readerType)
        add(textSizePx)
        add(textHeightPx)
        add(lineSpacingPx)
        add(paragraphSpacingPx)
        add(titleTextSizePx)
        add(titleTextHeightPx)
        add(titleLineSpacingPx)
        add(titleTopSpacingPx)
        add(titleBottomSpacingPx)
        add(endPaddingPx)
        add(contentWidthPx)
        add(contentHeightPx)
        add(fontKey)
        add(titleFontKey)
        add(letterSpacing)
        add(paragraphIndent)
        add(titleMode)
        add(doublePage)
        add(useZhLayout)
        add(textFullJustify)
        add(textEngineVersion)
        if (includeContentKey) add(contentKey)
    }.value

    private companion object {
        const val TEXT_ENGINE_VERSION = 1
    }
}

private class StablePageHash {
    var value: Long = FNV_OFFSET_BASIS
        private set

    fun add(value: Boolean) = add(if (value) 1 else 0)

    fun add(value: Float) = add(value.toRawBits())

    fun add(value: Int) {
        repeat(Int.SIZE_BYTES) { byteIndex ->
            mix((value ushr (byteIndex * Byte.SIZE_BITS)) and 0xff)
        }
    }

    fun add(value: Long) {
        repeat(Long.SIZE_BYTES) { byteIndex ->
            mix(((value ushr (byteIndex * Byte.SIZE_BITS)) and 0xff).toInt())
        }
    }

    fun add(value: String) {
        value.encodeToByteArray().forEach { mix(it.toInt() and 0xff) }
        mix(0xff)
    }

    private fun mix(byte: Int) {
        value = (value xor byte.toLong()) * FNV_PRIME
    }

    private companion object {
        const val FNV_OFFSET_BASIS = -3750763034362895579L
        const val FNV_PRIME = 1099511628211L
    }
}

data class ChapterLengthInfo(
    val chapterIndex: Int,
    val chapterId: String,
    val titleLength: Int,
    val includeTitle: Boolean = true,
    val contentLength: Int?,
    val contentHash: Long? = null,
)

object ChapterContentHasher {
    private const val PREFIX_LENGTH = 1_024

    fun fromContent(content: String): Long = fromLengthAndPrefix(
        contentLength = content.length,
        prefix = content.take(PREFIX_LENGTH),
    )

    fun fromLengthAndPrefix(contentLength: Int, prefix: String): Long =
        StablePageHash().apply {
            add(contentLength)
            add(prefix.take(PREFIX_LENGTH))
        }.value

    fun fromLocalOffsets(start: Long?, end: Long?): Long? {
        if (start == null || end == null || end <= start) return null
        return StablePageHash().apply {
            add(start)
            add(end)
        }.value
    }
}

internal fun ChapterLengthInfo.toInput(
    config: PageEstimateConfig,
    fallbackContentLength: Int,
) = ChapterPageEstimateInput(
    readerType = config.readerType,
    titleLength = titleLength,
    includeTitle = includeTitle,
    contentLength = contentLength ?: fallbackContentLength,
    textSizePx = config.textSizePx,
    textHeightPx = config.textHeightPx,
    lineSpacingPx = config.lineSpacingPx,
    paragraphSpacingPx = config.paragraphSpacingPx,
    titleTextSizePx = config.titleTextSizePx,
    titleTextHeightPx = config.titleTextHeightPx,
    titleLineSpacingPx = config.titleLineSpacingPx,
    titleTopSpacingPx = config.titleTopSpacingPx,
    titleBottomSpacingPx = config.titleBottomSpacingPx,
    endPaddingPx = config.endPaddingPx,
    contentWidthPx = config.contentWidthPx,
    contentHeightPx = config.contentHeightPx,
)
