package io.legado.app.ui.main.homepage.modules

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.book.SearchBookTagChip
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.text.AppText

/**
 * 瀑布流单项组件
 * 建议直接在 LazyVerticalStaggeredGrid 的 items 中使用，以获得最佳回收性能
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun WaterfallItem(
    book: SearchBook,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
) {
    GlassCard(
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            CoilBookCover(
                name = book.name,
                author = book.author,
                path = book.coverUrl,
                radius = 16.dp,
                sourceOrigin = book.origin,
                modifier = Modifier
                    .fillMaxWidth(),
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                sharedCoverKey = sharedCoverKey
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 8.dp)
            ) {
                AppText(
                    text = book.name,
                    style = LegadoTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                val subTitle = buildString {
                    if (book.author.isNotBlank()) append(book.author)
                    val kind = book.kind?.split(",")?.firstOrNull()
                    if (!kind.isNullOrBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(kind)
                    }
                }
                if (subTitle.isNotBlank()) {
                    AppText(
                        text = subTitle,
                        style = LegadoTheme.typography.labelSmall,
                        color = LegadoTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                val intro = book.intro?.replace("\\s+".toRegex(), " ")
                if (!intro.isNullOrBlank()) {
                    AppText(
                        text = intro,
                        style = LegadoTheme.typography.labelSmallEmphasized,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                val kinds = book.getKindList()
                if (kinds.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        kinds.forEach { kind ->
                            SearchBookTagChip(
                                text = kind,
                                color = LegadoTheme.colorScheme.surfaceContainerHigh
                            )
                        }
                    }
                }
            }
        }
    }
}
