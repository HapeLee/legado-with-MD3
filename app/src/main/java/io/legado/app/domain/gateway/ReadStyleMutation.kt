package io.legado.app.domain.gateway

sealed interface ReadStyleMutation {
    data class IntValue(val key: ReadStyleIntKey, val value: Int) : ReadStyleMutation
    data class FloatValue(val key: ReadStyleFloatKey, val value: Float) : ReadStyleMutation
    data class BooleanValue(val key: ReadStyleBooleanKey, val value: Boolean) : ReadStyleMutation
    data class StringValue(val key: ReadStyleStringKey, val value: String) : ReadStyleMutation
    data class ColorValue(val key: ReadStyleColorKey, val value: Int) : ReadStyleMutation
    data class Background(val type: Int, val value: String) : ReadStyleMutation
}

enum class ReadStyleIntKey {
    TextSize,
    LineSpacing,
    ParagraphSpacing,
    TextBold,
    TitleMode,
    TitleBold,
    TitleLineSpacingExtra,
    TitleLineSpacingSub,
    TitleSize,
    TitleTopSpacing,
    TitleBottomSpacing,
    TitleSegType,
    TitleSegDistance,
    HeaderMode,
    FooterMode,
    TipHeaderLeft,
    TipHeaderMiddle,
    TipHeaderRight,
    TipFooterLeft,
    TipFooterMiddle,
    TipFooterRight,
    HeaderFontSize,
    PageAnim,
    UnderlineHeight,
    UnderlinePadding,
    PaddingTop,
    PaddingBottom,
    PaddingLeft,
    PaddingRight,
    HeaderPaddingTop,
    HeaderPaddingBottom,
    HeaderPaddingLeft,
    HeaderPaddingRight,
    FooterPaddingTop,
    FooterPaddingBottom,
    FooterPaddingLeft,
    FooterPaddingRight,
    BgType,
    BgTypeNight,
    BgTypeEInk,
    BgAlpha,
}

enum class ReadStyleFloatKey {
    LetterSpacing,
    TitleSegScaling,
    ShadowRadius,
    ShadowDx,
    ShadowDy,
    DottedBase,
    DottedRatio,
}

enum class ReadStyleBooleanKey {
    TextItalic,
    TextShadow,
    Underline,
    DottedLine,
    UnderlineExtend,
    ShowHeaderLine,
    ShowFooterLine,
    StatusIconDark,
}

enum class ReadStyleStringKey {
    TextFont,
    ParagraphIndent,
    TitleFont,
    TitleSegFlag,
    HeaderFont,
    CustomTipHeaderLeft,
    CustomTipHeaderMiddle,
    CustomTipHeaderRight,
    CustomTipFooterLeft,
    CustomTipFooterMiddle,
    CustomTipFooterRight,
    BgStr,
    BgStrNight,
    BgStrEInk,
    StyleName,
}

enum class ReadStyleColorKey {
    Text,
    TextAccent,
    Title,
    TitleNight,
    TipHeader,
    TipHeaderNight,
    TipFooter,
    TipFooterNight,
    TipDivider,
    Shadow,
    Underline,
}
