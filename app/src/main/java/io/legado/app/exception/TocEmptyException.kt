package io.legado.app.exception

/**
 * 目录为空
 */
open class TocEmptyException(msg: String) : NoStackTraceException(msg)

internal class LocalMangaDirectoryEmptyException(msg: String) : TocEmptyException(msg)
