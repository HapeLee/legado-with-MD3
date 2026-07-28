package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import coil.compose.AsyncImage
import io.legado.app.R
import io.legado.app.domain.model.settings.ReadStyleItem
import io.legado.app.help.config.ReadStyleResolver
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookSheet
import io.legado.app.ui.book.read.ReadBookStyleConfig
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.fadingEdge
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.settingItem.TinySettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.text.AppText

// ========== Page 0: Global & Theme ==========

@Composable
fun GlobalThemePage(
    onToggleDayNight: () -> Unit,
    eyeProtectionEnabled: Boolean,
    onOpenBgTextConfig: (Int) -> Unit,
    onOpenTextTitle: () -> Unit,
    onOpenPaddingConfig: () -> Unit,
    onShareLayoutChange: (Boolean) -> Unit,
    onStyleSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onIntent: (ReadBookIntent) -> Unit,
    styleConfig: ReadBookStyleConfig = ReadBookStyleConfig(),
) {
    // Derive values directly from styleConfig (reactive state)
    val textSize = styleConfig.textSize
    val pageAnim = styleConfig.pageAnim
    val styleSelect = styleConfig.styleSelect
    val shareLayout = styleConfig.shareLayout
    val isNightTheme = LegadoTheme.isDark

    val configList = styleConfig.styleItems

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TinySliderSettingItem(
                title = stringResource(R.string.text_size),
                value = textSize.toFloat(),
                valueRange = 5f..50f,
                steps = 44,
                modifier = Modifier.weight(1f),
                onValueChange = { value ->
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TextSize(value.toInt())))
                },
            )
            NormalCard(
                onClick = onOpenTextTitle,
                modifier = Modifier
                    .height(56.dp)
                    .aspectRatio(1f),
                containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
                cornerRadius = 12.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = Icons.Default.TextFields,
                        contentDescription = stringResource(R.string.read_config_text_effects),
                        tint = LegadoTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Style section label + Day/Night
        NormalCard(
            containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
            cornerRadius = 12.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = stringResource(R.string.text_bg_style),
                        style = LegadoTheme.typography.titleSmallEmphasized
                    )
                    AppText(
                        text = stringResource(R.string.long_click_to_custom),
                        style = LegadoTheme.typography.labelSmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SmallTonalButton(
                    onClick = { onIntent(ReadBookIntent.ToggleEyeProtection) },
                    onLongClick = {
                        onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.EyeProtection))
                    },
                    selected = eyeProtectionEnabled,
                    icon = Icons.Default.Visibility,
                    contentColor = LegadoTheme.colorScheme.onSurfaceVariant,
                    containerColor = LegadoTheme.colorScheme.surfaceContainerHigh,
                    contentDescription = stringResource(R.string.eye_protection),
                )
                SmallTonalButton(
                    onClick = onToggleDayNight,
                    icon = if (isNightTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                    contentColor = LegadoTheme.colorScheme.onSurfaceVariant,
                    containerColor = LegadoTheme.colorScheme.surfaceContainerHigh,
                    selectedContainerColor = LegadoTheme.colorScheme.surfaceContainerHigh,
                    selectedContentColor = LegadoTheme.colorScheme.onSurfaceVariant,
                    contentDescription = stringResource(R.string.theme_mode),
                )
            }

            Spacer(Modifier.height(8.dp))

            // Style cards: [shareLayout] [cards...]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    tooltip = {
                        PlainTooltip(
                            containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
                            contentColor = LegadoTheme.colorScheme.onSurface,
                        ) {
                            AppText(
                                text = stringResource(R.string.share_layout),
                                style = LegadoTheme.typography.bodyMedium,
                            )
                        }
                    },
                    state = rememberTooltipState(),
                ) {
                    NormalCard(
                        onClick = {
                            val newShareLayout = !shareLayout
                            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ShareLayout(newShareLayout)))
                            onShareLayoutChange(newShareLayout)
                        },
                        modifier = Modifier
                            .width(40.dp)
                            .height(56.dp),
                        cornerRadius = 8.dp,
                        containerColor = if (shareLayout) {
                            LegadoTheme.colorScheme.secondaryContainer
                        } else {
                            LegadoTheme.colorScheme.surfaceContainerLow
                        },
                        border = BorderStroke(
                            1.dp,
                            LegadoTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ),
                        contentColor = if (shareLayout) LegadoTheme.colorScheme.onSecondaryContainer else null
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.GridView,
                                contentDescription = stringResource(R.string.share_layout),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                val styleListState = rememberLazyListState()

                // Auto-scroll to selected item (position as 2nd visible card)
                LaunchedEffect(styleSelect) {
                    styleListState.animateScrollToItem(maxOf(0, styleSelect - 1))
                }

                // Auto-scroll to newly added style config (skip initial composition)
                var previousSize by remember { mutableStateOf(configList.size) }
                LaunchedEffect(configList.size) {
                    if (configList.size > previousSize) {
                        styleListState.animateScrollToItem(configList.size)
                    }
                    previousSize = configList.size
                }

                LazyRow(
                    state = styleListState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                        .fadingEdge(styleListState),
                ) {
                    itemsIndexed(configList) { index, config ->
                        StyleCard(
                            config = config,
                            isSelected = styleSelect == index,
                            isNightTheme = isNightTheme,
                            onClick = {
                                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.StyleSelect(index)))
                                onStyleSelect(index)
                            },
                            onLongClick = {
                                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.StyleSelect(index)))
                                onStyleSelect(index)
                                onOpenBgTextConfig(index)
                            },
                        )
                    }
                    item {
                        NormalCard(
                            onClick = {
                                onIntent(ReadBookIntent.AddReadStyleConfig)
                            },
                            modifier = Modifier
                                .width(40.dp)
                                .height(56.dp),
                            containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        val pageAnimOptions = listOf(
            R.string.page_anim_cover,
            R.string.page_anim_slide,
            R.string.page_anim_simulation,
            R.string.page_anim_scroll,
            R.string.page_anim_fade,
            R.string.page_anim_none,
        )
        var showPageAnimMenu by remember { mutableStateOf(false) }
        val pageAnimEntries = pageAnimOptions.map { stringResource(it) }.toTypedArray()
        val pageAnimEntryValues = pageAnimOptions.indices.map { it.toString() }.toTypedArray()
        val currentPageAnimDisplay =
            pageAnimEntries.getOrNull(pageAnimEntryValues.indexOf(pageAnim.toString())) ?: ""

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TinySettingItem(
                    title = stringResource(R.string.page_anim),
                    modifier = Modifier.fillMaxWidth(),
                    trailingContent = {
                        TextCard(
                            cornerRadius = 8.dp,
                            horizontalPadding = 8.dp,
                            verticalPadding = 4.dp,
                            text = currentPageAnimDisplay,
                            backgroundColor = LegadoTheme.colorScheme.surfaceContainerLow,
                            contentColor = LegadoTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = { showPageAnimMenu = true },
                )
                RoundDropdownMenu(
                    expanded = showPageAnimMenu,
                    onDismissRequest = { showPageAnimMenu = false },
                ) { dismiss ->
                    pageAnimEntries.forEachIndexed { index, display ->
                        RoundDropdownMenuItem(
                            text = display,
                            onClick = {
                                ReadBook.book?.setPageAnim(-1)
                                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.PageAnim(index)))
                                dismiss()
                            },
                        )
                    }
                }
            }
            NormalCard(
                onClick = onOpenPaddingConfig,
                modifier = Modifier
                    .height(56.dp)
                    .aspectRatio(1f),
                containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
                cornerRadius = 12.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = Icons.Default.SpaceBar,
                        contentDescription = stringResource(R.string.padding),
                        tint = LegadoTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ========== Shared Components ==========

@Composable
fun StyleCard(
    config: ReadStyleItem,
    isSelected: Boolean,
    isNightTheme: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val mode = ReadStyleResolver.currentMode(isNightTheme)
    val bgType = when (mode) {
        ReadStyleResolver.ReadStyleMode.Day -> config.bgType
        ReadStyleResolver.ReadStyleMode.Night -> config.bgTypeNight
        ReadStyleResolver.ReadStyleMode.EInk -> config.bgTypeEInk
    }
    val bgValue = when (mode) {
        ReadStyleResolver.ReadStyleMode.Day -> config.bgValue
        ReadStyleResolver.ReadStyleMode.Night -> config.bgValueNight
        ReadStyleResolver.ReadStyleMode.EInk -> config.bgValueEInk
    }
    val bgColor = if (bgType == 0) {
        try {
            Color(bgValue.toColorInt())
        } catch (_: Exception) {
            LegadoTheme.colorScheme.surface
        }
    } else {
        LegadoTheme.colorScheme.surface
    }
    val textColor = Color(
        when (mode) {
            ReadStyleResolver.ReadStyleMode.Day -> config.textColor
            ReadStyleResolver.ReadStyleMode.Night -> config.textColorNight
            ReadStyleResolver.ReadStyleMode.EInk -> config.textColorEInk
        }
    )
    val name = config.name.ifBlank { stringResource(R.string.text_bg_style) }
    val bgPath = ReadStyleResolver.backgroundPath(bgType, bgValue)

    NormalCard(
        modifier = Modifier
            .width(44.dp)
            .height(56.dp),
        cornerRadius = 8.dp,
        containerColor = bgColor,
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bgPath != null) {
                AsyncImage(
                    model = bgPath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AppText(
                text = name,
                style = LegadoTheme.typography.labelSmall,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 8.dp),
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(12.dp)
                        .background(
                            color = LegadoTheme.colorScheme.surfaceContainerHigh
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = LegadoTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
