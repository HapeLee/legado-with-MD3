package io.legado.app.ui.widget.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import coil.compose.AsyncImage
import coil.request.ImageRequest

private val DefaultCoverModifier = Modifier
    .width(48.dp)
    .height(68.dp)

@Composable
fun Cover(
    path: Any?,
    modifier: Modifier = DefaultCoverModifier,
    badgeContent: (@Composable RowScope.() -> Unit)? = null,
    loadOnlyWifi: Boolean = false,
    sourceOrigin: String? = null,
    onLoadFinish: (() -> Unit)? = null
) {
    val context = LocalContext.current

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center
        ) {
            if (path == null) {
                Icon(
                    Icons.Default.Book,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(path) // 可以是 URL、File、Uri、DrawableRes
                        .crossfade(true)
                        .apply {
                            if (sourceOrigin != null) setParameter("sourceOrigin", sourceOrigin)
                            if (loadOnlyWifi) setParameter("loadOnlyWifi", true)
                        }
                        .listener(
                            onSuccess = { _, _ -> onLoadFinish?.invoke() },
                            onError = { _, _ -> onLoadFinish?.invoke() }
                        )
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
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