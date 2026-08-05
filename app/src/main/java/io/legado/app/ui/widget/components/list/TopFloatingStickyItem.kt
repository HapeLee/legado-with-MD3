package io.legado.app.ui.widget.components.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun <T : Any> TopFloatingStickyItem(
    item: T?,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn() + slideInVertically { -it },
    exit: ExitTransition = fadeOut() + slideOutVertically { -it },
    content: @Composable (T) -> Unit
) {
    var lastItem by remember { mutableStateOf<T?>(null) }

    AnimatedVisibility(
        visible = item != null,
        modifier = modifier,
        enter = enter,
        exit = exit
    ) {
        item?.let { lastItem = it }
        val currentItem = lastItem
        if (currentItem != null) {
            content(currentItem)
        }
    }
}
