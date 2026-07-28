package io.legado.app.ui.widget.components

import android.graphics.BitmapFactory
import android.graphics.NinePatch
import android.graphics.drawable.NinePatchDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File

enum class AppContainerBackgroundType {
    Large,
    Item,
}

/** Paints the configured container image behind this layout's content. */
@Composable
fun Modifier.appContainerBackground(
    type: AppContainerBackgroundType = AppContainerBackgroundType.Large,
    backgroundImage: String? = null,
    useThemeBackground: Boolean = true,
    backgroundAlpha: Float? = null,
    contentScale: ContentScale = ContentScale.Crop,
): Modifier {
    val theme = LocalAppUiConfiguration.current.theme
    val configuredImage = if (useThemeBackground && theme.enableContainerBackgroundImage) {
        when (type) {
            AppContainerBackgroundType.Large -> if (LegadoTheme.isDark) {
                theme.largeContainerBackgroundImageDark
            } else {
                theme.largeContainerBackgroundImageLight
            }
            AppContainerBackgroundType.Item -> if (LegadoTheme.isDark) {
                theme.itemBackgroundImageDark
            } else {
                theme.itemBackgroundImageLight
            }
        }
    } else {
        null
    }
    val path = backgroundImage ?: configuredImage
    if (path.isNullOrBlank()) return this

    val alpha = backgroundAlpha ?: when (type) {
        AppContainerBackgroundType.Large -> theme.appColumnBackgroundOpacity / 100f
        AppContainerBackgroundType.Item -> theme.glassCardBackgroundOpacity / 100f
    }
    val ninePatch by produceState<NinePatchDrawable?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { loadNinePatch(path) }
    }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val requestWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val requestHeight = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val request = remember(context, path, ninePatch, requestWidth, requestHeight) {
        ImageRequest.Builder(context)
            .data(ninePatch ?: path)
            .size(requestWidth.coerceAtLeast(1), requestHeight.coerceAtLeast(1))
            .build()
    }
    val painter = rememberAsyncImagePainter(
        model = request,
        imageLoader = koinInject(),
    )
    return this
        .paint(
            painter = painter,
            sizeToIntrinsics = false,
            contentScale = if (ninePatch != null) ContentScale.FillBounds else contentScale,
            alpha = alpha.coerceIn(0f, 1f),
        )
}

private fun loadNinePatch(path: String): NinePatchDrawable? {
    if (!path.endsWith(".9.png", ignoreCase = true)) return null
    return runCatching {
        val bitmap = BitmapFactory.decodeFile(File(path).absolutePath) ?: return null
        val chunk = bitmap.ninePatchChunk
        if (!NinePatch.isNinePatchChunk(chunk)) return null
        NinePatchDrawable(null, bitmap, chunk, null, null)
    }.getOrNull()
}
