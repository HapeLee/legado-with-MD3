package io.legado.app.feature.manga.translation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.text.AppText

@Composable
fun MangaTranslationStatus(
    state: MangaTranslationUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    if (!state.busy && state.error == null) return
    val progress = stringResource(
        R.string.feature_manga_translation_page_progress,
        state.done,
        state.total,
    )
    val text = if (state.error != null) {
        val failed = stringResource(R.string.feature_manga_translation_footer_failed)
        if (state.total > 0) "$failed · $progress" else failed
    } else progress
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.manga_translation_title),
                onClick = onClick,
            ),
        contentAlignment = when (horizontalAlignment) {
            Alignment.End -> Alignment.CenterEnd
            Alignment.CenterHorizontally -> Alignment.Center
            else -> Alignment.CenterStart
        },
    ) {
        AppText(
            text = text,
            color = Color.White,
            style = LegadoTheme.typography.labelSmall.copy(
                shadow = Shadow(Color.Black.copy(alpha = 0.78f), Offset(1.5f, 1.5f), 3f),
            ),
        )
    }
}
