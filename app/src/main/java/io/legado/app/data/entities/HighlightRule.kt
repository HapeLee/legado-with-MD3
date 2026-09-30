package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.uuid.Uuid

@Entity(tableName = "highlightRules")
data class HighlightRule(
    @PrimaryKey
    var id: String = Uuid.random().toString(),
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
    var fontWeight: Int = 400,
    var isItalic: Boolean = false,
    var fontSizeOffset: Int = 0,
    var npLeft: Float = 0.1f,
    var npRight: Float = 0.1f,
    var npTop: Float = 0.1f,
    var npBottom: Float = 0.1f,
    @ColumnInfo(defaultValue = "1")
    var manualNineSlice: Boolean = true,
    /** 命中字距（dp）：命中段与相邻字之间让出的空白，0 表示不让。只落在段首/段尾那一个字上。 */
    @ColumnInfo(defaultValue = "0")
    var letterSpacingBefore: Float = 0f,
    /** 命中字距（dp）：见 [letterSpacingBefore]。 */
    @ColumnInfo(defaultValue = "0")
    var letterSpacingAfter: Float = 0f,
    /** 命中行行距（dp）：把包含命中的那一行整行往下抬，行盒高度不变。 */
    @ColumnInfo(defaultValue = "0")
    var lineSpacingTop: Float = 0f,
    /** 命中行行距（dp）：这一行之后多留的空白，九宫格上下两条边就有多大的地方画。 */
    @ColumnInfo(defaultValue = "0")
    var lineSpacingBottom: Float = 0f,
    /**
     * 九宫格左偏移（dp）：字数不变时把中间那一格往左多拉（负值往回收）这么多，
     * 左边那两条边跟着平移。拆成左右两项是因为气泡两端要分别对齐，一个对称偏移调不动。
     */
    @ColumnInfo(defaultValue = "0")
    var bgLengthOffsetLeft: Float = 0f,
    /** 九宫格右偏移（dp）：见 [bgLengthOffsetLeft]，方向相反。 */
    @ColumnInfo(defaultValue = "0")
    var bgLengthOffsetRight: Float = 0f,
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
                    6 -> "删除线"
                    7 -> "荧光"
                    else -> "下划线"
                } + underlineColor?.let { " ${it.toHexColor()}" }.orEmpty()
            )
        }
        if (!bgImage.isNullOrBlank()) {
            parts.add(
                when (bgImageFit) {
                    1 -> "背景图(拉伸)"
                    2 -> "背景图(裁剪)"
                    3 -> "背景图(九宫格)"
                    else -> "背景图(平铺)"
                }
            )
        }
        if (letterSpacingBefore > 0f || letterSpacingAfter > 0f) {
            parts.add("命中字距 ${letterSpacingBefore.toInt()} / ${letterSpacingAfter.toInt()}dp")
        }
        if (lineSpacingTop > 0f || lineSpacingBottom > 0f) {
            parts.add("命中行行距 ${lineSpacingTop.toInt()} / ${lineSpacingBottom.toInt()}dp")
        }
        if (!fontPath.isNullOrBlank()) {
            parts.add("自定义字体")
        }
        if (fontSizeOffset != 0) {
            parts.add("字号${if (fontSizeOffset > 0) "+" else ""}${fontSizeOffset}")
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
        return sampleText.ifBlank { DEFAULT_SAMPLE_TEXT }
    }

    /**
     * 预览里被上样的那一段在示例句中的 [start, endExclusive)。
     *
     * 用户把示例句改成不含 [SAMPLE_HIT_TEXT] 的内容时返回 null：这时没有「那一段」可钉，
     * 预览退回按正则命中。
     */
    fun previewHitRange(): Pair<Int, Int>? {
        val start = normalizedSampleText().indexOf(SAMPLE_HIT_TEXT)
        return if (start < 0) null else start to start + SAMPLE_HIT_TEXT.length
    }

    fun copyWithNewId(): HighlightRule {
        return copy(id = Uuid.random().toString())
    }

    companion object {
        const val TARGET_ALL = 0
        const val TARGET_TITLE = 1
        const val TARGET_BODY = 2

        /**
         * 预览示例句：固定这一句，不再按正则换着迁就。引号外留着「张三：」和「他惊了！」，
         * 命中字距、命中行行距、背景图四周一圈都有相邻的字可以参照，调一个参数就能看出它
         * 到底只作用在中间那一段上。
         */
        const val DEFAULT_SAMPLE_TEXT = "张三：我是李四。他惊了！"

        /**
         * [DEFAULT_SAMPLE_TEXT] 里被上样的那一段。规则编辑的每一项改动都保证落在它上面，
         * 与用户填的正则命没命中无关（见 `LegacyReaderStyleRangeMapper.rangesForLiteralRange`）。
         */
        const val SAMPLE_HIT_TEXT = "我是李四。"

        fun Int.toHexColor(): String = String.format("#%08X", this)
    }
}
