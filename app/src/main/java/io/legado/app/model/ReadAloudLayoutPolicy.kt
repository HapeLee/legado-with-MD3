package io.legado.app.model

internal fun shouldRestartReadAloudAfterContentLoad(
    preserveReadAloudPosition: Boolean,
    serviceChapterIndex: Int,
    loadedChapterIndex: Int,
): Boolean = !preserveReadAloudPosition || serviceChapterIndex != loadedChapterIndex
