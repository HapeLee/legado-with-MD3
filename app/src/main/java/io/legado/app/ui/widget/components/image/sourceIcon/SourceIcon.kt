package io.legado.app.ui.widget.components.image.sourceIcon

import android.graphics.drawable.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.koin.compose.koinInject

/**
 * 专门用于显示源图标的组件
 * 1. 默认正方形比例
 * 2. ContentScale.Fit (不裁切)
 * 3. 无强制背景色，适合在已有背景的容器中使用
 */
@Composable
fun SourceIcon(
    path: Any?,
    modifier: Modifier = Modifier.size(32.dp),
    sourceOrigin: String? = null,
    loadOnlyWifi: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit,
    imageLoader: ImageLoader = koinInject(),
    placeholderIcon: @Composable () -> Unit = {
        Icon(
            Icons.Default.RssFeed,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.fillMaxSize(0.7f)
        )
    }
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var imageLoaded by remember(path) { mutableStateOf(false) }
    var animatedDrawable by remember(path) { mutableStateOf<Animatable?>(null) }
    val imageRequest = remember(context, path, sourceOrigin, loadOnlyWifi) {
        ImageRequest.Builder(context)
            .data(path)
            .crossfade(true)
            .setParameter("sourceOrigin", sourceOrigin)
            .setParameter("loadOnlyWifi", loadOnlyWifi)
            .build()
    }

    DisposableEffect(lifecycle, animatedDrawable) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (animatedDrawable?.isRunning == false) {
                        animatedDrawable?.start()
                    }
                }

                Lifecycle.Event.ON_PAUSE -> animatedDrawable?.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            if (animatedDrawable?.isRunning == false) {
                animatedDrawable?.start()
            }
        } else {
            animatedDrawable?.stop()
        }
        onDispose {
            lifecycle.removeObserver(observer)
            animatedDrawable?.stop()
        }
    }

    Box(
        modifier = modifier.clip(MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center
    ) {
        if (path == null || (path is String && path.isEmpty())) {
            placeholderIcon()
        } else {
            if (!imageLoaded) {
                placeholderIcon()
            }
            AsyncImage(
                model = imageRequest,
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = contentScale, // 不裁切
                modifier = Modifier.fillMaxSize(),
                onSuccess = { state ->
                    imageLoaded = true
                    animatedDrawable = (state.result.drawable as? Animatable)?.also { drawable ->
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            if (!drawable.isRunning) drawable.start()
                        } else {
                            drawable.stop()
                        }
                    }
                },
                onError = {
                    imageLoaded = false
                    animatedDrawable = null
                }
            )
        }
    }
}
