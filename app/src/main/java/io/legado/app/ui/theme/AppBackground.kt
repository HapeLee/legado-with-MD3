package io.legado.app.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.legado.app.ui.config.themeConfig.ThemeConfig

@Composable
fun AppBackground(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val hasImageBg = ThemeConfig.hasImageBg(darkTheme)
    val bgImagePath = if (darkTheme) ThemeConfig.bgImageDark else ThemeConfig.bgImageLight
    val blur = if (darkTheme) {
        ThemeConfig.bgImageNBlurring
    } else {
        ThemeConfig.bgImageBlurring
    }

    Box(modifier = Modifier.fillMaxSize()) {

        if (hasImageBg && !bgImagePath.isNullOrBlank()) {
            AsyncImage(
                model = bgImagePath,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blur.dp),
                //TODO:低版本安卓使用coil-transformations
                contentScale = ContentScale.Crop
            )
        }

        content()
    }
}