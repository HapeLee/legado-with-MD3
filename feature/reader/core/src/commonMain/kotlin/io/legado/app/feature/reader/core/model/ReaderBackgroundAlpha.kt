package io.legado.app.feature.reader.core.model

/** Stored reader background opacity uses the legacy 0..100 percentage scale. */
fun readerBackgroundAlpha(percent: Float): Float = (percent / 100f).coerceIn(0f, 1f)
