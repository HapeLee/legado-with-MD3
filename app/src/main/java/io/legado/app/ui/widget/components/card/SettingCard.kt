package io.legado.app.ui.widget.components.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.basic.BasicComponent

// import top.yukonga.miuix.kmp.basic.theme.LocalColors as MiuixLocalColors

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SettingCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = 4.dp,
    colors: CardColors? = null,
    elevation: Dp = 0.dp,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val composeEngine = LegadoTheme.composeEngine
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

        GlassCard(
            modifier = modifier,
            onClick = onClick,
            cornerRadius = cornerRadius,
            containerColor = baseColors.containerColor,
            contentColor = baseColors.contentColor,
            elevation = elevation,
            border = border,
            content = content,
        )
    }
}
