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

        /** 用户划线/高亮标记：不改文本，样式由 styleJson 承载，文本仅供锚点定位。 */
        const val TYPE_MARK = "mark"

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

/**
 * 用户划线/高亮笔记的 5 种效果（5x1 互斥）：单实线/波浪线/虚线/背景色/字体色。
 *
 * 样式即类型：book_marks 不再存 kind 列，效果由 [TextProcessStyle] 推导/生成，
 * 渲染引擎按 styleJson 画线，效果本身与「划线 vs 高亮」的老二元 kind 等价。
 */
@Keep
enum class MarkingEffect {
    SOLID, WAVE, DASHED, BG, TEXT;

    /** 是否属于下划线类效果（对应 underlineMode != 0）。 */
    val isUnderline: Boolean
        get() = this == SOLID || this == WAVE || this == DASHED

    /**
     * 由效果 + 选中颜色生成样式。背景色自动半透明（约 20% alpha），
     * 避免不透明背景盖住正文；下划线/字体色用原色。
     */
    fun toStyle(color: Int): TextProcessStyle = when (this) {
        SOLID -> TextProcessStyle(underlineMode = 1, underlineColor = color)
        WAVE -> TextProcessStyle(underlineMode = 3, underlineColor = color)
        DASHED -> TextProcessStyle(underlineMode = 2, underlineColor = color)
        BG -> TextProcessStyle(bgColor = (color and 0x00FFFFFF) or 0x33000000)
        TEXT -> TextProcessStyle(textColor = color)
    }

    companion object {
        /** 标记默认颜色（绿色）。 */
        const val DEFAULT_COLOR = 0xFF63C37D.toInt()

        /** 从样式反推效果：编辑已有标记时预填 5x1 格。未知下划线模式回退单实线。 */
        fun fromStyle(style: TextProcessStyle?): MarkingEffect = when {
            style?.underlineMode == 1 -> SOLID
            style?.underlineMode == 3 -> WAVE
            style?.underlineMode == 2 -> DASHED
            style?.bgColor != null -> BG
            style?.textColor != null -> TEXT
            else -> SOLID
        }

        /** 取样式的「展示色」：下划线取线色，背景剥掉 alpha 取底色，字体取字色。 */
        fun colorOf(style: TextProcessStyle?): Int = when {
            style?.underlineColor != null -> style.underlineColor
            style?.bgColor != null -> (style.bgColor and 0x00FFFFFF) or 0xFF000000.toInt()
            style?.textColor != null -> style.textColor
            else -> DEFAULT_COLOR
        }
    }
}
