package io.legado.app.ui.widget.components.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.LocalMochiSettingsGroup
import io.legado.app.ui.widget.components.LocalCouiSettingsGroup
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
    if (composeEngine == ComposeEngine.Mochi && LocalMochiSettingsGroup.current ||
        composeEngine == ComposeEngine.Coui && LocalCouiSettingsGroup.current != null
    ) {
        Surface(
            modifier = modifier.then(
                onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier
            ),
            color = colors?.containerColor ?: Color.Transparent,
            contentColor = colors?.contentColor ?: LegadoTheme.colorScheme.onSurface,
        ) {
            Column(content = content)
        }
        return
    }

    when (composeEngine) {
        ComposeEngine.Miuix -> {
            BasicComponent(
                modifier = modifier,
                onClick = onClick,
                content = content
            )
        }
        ComposeEngine.Material, ComposeEngine.Mochi, ComposeEngine.Coui -> {
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
}
