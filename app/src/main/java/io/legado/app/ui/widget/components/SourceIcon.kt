package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

    Box(
        modifier = modifier.clip(MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center
    ) {
        if (path == null || (path is String && path.isEmpty())) {
            placeholderIcon()
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(path)
                    .crossfade(true)
                    .setParameter("sourceOrigin", sourceOrigin)
                    .setParameter("loadOnlyWifi", loadOnlyWifi)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = ContentScale.Fit, // 不裁切
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}