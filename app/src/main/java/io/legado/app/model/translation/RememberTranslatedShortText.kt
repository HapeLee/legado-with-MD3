package io.legado.app.model.translation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Remembers the translated version of a short text.
 * Falls back to original text while translating or on error.
 */
@Composable
fun rememberTranslatedShortText(
    text: String,
    enabled: Boolean = true
): String {
    var translated by remember(text) { mutableStateOf(text) }

    LaunchedEffect(text, enabled) {
        if (!enabled) {
            translated = text
            return@LaunchedEffect
        }

        translated = ShortTextTranslator.translate(text)
    }

    return translated
}