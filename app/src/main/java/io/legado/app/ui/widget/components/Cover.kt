package io.legado.app.ui.widget.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.Coil.imageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.legado.app.model.BookCover.coverImageLoader

@Composable
fun Cover(
    path: Any?,
    modifier: Modifier = Modifier.width(48.dp),
    badgeContent: (@Composable RowScope.() -> Unit)? = null,
    loadOnlyWifi: Boolean = false,
    sourceOrigin: String? = null,
    onLoadFinish: (() -> Unit)? = null
) {
    val context = LocalContext.current

    val boxModifier = modifier
        .aspectRatio(3f / 4f)
        .clip(RoundedCornerShape(4.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerLow)

    Box(modifier = boxModifier) {
        if (path == null) {
            Icon(
                Icons.Default.Book,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(24.dp).align(Alignment.Center)
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(path)
                    .crossfade(true)
                    .setParameter("sourceOrigin", sourceOrigin)
                    .setParameter("loadOnlyWifi", loadOnlyWifi)
                    .listener(
                        onSuccess = { _, _ -> onLoadFinish?.invoke() },
                        onError = { _, _ -> onLoadFinish?.invoke() }
                    )
                    .build(),
                imageLoader = coverImageLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (badgeContent != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp),
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    content = badgeContent
                )
            }
        }
    }
}