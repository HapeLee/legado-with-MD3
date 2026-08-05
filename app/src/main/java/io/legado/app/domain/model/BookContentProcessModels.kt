package io.legado.app.domain.model

import androidx.annotation.Keep

@Keep
data class TextProcessAnchor(
    val chapterIndex: Int,
    val chapterPosition: Int? = null,
    val selectedText: String,
    val contextBefore: String = "",
    val contextAfter: String = "",
    val normalizedTextHash: String,
)

@Keep
data class TextProcessAction(
    val type: String,
    val replacement: String? = null,
    val text: String? = null,
) {
    companion object {
        const val TYPE_REPLACE = "replace"
        const val TYPE_DELETE = "delete"
        const val TYPE_INSERT_BEFORE = "insert_before"
        const val TYPE_INSERT_AFTER = "insert_after"

        fun replace(replacement: String): TextProcessAction {
            return TextProcessAction(TYPE_REPLACE, replacement = replacement)
        }

        fun delete(): TextProcessAction {
            return TextProcessAction(TYPE_DELETE)
        }
    }
}

@Keep
data class TextProcessStyle(
    val textColor: Int? = null,
    val bgColor: Int? = null,
    val underlineMode: Int = 0,
    val underlineColor: Int? = null,
    val underlineWidth: Float = 1f,
    val underlineOffset: Float = 2f,
    val underlineSvgPath: String? = null,
)
