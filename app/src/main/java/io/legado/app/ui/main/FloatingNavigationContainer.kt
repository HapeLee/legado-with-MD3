package io.legado.app.ui.main

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A floating footer uses only the system inset that its parent has not already handled. */
@Composable
internal fun FloatingNavigationContainer(
    modifier: Modifier = Modifier,
    navigationBarInsets: WindowInsets = WindowInsets.navigationBars,
    onHeightChanged: (Dp) -> Unit = {},
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .onSizeChanged { onHeightChanged(with(density) { it.height.toDp() }) }
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(navigationBarInsets.only(WindowInsetsSides.Bottom))
            .padding(bottom = 12.dp),
        content = content,
    )
}
