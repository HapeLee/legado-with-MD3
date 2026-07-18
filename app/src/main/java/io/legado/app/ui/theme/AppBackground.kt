package io.legado.app.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.legado.app.domain.model.settings.hasBackgroundImage
import org.koin.compose.koinInject

@Composable
fun AppBackground(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val themeSettings = LocalAppUiConfiguration.current.theme
    val hasImageBg = themeSettings.hasBackgroundImage(darkTheme)
    val bgImagePath = if (darkTheme) {
        themeSettings.backgroundImageDark
    } else {
        themeSettings.backgroundImageLight
    }
    val blur = if (darkTheme) {
        themeSettings.backgroundImageDarkBlurring
    } else {
        themeSettings.backgroundImageBlurring
    }

    Box(modifier = Modifier.fillMaxSize()) {

        if (hasImageBg && !bgImagePath.isNullOrBlank()) {
            AsyncImage(
                model = bgImagePath,
                contentDescription = null,
                imageLoader = koinInject(),
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blur.dp),
                contentScale = ContentScale.Crop
            )
        }

        content()
    }
}
