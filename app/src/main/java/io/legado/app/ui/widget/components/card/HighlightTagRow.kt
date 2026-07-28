package io.legado.app.ui.widget.components.card

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.ui.book.info.HighlightedTag
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.text.AnimatedTextLine

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HighlightTagRow(
    tags: List<HighlightedTag>,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return

    if (tags.size == 1) {
        HighlightTagItem(tag = tags[0])
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tags.forEachIndexed { index, tag ->
                if (index > 0) {
                    VerticalDivider(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .fillMaxHeight(0.5f),
                        thickness = 0.5.dp,
                        color = LegadoTheme.colorScheme.outlineVariant,
                    )
                }
                HighlightTagItem(
                    tag = tag,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HighlightTagItem(
    tag: HighlightedTag,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        tag.title?.let {
            AnimatedTextLine(
                text = it,
                style = LegadoTheme.typography.labelSmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
        }

        val label = tag.matchedLabels.joinToString(" · ")
        AnimatedTextLine(
            text = label,
            modifier = Modifier.basicMarquee(),
            style = LegadoTheme.typography.titleMediumEmphasized,
            color = LegadoTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}
