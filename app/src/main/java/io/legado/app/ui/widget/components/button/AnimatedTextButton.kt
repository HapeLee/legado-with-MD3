package io.legado.app.ui.widget.components.button

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.TextButton
import io.legado.app.ui.widget.components.progressIndicator.AppContainedLoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.legado.app.ui.widget.components.text.AppText

@Composable
fun AnimatedTextButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier.animateContentSize()
    ) {
        AnimatedContent(
            targetState = isLoading,
            contentAlignment = Alignment.Center,
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(200))
            },
            label = "ButtonLoading"
        ) { loading ->
            if (loading) {
                AppContainedLoadingIndicator()
            } else {
                AppText(text)
            }
        }
    }
}