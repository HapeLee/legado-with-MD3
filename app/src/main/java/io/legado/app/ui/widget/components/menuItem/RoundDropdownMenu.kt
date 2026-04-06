package io.legado.app.ui.widget.components.menuItem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.rememberOpaqueColorScheme
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.overlay.OverlayListPopup

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RoundDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    // M3 专属视觉参数，Miuix 模式下会优雅降级（忽略）
    shape: Shape = MaterialTheme.shapes.medium,
    shadowElevation: Dp = 4.dp,
    verticalSpacing: Dp = 8.dp,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
) {
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)

    if (isMiuix) {
        OverlayListPopup(
            show = expanded,
            onDismissRequest = onDismissRequest,
            popupModifier = modifier
        ) {
            ListPopupColumn {
                Column() {
                    Spacer(Modifier.height(12.dp))
                    content(onDismissRequest)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    } else {
        val colorScheme = rememberOpaqueColorScheme()

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            shape = shape,
            shadowElevation = shadowElevation,
            containerColor = colorScheme.surfaceContainerLow
        ) {
            MaterialExpressiveTheme(
                colorScheme = colorScheme,
                typography = Typography(),
                motionScheme = MotionScheme.expressive(),
                shapes = Shapes()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(verticalSpacing)
                ) {
                    content(onDismissRequest)
                }
            }
        }
    }
}
