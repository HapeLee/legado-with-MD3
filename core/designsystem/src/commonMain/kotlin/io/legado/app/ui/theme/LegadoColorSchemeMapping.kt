package io.legado.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Material3 [ColorScheme] → [LegadoColorScheme] 的映射。
 *
 * **M1-4 上提**：本函数原先在 `:core:ui` 的 `ThemeColorSchemeOverride.kt`。那里整个文件混着
 * Android 依赖（`Context` / `Typeface` / `Uri` / `LruCache`），而 `:core:ui` 是 **Android-only**
 * 模块 ⇒ desktop 根本拿不到这个映射。
 *
后果是：`:core:designsystem` 里读 `LegadoTheme.colorScheme` 的组件（`RoundDropdownMenuItem`、
 * `AppModalBottomSheet`、`FilePickerSheet` 等）**在 desktop 上编译得过但渲染不了**
 * ——`LocalLegadoColorScheme` 的默认值是 `error("No ColorScheme provided")`，
 * 于是任何转 CMP 的 Feature Screen 一渲染就抛 `IllegalStateException`。
 *
 * 这是「四个 Feature 已全部转 CMP」之后仍然过不去的一道坎：编译证据到此为止，
 * 渲染需要主题，而主题的组装还留在 Android 侧。搬到 `commonMain` 后，desktop host
 * 可以用 `MaterialTheme` + 本函数自己搭出一套语义色，不再依赖 `:core:ui`。
 *
 * 上提之所以可行：这两个重载是**纯映射**，只碰 `ColorScheme` 与 `Color`，没有任何平台 API。
 * 包名保持不变（`io.legado.app.ui.theme`）⇒ `:core:ui` 侧调用方 import 零改动。
 *
 * @param customBgColor 背景色（默认取 [ColorScheme.background]）。
 * @param customFontColor 前景/文字色（默认取 [ColorScheme.onSurface]）。
 * @param customTopBarColor / customNavBarColor 顶栏与底栏色。⚠️ 这两个参数在映射里**没有被使用**
 *   （`LegadoColorScheme` 没有对应字段）——签名保留是为了不动调用方，不是因为它们有效。
 * @param surfaceInput 输入框底色；`Unspecified` 表示沿用主题默认。
 */
fun ColorScheme.toLegadoColorScheme(
    customBgColor: Color = background,
    customFontColor: Color = onSurface,
    customTopBarColor: Color = surface,
    customNavBarColor: Color = surface,
    surfaceInput: Color = Color.Unspecified,
): LegadoColorScheme {
    return LegadoColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = customBgColor,
        onBackground = customFontColor,
        surface = surface,
        onSurface = customFontColor,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = surfaceTint,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = outline,
        outlineVariant = outlineVariant,
        scrim = scrim,
        surfaceBright = surfaceBright,
        surfaceDim = surfaceDim,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainerLowest = surfaceContainerLowest,
        primaryFixed = primaryFixed,
        primaryFixedDim = primaryFixedDim,
        onPrimaryFixed = onPrimaryFixed,
        onPrimaryFixedVariant = onPrimaryFixedVariant,
        secondaryFixed = secondaryFixed,
        secondaryFixedDim = secondaryFixedDim,
        onSecondaryFixed = onSecondaryFixed,
        onSecondaryFixedVariant = onSecondaryFixedVariant,
        tertiaryFixed = tertiaryFixed,
        tertiaryFixedDim = tertiaryFixedDim,
        onTertiaryFixed = onTertiaryFixed,
        onTertiaryFixedVariant = onTertiaryFixedVariant,
        cardContainer = surfaceContainerLow,
        onCardContainer = primary,
        onSheetContent = surface,
        cardPrimaryContainer = primaryContainer,
        surfaceInput = surfaceInput
    )
}

/** 全部取默认值的便捷重载。 */
fun ColorScheme.toLegadoColorScheme(): LegadoColorScheme {
    return toLegadoColorScheme(
        customBgColor = background,
        customFontColor = onSurface,
        customTopBarColor = surface,
        customNavBarColor = surface
    )
}
