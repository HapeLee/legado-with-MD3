package io.legado.app.ui.widget.components.button

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun AnimatedIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = imageVector,
        transitionSpec = {
            (fadeIn() + scaleIn(initialScale = 0.8f))
                .togetherWith(fadeOut())
        },
        label = "IconTransition"
    ) { targetIcon ->
        Icon(
            imageVector = targetIcon,
            contentDescription = contentDescription,
            modifier = modifier
        )
    }
}
