package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleanHands
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Toc
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.PageAnim
import io.legado.app.data.entities.Book
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.MoreActionIds
import io.legado.app.ui.book.read.ReadBookButtonConfigItem
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookSheet
import io.legado.app.ui.book.read.ReadBookUiState
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.ConfigListEntry
import io.legado.app.ui.widget.components.ReorderableConfigList
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText

private data class MoreActionSpec(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val applicable: Boolean = true,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun ReaderMoreActionsSheet(
    show: Boolean,
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var editing by remember(show) { mutableStateOf(false) }
    fun dispatch(intent: ReadBookIntent) {
        onDismissRequest()
        onIntent(intent)
    }

    val specs = moreActionSpecs(state, onIntent, ::dispatch)
    val config = state.menuConfig.moreActionItems.ifEmpty {
        MoreActionIds.map { ReadBookButtonConfigItem(it, true) }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        contentPaddingEnabled = false,
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            ReaderBookHeader(book = state.book)
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (editing) {
            MoreActionsConfigContent(
                items = config,
                specs = specs,
                onCancel = { editing = false },
                onSave = {
                    onIntent(ReadBookIntent.SaveMoreActionsConfig(it))
                    editing = false
                },
            )
        } else {
            MoreActionsPager(
                actions = orderedVisibleActions(config, specs) + MoreActionSpec(
                    id = "configure_more_actions",
                    label = stringResource(R.string.more_actions_config),
                    icon = Icons.Default.Settings,
                    onClick = { editing = true },
                ),
                state = state,
                onIntent = onIntent,
                dispatch = ::dispatch,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

private fun orderedVisibleActions(
    config: List<ReadBookButtonConfigItem>,
    specs: List<MoreActionSpec>,
): List<MoreActionSpec> {
    val specMap = specs.associateBy { it.id }
    return config.mapNotNull { item ->
        specMap[item.id]?.takeIf { item.enabled && it.applicable }
    }
}

@Composable
private fun MoreActionsPager(
    actions: List<MoreActionSpec>,
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
    dispatch: (ReadBookIntent) -> Unit,
) {
    val pages = actions.chunked(8).ifEmpty { listOf(emptyList()) }
    val pagerState = rememberPagerState { pages.size }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val itemSize = (maxWidth - 56.dp) / 4
        val rowCount = if (actions.size > 4) 2 else 1
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(
                    itemSize * rowCount + if (rowCount == 2) 24.dp else 16.dp
                ),
        ) { page ->
            val pageItems = pages[page]
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pageItems.chunked(4).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowItems.forEach { action ->
                            ActionSquareHost(
                                action = action,
                                state = state,
                                onIntent = onIntent,
                                dispatch = dispatch,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionSquareHost(
    action: MoreActionSpec,
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
    dispatch: (ReadBookIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(action.id) { mutableStateOf(false) }
    val hasMore = action.id == "change_source" || action.id == "refresh"
    Box(modifier = modifier) {
        SquareAction(
            icon = action.icon,
            text = action.label,
            selected = action.selected,
            hasMore = hasMore,
            onClick = if (action.id == "image_style" || action.id == "page_anim") {
                { expanded = true }
            } else {
                action.onClick
            },
            onMoreClick = { expanded = true },
        )
        when (action.id) {
            "change_source" -> RoundDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) { dismiss ->
                RoundDropdownMenuItem(
                    text = stringResource(R.string.change_origin),
                    onClick = { dismiss(); dispatch(ReadBookIntent.MenuBookChangeSource) },
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.chapter_change_source),
                    onClick = { dismiss(); dispatch(ReadBookIntent.MenuChapterChangeSource) },
                )
            }

            "refresh" -> RoundDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) { dismiss ->
                listOf(
                    R.string.menu_refresh_dur to ReadBookIntent.MenuRefreshDur,
                    R.string.menu_refresh_after to ReadBookIntent.MenuRefreshAfter,
                    R.string.menu_refresh_all to ReadBookIntent.MenuRefreshAll,
                ).forEach { (label, intent) ->
                    RoundDropdownMenuItem(
                        text = stringResource(label),
                        onClick = { dismiss(); dispatch(intent) },
                    )
                }
            }

            "image_style" -> ImageStyleDropdown(
                expanded = expanded,
                currentStyle = state.book?.getImageStyle() ?: Book.imgStyleDefault,
                onDismissRequest = { expanded = false },
                onIntent = onIntent,
            )

            "page_anim" -> PageAnimDropdown(
                expanded = expanded,
                currentPageAnim = state.book?.getPageAnim() ?: -1,
                onDismissRequest = { expanded = false },
                onIntent = onIntent,
            )
        }
    }
}

@Composable
private fun SquareAction(
    icon: ImageVector,
    text: String,
    selected: Boolean,
    hasMore: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) LegadoTheme.colorScheme.secondaryContainer
                else LegadoTheme.colorScheme.surfaceContainerLow
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppIcon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )

            val space = if (hasMore) 6.dp else 8.dp
            Spacer(
                modifier = Modifier.height(space)
            )

            AppText(
                text = text,
                style = LegadoTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (hasMore) {
            HorizontalDivider(color = LegadoTheme.colorScheme.surfaceContainer)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clickable(onClick = onMoreClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = stringResource(R.string.more_actions),
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun MoreActionsConfigContent(
    items: List<ReadBookButtonConfigItem>,
    specs: List<MoreActionSpec>,
    onCancel: () -> Unit,
    onSave: (List<ReadBookButtonConfigItem>) -> Unit,
) {
    val meta = specs.associateBy { it.id }
    val entries = items.mapNotNull { item ->
        meta[item.id]?.let { spec ->
            ConfigListEntry(item.id, item.enabled, spec.icon, spec.label)
        }
    }

    ReorderableConfigList(
        initialEntries = entries,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        onDismiss = onCancel,
        onConfirm = { confirmed ->
            onSave(confirmed.map { ReadBookButtonConfigItem(it.id, it.enabled) })
        },
    )
}

@Composable
private fun moreActionSpecs(
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
    dispatch: (ReadBookIntent) -> Unit,
): List<MoreActionSpec> = listOf(
    MoreActionSpec(
        "change_source", stringResource(R.string.change_origin), Icons.Default.SwapHoriz,
        applicable = !state.isLocalBook, onClick = { dispatch(ReadBookIntent.MenuChangeSource) }),
    MoreActionSpec(
        "refresh", stringResource(R.string.menu_refresh), Icons.Default.Refresh,
        applicable = !state.isLocalBook, onClick = { dispatch(ReadBookIntent.MenuRefreshDur) }),
    MoreActionSpec(
        "download", stringResource(R.string.offline_cache), Icons.Default.CloudDownload,
        applicable = !state.isLocalBook,
        onClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Download)) }),
    MoreActionSpec(
        "toc_rule", stringResource(R.string.txt_toc_rule), Icons.Default.Toc,
        applicable = state.isLocalTxt, onClick = { dispatch(ReadBookIntent.MenuTocRegex) }),
    MoreActionSpec(
        "charset", stringResource(R.string.set_charset), Icons.Default.Translate,
        applicable = state.isLocalBook,
        onClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Charset)) }),
    MoreActionSpec(
        "edit_content", stringResource(R.string.edit_content), Icons.Default.Edit,
        onClick = { dispatch(ReadBookIntent.OpenContentEdit) }),
    MoreActionSpec(
        "add_bookmark", stringResource(R.string.bookmark_add), Icons.Default.Bookmark,
        onClick = { dispatch(ReadBookIntent.AddBookmark) }),
    MoreActionSpec(
        "text_processing", stringResource(R.string.text_processing), Icons.Default.FindReplace,
        onClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.TextProcessing)) }),
    MoreActionSpec(
        "highlight_rule", stringResource(R.string.highlight_rule_config), Icons.Default.Tune,
        onClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.HighlightRuleConfig)) }),
    MoreActionSpec(
        "reverse_content", stringResource(R.string.reverse_content), Icons.Default.SwapVert,
        onClick = { dispatch(ReadBookIntent.MenuReverseContent) }),
    MoreActionSpec(
        "re_segment", stringResource(R.string.re_segment), Icons.Default.Toc,
        selected = state.reSegment, onClick = { onIntent(ReadBookIntent.MenuReSegment) }),
    MoreActionSpec(
        "del_ruby", stringResource(R.string.del_ruby_tag), Icons.Default.CleanHands,
        applicable = state.isEpub, selected = state.delRubyTag,
        onClick = { onIntent(ReadBookIntent.MenuDelRubyTag) }),
    MoreActionSpec(
        "del_h", stringResource(R.string.del_h_tag), Icons.Default.CleanHands,
        applicable = state.isEpub, selected = state.delHTag,
        onClick = { onIntent(ReadBookIntent.MenuDelHTag) }),
    MoreActionSpec(
        "image_style", stringResource(R.string.image_style), Icons.Default.Image,
        onClick = {}),
    MoreActionSpec(
        "page_anim", stringResource(R.string.book_page_anim), Icons.Default.Animation,
        onClick = {}),
    MoreActionSpec(
        "simulated_reading", stringResource(R.string.simulated_reading), Icons.Default.AutoStories,
        onClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.SimulatedReading)) }),
    MoreActionSpec(
        "get_progress", stringResource(R.string.get_book_progress), Icons.Default.Sync,
        applicable = state.isReadingProgressSyncConfigured,
        onClick = { dispatch(ReadBookIntent.MenuGetProgress) }),
    MoreActionSpec(
        "cover_progress", stringResource(R.string.cover_book_progress), Icons.Default.Sync,
        applicable = state.isReadingProgressSyncConfigured,
        onClick = { dispatch(ReadBookIntent.MenuCoverProgress) }),
    MoreActionSpec(
        "bottom_button_config", stringResource(R.string.config_btn), Icons.Default.Settings,
        onClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.ToolButtonConfig)) }),
    MoreActionSpec(
        "log", stringResource(R.string.log), Icons.Default.BugReport,
        onClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.AppLog)) }),
)

@Composable
private fun ImageStyleDropdown(
    expanded: Boolean,
    currentStyle: String,
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    RoundDropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) { dismiss ->
        listOf(
            R.string.btn_default_s to Book.imgStyleDefault,
            R.string.image_style_full to Book.imgStyleFull,
            R.string.image_style_text to Book.imgStyleText,
            R.string.image_style_single to Book.imgStyleSingle,
        ).forEach { (label, style) ->
            RoundDropdownMenuItem(
                text = stringResource(label),
                isSelected = currentStyle == style,
                onClick = { dismiss(); onIntent(ReadBookIntent.MenuImageStyle(style)) },
            )
        }
    }
}

@Composable
private fun PageAnimDropdown(
    expanded: Boolean,
    currentPageAnim: Int,
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    val pageAnimOptions = listOf(
        R.string.btn_default_s to -1,
        R.string.page_anim_cover to PageAnim.coverPageAnim,
        R.string.page_anim_slide to PageAnim.slidePageAnim,
        R.string.page_anim_simulation to PageAnim.simulationPageAnim,
        R.string.page_anim_scroll to PageAnim.scrollPageAnim,
        R.string.page_anim_fade to PageAnim.fadePageAnim,
        R.string.page_anim_none to PageAnim.noAnim,
    )
    RoundDropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) { dismiss ->
        pageAnimOptions.forEach { (label, value) ->
            RoundDropdownMenuItem(
                text = stringResource(label),
                isSelected = currentPageAnim == value,
                onClick = {
                    ReadBook.book?.setPageAnim(value)
                    dismiss()
                    onIntent(ReadBookIntent.PageAnimChanged)
                },
            )
        }
    }
}
