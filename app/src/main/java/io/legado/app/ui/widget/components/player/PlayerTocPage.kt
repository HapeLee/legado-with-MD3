package io.legado.app.ui.widget.components.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.ImmutableList

/** 播放器目录/章节列表的通用条目模型。 */
@Stable
data class PlayerChapterUi(
    val index: Int,
    val title: String,
    val isVolume: Boolean,
    val tocLevel: Int,
)

/**
 * 播放器通用的卷/章节列表页：卷名标题 + 章节卡片，自动滚动到当前章节。
 */
@Composable
fun PlayerTocPage(
    chapters: ImmutableList<PlayerChapterUi>,
    currentIndex: Int,
    isPaused: Boolean,
    onSelect: (Int) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // 只在实际章节变化时滚动一次：chapters 列表实例可能随播放进度事件重建，
    // 若每次都在 LaunchedEffect 里滚动，用户浏览目录时会被反复拽回当前章节
    var lastScrollTarget by remember { mutableIntStateOf(-1) }
    LaunchedEffect(currentIndex, chapters) {
        val currentItem = chapters.indexOfFirst {
            !it.isVolume && it.index == currentIndex
        }
        if (currentItem >= 0) {
            val target = (currentItem - 2).coerceAtLeast(0)
            if (target != lastScrollTarget) {
                lastScrollTarget = target
                listState.scrollToItem(target)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (chapters.isEmpty()) {
            item {
                AppText(
                    text = stringResource(R.string.chapter_list_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    style = LegadoTheme.typography.bodyMedium,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            items(
                items = chapters,
                key = { chapter ->
                    if (chapter.isVolume) "player-volume-${chapter.index}"
                    else "player-chapter-${chapter.index}"
                },
                contentType = { chapter -> if (chapter.isVolume) "volume" else "chapter" },
            ) { chapter ->
                if (chapter.isVolume) {
                    AppText(
                        text = chapter.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = (chapter.tocLevel.coerceIn(0, 6) * 10).dp,
                                top = 12.dp,
                                bottom = 4.dp,
                            ),
                        style = LegadoTheme.typography.titleSmallEmphasized,
                        color = LegadoTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    val isCurrent = chapter.index == currentIndex
                    NormalCard(
                        onClick = { onSelect(chapter.index) },
                        cornerRadius = 12.dp,
                        containerColor = if (isCurrent) {
                            LegadoTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        } else {
                            LegadoTheme.colorScheme.surfaceVariant.copy(alpha = 0f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (chapter.tocLevel.coerceIn(0, 6) * 10).dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isCurrent) {
                                AppIcon(
                                    imageVector = if (isPaused) {
                                        Icons.Default.PlayArrow
                                    } else {
                                        Icons.Default.Pause
                                    },
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(16.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                AppText(
                                    text = chapter.title,
                                    style = if (isCurrent) {
                                        LegadoTheme.typography.bodyMediumEmphasized
                                    } else {
                                        LegadoTheme.typography.bodyMedium
                                    },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
