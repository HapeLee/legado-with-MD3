package io.legado.app.feature.reader.core.model

/** Reader tip color value `0` means "follow body text color", not transparent. */
fun resolveReaderTipColor(configuredColorArgb: Int, bodyTextColorArgb: Int): Int =
    configuredColorArgb.takeUnless { it == 0 } ?: bodyTextColorArgb
