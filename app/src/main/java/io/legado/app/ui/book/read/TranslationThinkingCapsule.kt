package io.legado.app.ui.book.read

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme

@Composable
fun TranslationThinkingCapsule() {
    val transition = rememberInfiniteTransition(label = "translationThinking")
    val capsuleAlpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "translationThinkingAlpha",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 88.dp)
                .graphicsLayer { alpha = capsuleAlpha },
            shape = RoundedCornerShape(24.dp),
            color = LegadoTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f),
            contentColor = LegadoTheme.colorScheme.onSurface,
            tonalElevation = 2.dp,
        ) {
            Text(
                text = stringResource(R.string.translation_model_thinking),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                style = LegadoTheme.typography.labelLarge,
            )
        }
    }
}
