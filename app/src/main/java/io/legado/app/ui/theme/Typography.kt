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
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun Typography.toLegadoTypography(): LegadoTypography {
    return LegadoTypography(
        headlineLarge = headlineLarge,
        headlineLargeEmphasized = headlineLarge.copy(fontWeight = FontWeight.Medium),
        headlineMedium = headlineMedium,
        headlineMediumEmphasized = headlineMedium.copy(fontWeight = FontWeight.Medium),
        headlineSmall = headlineSmall,
        headlineSmallEmphasized = headlineSmall.copy(fontWeight = FontWeight.Medium),
        titleLarge = titleLarge,
        titleLargeEmphasized = titleLarge.copy(fontWeight = FontWeight.Medium),
        titleMedium = titleMedium,
        titleMediumEmphasized = titleMedium.copy(fontWeight = FontWeight.Medium),
        titleSmall = titleSmall,
        titleSmallEmphasized = titleSmall.copy(fontWeight = FontWeight.Medium),
        bodyLarge = bodyLarge,
        bodyLargeEmphasized = bodyLarge.copy(fontWeight = FontWeight.Medium),
        bodyMedium = bodyMedium,
        bodyMediumEmphasized = bodyMedium.copy(fontWeight = FontWeight.Medium),
        bodySmall = bodySmall,
        bodySmallEmphasized = bodySmall.copy(fontWeight = FontWeight.Medium),
        labelLarge = labelLarge,
        labelLargeEmphasized = labelLarge.copy(fontWeight = FontWeight.Medium),
        labelMedium = labelMedium,
        labelMediumEmphasized = labelMedium.copy(fontWeight = FontWeight.Medium),
        labelSmall = labelSmall,
        labelSmallEmphasized = labelSmall.copy(fontWeight = FontWeight.Medium)
    )
}