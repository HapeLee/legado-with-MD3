package io.legado.app.ui.widget.components.image.cover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.size.Size
import io.legado.app.ui.theme.LegadoTheme

@Composable
fun CoverBlurBackdrop(
    name: String?,
    author: String?,
    path: String?,
    sourceOrigin: String?,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 48.dp,
) {
    val tint = lerp(LegadoTheme.colorScheme.secondaryContainer, LegadoTheme.seedColor, 0.42f)
    Box(modifier.fillMaxSize()) {
        BookCoverImage(
            name = name,
            author = author,
            path = path,
            sourceOrigin = sourceOrigin,
            memoryCacheKey = path?.let { "$it#cover-blur-backdrop" },
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius),
            contentScale = ContentScale.Crop,
            showLoadingPlaceholder = false,
            requestBuilder = { size(Size(512, 512)) },
        )
        Box(Modifier
            .fillMaxSize()
            .background(tint.copy(alpha = 0.38f)))
        Box(Modifier
            .fillMaxSize()
            .background(LegadoTheme.colorScheme.surface.copy(alpha = 0.52f)))
    }
}
