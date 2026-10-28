package io.legado.app.feature.reader.platform

import io.legado.app.feature.reader.core.layout.ReaderReviewBubbleMetrics
import io.legado.app.help.config.BubblePackageManager
import io.legado.app.help.config.PackageResource
import io.legado.app.help.config.ReadBookConfig
import java.io.File

/**
 * 当前生效的段评气泡外观。
 *
 * 三种来源统一成一套参数：跟随书源图（[current] 返回 null）、旧版选定的单个 SVG 文件
 * （`custom`），以及气泡包（`package`，模板/四色/资源/缩放都来自 `bubble.json`）。
 */
data class ReviewBubbleAppearance(
    val template: String,
    val sizeScale: Float,
    val dayNormalColor: String?,
    val dayEmphasisColor: String?,
    val nightNormalColor: String?,
    val nightEmphasisColor: String?,
    val resources: List<PackageResource>,
    val resourceRoot: File?,
    val aspectRatio: Float,
    val revision: Long,
    /** 用于绘制缓存分键，换包/换文件后自动失效。 */
    val cacheKey: String,
)

object ReviewBubbleAppearances {

    private data class Cached(
        val style: String,
        val svgPath: String,
        val svgModified: Long,
        val packageDir: String,
        val packageUpdatedAt: Long,
        val appearance: ReviewBubbleAppearance?,
    )

    @Volatile
    private var cached: Cached? = null

    /** null = `source`：书源自己的段评图照常加载，应用不接管。 */
    fun current(): ReviewBubbleAppearance? {
        val style = ReadBookConfig.reviewBubbleStyle
        if (style == "source") return null
        val entry = BubblePackageManager.currentEntry()
        val svgPath = ReadBookConfig.reviewBubbleSvg
        val file = if (style == "custom") File(svgPath).takeIf { it.isFile } else null
        val modified = file?.lastModified() ?: 0L
        cached?.takeIf {
            it.style == style &&
                it.svgPath == svgPath &&
                it.svgModified == modified &&
                it.packageDir == entry.dirName &&
                it.packageUpdatedAt == entry.config.updatedAt
        }?.let { return it.appearance }

        val appearance = when {
            style == "custom" && file != null -> customAppearance(file)
            else -> of(entry)
        }
        cached = Cached(
            style = style,
            svgPath = svgPath,
            svgModified = modified,
            packageDir = entry.dirName,
            packageUpdatedAt = entry.config.updatedAt,
            appearance = appearance,
        )
        return appearance
    }

    private fun customAppearance(file: File): ReviewBubbleAppearance? {
        val template = runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: return of(BubblePackageManager.currentEntry())
        return ReviewBubbleAppearance(
            template = template,
            sizeScale = 1f,
            dayNormalColor = BubblePackageManager.DEFAULT_NORMAL_COLOR,
            dayEmphasisColor = BubblePackageManager.DEFAULT_EMPHASIS_COLOR,
            nightNormalColor = BubblePackageManager.DEFAULT_NORMAL_COLOR,
            nightEmphasisColor = BubblePackageManager.DEFAULT_EMPHASIS_COLOR,
            resources = emptyList(),
            resourceRoot = null,
            aspectRatio = aspectRatioOf(template),
            revision = file.lastModified(),
            cacheKey = "custom:${file.absolutePath}:${file.lastModified()}",
        )
    }

    /** 把一个气泡包转成绘制/测量用的外观；预览非当前包时也用它。 */
    fun of(entry: BubblePackageManager.Entry): ReviewBubbleAppearance {
        val config = entry.config
        return ReviewBubbleAppearance(
            template = config.svgTemplate,
            sizeScale = config.sizeScale,
            dayNormalColor = config.dayNormalColor,
            dayEmphasisColor = config.dayEmphasisColor,
            nightNormalColor = config.nightNormalColor,
            nightEmphasisColor = config.nightEmphasisColor,
            resources = config.resources,
            resourceRoot = entry.localDir,
            aspectRatio = ReaderReviewBubbleRenderer.aspectRatio(config.svgTemplate),
            revision = config.updatedAt,
            cacheKey = "${entry.dirName}:${config.updatedAt}",
        )
    }

    private fun aspectRatioOf(template: String): Float =
        ReaderReviewBubbleRenderer.aspectRatio(template)

    /** 排版层只要尺寸参数；跟随书源图时按默认尺寸占位（实际不会画气泡）。 */
    fun layoutMetrics(): Pair<Float, Float> {
        val appearance = current() ?: return 1f to ReaderReviewBubbleMetrics.DEFAULT_ASPECT_RATIO
        return appearance.sizeScale to appearance.aspectRatio
    }
}
