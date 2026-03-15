package io.legado.app.ui.widget.components.cover

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

@Composable
fun BookCoverWithProgress(
    name: String?,
    author: String?,
    path: String?,
    modifier: Modifier = Modifier,
    isUpdating: Boolean = false,
    badgeContent: (@Composable RowScope.() -> Unit)? = null,
    sourceOrigin: String? = null,
    onLoadFinish: (() -> Unit)? = null
) {
    Box(modifier = modifier) {
        BookCover(
            name = name,
            author = author,
            path = path,
            modifier = Modifier.fillMaxWidth(),
            badgeContent = badgeContent,
            sourceOrigin = sourceOrigin,
            onLoadFinish = onLoadFinish
        )

        if (isUpdating) {
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                strokeCap = StrokeCap.Round
            )
        }
    }
}
