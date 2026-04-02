package io.legado.app.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import top.yukonga.miuix.kmp.theme.TextStyles


/**
 * 将 Miuix 的 TextStyles 语义化映射为 Material 3 的 Typography
 */

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun miuixStylesToM3Typography(miuixStyles: TextStyles): Typography {
    return Typography(
        displayLarge = miuixStyles.title1,   // 32.sp
        displayMedium = miuixStyles.title2,  // 24.sp
        displaySmall = miuixStyles.title3,   // 20.sp

        headlineLarge = miuixStyles.title2,  // 24.sp
        headlineMedium = miuixStyles.title3, // 20.sp
        headlineSmall = miuixStyles.title4,  // 18.sp

        titleLarge = miuixStyles.headline1,  // 17.sp
        titleMedium = miuixStyles.headline2, // 16.sp
        titleSmall = miuixStyles.subtitle,   // 14.sp, Bold

        bodyLarge = miuixStyles.paragraph,   // 17.sp, lineHeight 1.2em (如果不需要行高也可以换成 main)
        bodyMedium = miuixStyles.body1,      // 16.sp
        bodySmall = miuixStyles.body2,       // 14.sp

        labelLarge = miuixStyles.button,     // 17.sp
        labelMedium = miuixStyles.footnote1, // 13.sp
        labelSmall = miuixStyles.footnote2,   // 11.sp

        bodyLargeEmphasized = miuixStyles.paragraph.copy(fontWeight = FontWeight.Medium),
        bodyMediumEmphasized = miuixStyles.body1.copy(fontWeight = FontWeight.Medium),
        bodySmallEmphasized = miuixStyles.body2.copy(fontWeight = FontWeight.Medium),

        labelLargeEmphasized = miuixStyles.button.copy(fontWeight = FontWeight.Medium),
        labelMediumEmphasized = miuixStyles.footnote1.copy(fontWeight = FontWeight.Medium),
        labelSmallEmphasized = miuixStyles.footnote2.copy(fontWeight = FontWeight.Medium)
    )
}