package io.legado.app.ui.main.homepage.modules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.GlassCard

@Composable
private fun rememberShimmerBrush(): Brush {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val offset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerOffset",
    )

    val colorScheme = LegadoTheme.colorScheme
    val colors = remember(colorScheme) {
        listOf(
            colorScheme.surfaceContainerHighest,
            colorScheme.surfaceContainerHigh,
            colorScheme.surfaceContainerHighest,
        )
    }

    return Brush.linearGradient(
        colors = colors,
        start = Offset(offset * 300f, 0f),
        end = Offset(offset * 300f + 300f, 0f),
    )
}

@Composable
private fun SkeletonBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(rememberShimmerBrush())
    )
}

// ── Waterfall skeleton ──

@Composable
fun WaterfallSkeletonItem(modifier: Modifier = Modifier) {
    GlassCard(
        modifier = modifier,
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                cornerRadius = 16.dp,
            )
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(14.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(10.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(10.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SkeletonBox(
                        modifier = Modifier
                            .width(40.dp)
                            .height(18.dp),
                        cornerRadius = 4.dp,
                    )
                    SkeletonBox(
                        modifier = Modifier
                            .width(52.dp)
                            .height(18.dp),
                        cornerRadius = 4.dp,
                    )
                }
            }
        }
    }
}

// ── Grid skeleton ──

@Composable
fun GridSkeletonItem(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(5f / 7f),
            cornerRadius = 4.dp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(12.dp),
        )
    }
}

@Composable
fun GridSkeletonModule(
    modifier: Modifier = Modifier,
    columns: Int = 3,
    rows: Int = 2,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(columns) {
                    GridSkeletonItem(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ── Banner skeleton ──

@Composable
fun BannerSkeletonModule(
    modifier: Modifier = Modifier,
    itemCount: Int = 6,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(itemCount) {
            SkeletonBox(
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(5f / 7f),
                cornerRadius = 12.dp,
            )
        }
    }
}

// ── Card skeleton ──

@Composable
fun CardSkeletonItem(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LegadoTheme.colorScheme.surfaceContainerLow),
    ) {
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(5f / 7f),
            cornerRadius = 0.dp,
        )
        Column(modifier = Modifier.padding(8.dp)) {
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(14.dp),
            )
        }
    }
}

@Composable
fun CardSkeletonModule(
    modifier: Modifier = Modifier,
    itemCount: Int = 5,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(itemCount) {
            CardSkeletonItem()
        }
    }
}

// ── Ranking skeleton ──

@Composable
fun RankingSkeletonModule(
    modifier: Modifier = Modifier,
    rows: Int = 5,
) {
    GlassCard(
        modifier = modifier,
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
        cornerRadius = 16.dp,
    ) {
        Column(modifier = Modifier.padding(top = 12.dp)) {
            repeat(rows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    SkeletonBox(
                        modifier = Modifier
                            .size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    SkeletonBox(
                        modifier = Modifier
                            .width(52.dp)
                            .aspectRatio(5f / 7f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        SkeletonBox(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(14.dp),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SkeletonBox(
                            modifier = Modifier
                                .fillMaxWidth(0.4f)
                                .height(10.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── GridRanking skeleton ──

@Composable
fun GridRankingSkeletonModule(
    modifier: Modifier = Modifier,
    rows: Int = 4,
) {
    GlassCard(
        modifier = modifier,
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
        cornerRadius = 20.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            repeat(rows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    SkeletonBox(
                        modifier = Modifier
                            .width(48.dp)
                            .aspectRatio(5f / 7f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SkeletonBox(
                        modifier = Modifier
                            .size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        SkeletonBox(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(14.dp),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SkeletonBox(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(10.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Dispatcher ──

@Composable
fun HomepageModuleSkeleton(
    type: HomepageModuleType,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    rows: Int = 2,
) {
    when (type) {
        HomepageModuleType.Waterfall -> {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(2) {
                    WaterfallSkeletonItem(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        HomepageModuleType.InfiniteGrid -> {
            GridSkeletonModule(
                modifier = modifier,
                columns = columns,
                rows = rows,
            )
        }

        HomepageModuleType.Grid -> {
            GridSkeletonModule(
                modifier = modifier,
                columns = columns,
                rows = rows,
            )
        }

        HomepageModuleType.Banner -> {
            BannerSkeletonModule(modifier = modifier)
        }

        HomepageModuleType.Card -> {
            CardSkeletonModule(modifier = modifier)
        }

        HomepageModuleType.Ranking -> {
            RankingSkeletonModule(modifier = modifier)
        }

        HomepageModuleType.GridRanking -> {
            GridRankingSkeletonModule(modifier = modifier)
        }

        HomepageModuleType.ButtonGroup -> {
            // ButtonGroup loads instantly (no network), no skeleton needed
        }

        HomepageModuleType.Unknown -> {}
    }
}
