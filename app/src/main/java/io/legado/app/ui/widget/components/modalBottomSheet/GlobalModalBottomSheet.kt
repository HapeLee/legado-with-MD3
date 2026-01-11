package io.legado.app.ui.widget.components.modalBottomSheet

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.SheetState
import androidx.compose.material3.Typography
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.legado.app.ui.theme.ThemeManager
import io.legado.app.ui.theme.ThemeState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GlobalModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    containerColor: (ColorScheme) -> Color = { it.surfaceContainerLow },
    content: @Composable ColumnScope.() -> Unit
) {
    val themeMode by ThemeState.themeMode.collectAsState()
    val isPureBlack by ThemeState.isPureBlack.collectAsState()
    val hasImageBg by ThemeState.hasImageBg.collectAsState()

    val colorScheme = ThemeManager.getColorScheme(
        mode = themeMode,
        isAmoled = isPureBlack,
        isImageBg = hasImageBg,
        forceOpaque = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = containerColor(colorScheme),
        contentColor = colorScheme.onSurface
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            motionScheme = MotionScheme.expressive(),
            shapes = Shapes()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .then(modifier),
                content = content
            )
        }
    }
}