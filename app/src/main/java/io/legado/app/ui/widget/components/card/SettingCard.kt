package io.legado.app.ui.widget.components.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.basic.BasicComponent

// import top.yukonga.miuix.kmp.basic.theme.LocalColors as MiuixLocalColors

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SettingCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = CardDefaults.shape,
    colors: CardColors? = null,
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val composeEngine = LegadoTheme.composeEngine
    val containerAlpha = ThemeConfig.containerOpacity / 100f

    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        BasicComponent(
            modifier = modifier,
            onClick = onClick,
            content = content
        )
    } else {
        val baseColors = colors ?: CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )

        val finalColors = CardDefaults.cardColors(
            containerColor = baseColors.containerColor.copy(alpha = containerAlpha),
            contentColor = baseColors.contentColor,
            disabledContainerColor = baseColors.disabledContainerColor.copy(alpha = containerAlpha),
            disabledContentColor = baseColors.disabledContentColor
        )

        if (onClick != null) {
            Card(
                onClick = onClick,
                modifier = modifier,
                shape = shape,
                colors = finalColors,
                elevation = elevation,
                border = border,
                content = content
            )
        } else {
            Card(
                modifier = modifier,
                shape = shape,
                colors = finalColors,
                elevation = elevation,
                border = border,
                content = content
            )
        }
    }
}
