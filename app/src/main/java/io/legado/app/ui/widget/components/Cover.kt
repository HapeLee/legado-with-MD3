package io.legado.app.ui.widget.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun Cover(path: String?) {
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(68.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFEEEEEE)), // 灰色背景
        contentAlignment = Alignment.Center
    ) {
        if (path == null) {
            Icon(Icons.Default.Book, null, tint = Color.Gray)
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(path)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}