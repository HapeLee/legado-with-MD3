package io.legado.app.feature.reader.legacy

import android.os.Build
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import io.legado.app.feature.reader.core.model.ReaderTextShadow
import io.legado.app.feature.reader.core.model.ReaderEmphasisUnderline
import io.legado.app.feature.reader.platform.ReaderAndroidPaintFactory
import io.legado.app.feature.reader.platform.ReaderAndroidPaginationStyle
import io.legado.app.feature.reader.core.source.ReaderTitleSegmentation
import io.legado.app.feature.reader.core.layout.ReaderColumnMode
import io.legado.app.feature.reader.core.layout.ReaderPageUnderline
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.help.book.isImage
import io.legado.app.model.ReadBook
import io.legado.app.utils.isPad
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.utils.dpToPx
import io.legado.app.utils.spToPx
import io.legado.app.utils.textHeight
import splitties.init.appCtx

/** Builds the direct renderer's shaping snapshot from persisted reader settings. */
object LegacyReaderPaginationStyleFactory : KoinComponent {
    private val readSettings: ReadSettingsGateway by inject()
    private val themeSettings: ThemeSettingsGateway by inject()
    fun create(): ReaderAndroidPaginationStyle {
        val settings = readSettings.currentSettings
        val isEInkMode = themeSettings.currentSettings.appTheme == "4"
        val bodyStyle = ReaderTextStyle(
            colorArgb = ReadBookConfig.textColor,
            fontSizePx = ReadBookConfig.textSize.toFloat().spToPx(),
            fontPath = ReadBookConfig.textFont,
            fontWeight = resolveWeight(ReadBookConfig.textBold),
            italic = ReadBookConfig.textItalic,
            shadow = if (ReadBookConfig.textShadow) ReaderTextShadow(
                colorArgb = ReadBookConfig.textShadowColor,
                radiusPx = ReadBookConfig.shadowRadius,
                dxPx = ReadBookConfig.shadowDx,
                dyPx = ReadBookConfig.shadowDy,
            ) else null,
            fontFamily = when (settings.systemTypefaces) {
                1 -> "serif"
                2 -> "monospace"
                else -> "sans-serif"
            },
            linearText = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q && settings.optimizeRender,
        )
        val titleStyle = bodyStyle.copy(
            colorArgb = ReadBookConfig.resolvedTitleColor.takeIf { it != 0 } ?: bodyStyle.colorArgb,
            fontSizePx = ReadBookConfig.titleSize.toFloat().spToPx(),
            fontPath = ReadBookConfig.titleFont.takeIf(String::isNotBlank) ?: bodyStyle.fontPath,
            fontWeight = resolveWeight(ReadBookConfig.titleBold),
        )
        val letterSpacing = ReadBookConfig.letterSpacing
        val bodyPaint = ReaderAndroidPaintFactory.createTextPaint(bodyStyle).apply { this.letterSpacing = letterSpacing }
        val titlePaint = ReaderAndroidPaintFactory.createTextPaint(titleStyle).apply { this.letterSpacing = letterSpacing }
        return ReaderAndroidPaginationStyle(
            columnMode = ReaderColumnMode.fromPreference(settings.doubleHorizontalPage),
            isTablet = appCtx.isPad,
            isScroll = ReadBook.pageAnim() == 3,
            textBottomJustify = settings.textBottomJustify,
            pageUnderline = ReadBookConfig.underline
                .takeIf { it && ReadBook.book?.isImage != true }
                ?.let {
                    ReaderPageUnderline(
                        colorArgb = ReadBookConfig.durConfig.curUnderlineColor(),
                        widthPx = ReadBookConfig.underlineHeight.toFloat(),
                        offsetPx = (ReadBookConfig.underlinePadding - 10).dpToPx().toFloat(),
                        extendToColumn = ReadBookConfig.underlineExtend,
                        dashed = ReadBookConfig.dottedLine && !isEInkMode,
                        dashOnPx = ReadBookConfig.durConfig.dottedBase,
                        dashOffPx = ReadBookConfig.durConfig.dottedRatio,
                    )
                },
            emphasisUnderlineStyle = settings.useUnderline.takeIf { it }?.let {
                ReaderEmphasisUnderline(
                    colorArgb = ReadBookConfig.textColor,
                    widthPx = ReadBookConfig.underlineHeight.toFloat(),
                    bottomOffsetPx = 1.dpToPx().toFloat(),
                )
            },
            bodyStyle = bodyStyle,
            titleStyle = titleStyle,
            bodyPaint = bodyPaint,
            titlePaint = titlePaint,
            paddingLeftPx = ReadBookConfig.paddingLeft.dpToPx(),
            paddingTopPx = ReadBookConfig.paddingTop.dpToPx(),
            paddingRightPx = ReadBookConfig.paddingRight.dpToPx(),
            paddingBottomPx = ReadBookConfig.paddingBottom.dpToPx(),
            bodyTextHeightPx = bodyPaint.textHeight,
            titleTextHeightPx = titlePaint.textHeight,
            bodyBaselineOffsetPx = ReaderAndroidPaintFactory.baselineOffset(bodyPaint),
            titleBaselineOffsetPx = ReaderAndroidPaintFactory.baselineOffset(titlePaint),
            lineSpacingExtra = ReadBookConfig.lineSpacingExtra / 10f,
            titleLineSpacingExtra = ReadBookConfig.titleLineSpacingExtra / 10f,
            paragraphSpacing = ReadBookConfig.paragraphSpacing,
            titleTopSpacingPx = ReadBookConfig.titleTopSpacing.dpToPx().toFloat(),
            titleBottomSpacingPx = ReadBookConfig.titleBottomSpacing.dpToPx().toFloat(),
            titleLineSpacingSub = ReadBookConfig.titleLineSpacingSub / 10f,
            titleSegmentation = ReaderTitleSegmentation(
                type = ReadBookConfig.titleSegType,
                distance = ReadBookConfig.titleSegDistance,
                delimiter = ReadBookConfig.titleSegFlag,
                subtitleScale = ReadBookConfig.titleSegScaling,
            ),
        )
    }

    /** 字重配置的口径（0=常规 / 1=加粗 / 2=细 / 100..900=直接值）：预览与正文共用这一份。 */
    fun resolveWeight(configured: Int): Int = when (configured) {
        1 -> 900
        2 -> 300
        in 100..900 -> configured
        else -> 400
    }
}
