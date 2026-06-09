package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "highlightRules")
data class HighlightRule(
    @PrimaryKey
    var id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var pattern: String = "",
    var sampleText: String = "",
    var targetScope: Int = TARGET_ALL,
    var enabled: Boolean = true,
    var position: Int = 0,
    var textColor: Int? = null,
    var bgColor: Int? = null,
    var underlineMode: Int = 0,
    var underlineColor: Int? = null,
    var underlineWidth: Float = 1f,
    var underlineOffset: Float = 2f,
    var underlineSvgPath: String? = null,
    var bgImage: String? = null,
    var bgImageFit: Int = 0,
    var bgImageScale: Float = 1f,
    var configName: String? = null,
    var fontPath: String? = null,
) {

    fun styleSummary(): String {
        val parts = ArrayList<String>(4)
        parts.add(targetScopeLabel())
        textColor?.let {
            parts.add("字色 ${it.toHexColor()}")
        }
        bgColor?.let {
            parts.add("背景色 ${it.toHexColor()}")
        }
        if (underlineMode != 0) {
            parts.add(
                when (underlineMode) {
                    1 -> "实线下划线"
                    2 -> "虚线下划线"
                    3 -> "波浪下划线"
                    4 -> "双下划线"
                    5 -> "自定义SVG"
                    else -> "下划线"
                } + underlineColor?.let { " ${it.toHexColor()}" }.orEmpty()
            )
        }
        if (!bgImage.isNullOrBlank()) {
            parts.add(
                when (bgImageFit) {
                    1 -> "背景图(拉伸)"
                    2 -> "背景图(裁剪)"
                    else -> "背景图(平铺)"
                }
            )
        }
        if (!fontPath.isNullOrBlank()) {
            parts.add("自定义字体")
        }
        if (parts.isEmpty()) {
            parts.add("无样式")
        }
        return parts.joinToString(" / ")
    }

    fun targetScopeLabel(): String {
        return when (targetScope) {
            TARGET_TITLE -> "作用于标题"
            TARGET_BODY -> "作用于正文"
            else -> "作用于全部"
        }
    }

    fun displayPattern(): String {
        return pattern.ifBlank { ".*" }
    }

    fun normalizedSampleText(): String {
        return sampleText.ifBlank {
            "她轻声说：“今晚就出发。”\n最近在重读《百年孤独》（纪念版），节奏依然很稳。"
        }
    }

    fun copyWithNewId(): HighlightRule {
        return copy(id = UUID.randomUUID().toString())
    }

    companion object {
        const val TARGET_ALL = 0
        const val TARGET_TITLE = 1
        const val TARGET_BODY = 2

        fun Int.toHexColor(): String = String.format("#%08X", this)
    }
}
