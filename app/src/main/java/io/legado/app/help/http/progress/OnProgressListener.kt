package io.legado.app.help.http.progress

typealias OnProgressListener = (isComplete: Boolean, percentage: Int, bytesRead: Long, totalBytes: Long) -> Unit
