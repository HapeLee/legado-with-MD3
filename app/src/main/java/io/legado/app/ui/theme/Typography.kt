package io.legado.app.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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

        headlineLarge = miuixStyles.title1,  // 32.sp
        headlineMedium = miuixStyles.title2, // 24.sp
        headlineSmall = miuixStyles.title3,  // 20.sp

        titleLarge = miuixStyles.title4,     // 18.sp
        titleMedium = miuixStyles.headline2, // 16.sp
        titleSmall = miuixStyles.subtitle,   // 14.sp, Bold

        bodyLarge = miuixStyles.paragraph,   // 17.sp
        bodyMedium = miuixStyles.body1,      // 16.sp
        bodySmall = miuixStyles.body2.copy(fontSize = 12.sp), // 12.sp

        labelLarge = miuixStyles.footnote1.copy(fontSize = 14.sp), // 14.sp
        labelMedium = miuixStyles.footnote1, // 13.sp
        labelSmall = miuixStyles.footnote2,  // 11.sp

        bodyLargeEmphasized = miuixStyles.paragraph.copy(fontWeight = FontWeight.Medium),
        bodyMediumEmphasized = miuixStyles.body1.copy(fontWeight = FontWeight.Medium),
        bodySmallEmphasized = miuixStyles.body2.copy(
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        ),

        labelLargeEmphasized = miuixStyles.button.copy(
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        ),
        labelMediumEmphasized = miuixStyles.footnote1.copy(fontWeight = FontWeight.Medium),
        labelSmallEmphasized = miuixStyles.footnote2.copy(fontWeight = FontWeight.Medium)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun Typography.toLegadoTypography(): LegadoTypography {
    return LegadoTypography(
        headlineLarge = headlineLarge,
        headlineLargeEmphasized = headlineLargeEmphasized,
        headlineMedium = headlineMedium,
        headlineMediumEmphasized = headlineMediumEmphasized,
        headlineSmall = headlineSmall,
        headlineSmallEmphasized = headlineSmallEmphasized,
        titleLarge = titleLarge,
        titleLargeEmphasized = titleLargeEmphasized,
        titleMedium = titleMedium,
        titleMediumEmphasized = titleMediumEmphasized,
        titleSmall = titleSmall,
        titleSmallEmphasized = titleSmallEmphasized,
        bodyLarge = bodyLarge,
        bodyLargeEmphasized = bodyLargeEmphasized,
        bodyMedium = bodyMedium,
        bodyMediumEmphasized = bodyMediumEmphasized,
        bodySmall = bodySmall,
        bodySmallEmphasized = bodySmallEmphasized,
        labelLarge = labelLarge,
        labelLargeEmphasized = labelLargeEmphasized,
        labelMedium = labelMedium,
        labelMediumEmphasized = labelMediumEmphasized,
        labelSmall = labelSmall,
        labelSmallEmphasized = labelSmallEmphasized
    )
}
