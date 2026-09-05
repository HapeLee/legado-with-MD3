package io.legado.app.feature.reader.core.navigation

import io.legado.app.feature.reader.core.model.ReaderPageWindow

/** Immutable render data that is independent from an Android background drawable and state owner. */
data class ReaderRenderState(
    val pageWindow: ReaderPageWindow = ReaderPageWindow(),
    val paginationError: String? = null,
)
