package io.legado.app.ui.widget.components.modalBottomSheet

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import io.legado.app.ui.theme.rememberOpaqueColorScheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GlassModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    ),
    containerColor: (ColorScheme) -> Color = { it.surfaceContainerLow },
    content: @Composable ColumnScope.() -> Unit
) {
    val colorScheme = rememberOpaqueColorScheme()
    val density = LocalDensity.current
    val maxHeight = with(density) {
        LocalWindowInfo.current.containerSize.height.toDp() * 0.8f
    }

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
                    .heightIn(max = maxHeight)
                    .animateContentSize()
                    .then(modifier),
                content = content
            )
        }
    }
}