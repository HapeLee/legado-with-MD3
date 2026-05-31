package io.legado.app.ui.book.read

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.book.read.sheet.PaddingConfigContent
import io.legado.app.ui.book.read.sheet.ReadAloudContent
import io.legado.app.ui.book.read.sheet.ReadStyleContent
import io.legado.app.ui.book.read.sheet.ReadStyleTextTitleContent
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppSlider
import io.legado.app.ui.widget.components.bookmark.BookmarkEditContent
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.divider.PillDivider
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import kotlin.math.roundToInt

/**
 * Compose replacement for ReadMenu — main reading menu overlay.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadBookMenuBar(
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
) {
    val context = LocalContext.current
    val currentRoute = state.menuState.currentRoute
    val dialogLikeRoute = currentRoute == ReadBookMenuRoute.PaddingConfig

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state.menuVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onIntent(ReadBookIntent.HideMenu) }
            )
        }

        // Top title bar
        AnimatedVisibility(
            visible = state.menuVisible && !dialogLikeRoute,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            MenuTitleBar(state = state, onIntent = onIntent)
        }

        // Bottom menu
        AnimatedVisibility(
            visible = state.menuVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReadBookMenuSurface(
                route = currentRoute,
                state = state,
                onIntent = onIntent,
                context = context,
            )
        }
    }
}

@Composable
private fun ReadBookMenuSurface(
    route: ReadBookMenuRoute,
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
    context: Context,
) {
    val expanded = route != ReadBookMenuRoute.Main
    val dialogLikeRoute = route == ReadBookMenuRoute.PaddingConfig
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    var surfaceHeightPx by remember { mutableIntStateOf(0) }
    val morphProgress by animateFloatAsState(
        targetValue = if (dialogLikeRoute) 1f else 0f,
        label = "ReadBookMenuMorph",
    )
    val maxHeight = with(density) {
        windowSize.height.toDp() * 0.6f
    }
    val screenWidth = with(density) { windowSize.width.toDp() }
    val dialogAvailableWidth = screenWidth - 48.dp
    val dialogWidth = if (dialogAvailableWidth < 560.dp) {
        dialogAvailableWidth
    } else {
        560.dp
    }
    val surfaceWidth = lerp(screenWidth, dialogWidth, morphProgress)
    val bottomTopCorner by animateDpAsState(
        targetValue = if (expanded) 24.dp else 0.dp,
        label = "ReadBookMenuCorner",
    )
    val corner = lerp(bottomTopCorner, 28.dp, morphProgress)
    val bottomCorner = lerp(0.dp, 28.dp, morphProgress)

    Surface(
        modifier = Modifier
            .width(surfaceWidth)
            .heightIn(max = maxHeight)
            .onSizeChanged { surfaceHeightPx = it.height }
            .offset {
                val liftPx = ((windowSize.height - surfaceHeightPx) / 2f) * morphProgress
                IntOffset(x = 0, y = -liftPx.roundToInt())
            },
        shape = RoundedCornerShape(
            topStart = corner,
            topEnd = corner,
            bottomStart = bottomCorner,
            bottomEnd = bottomCorner,
        ),
        color = LegadoTheme.colorScheme.surfaceContainer,
        contentColor = LegadoTheme.colorScheme.onSurface,
        tonalElevation = if (expanded) 6.dp else 0.dp,
        shadowElevation = if (expanded) 8.dp else 0.dp,
    ) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                (slideInVertically { it / 4 } + fadeIn())
                    .togetherWith(slideOutVertically { -it / 4 } + fadeOut())
                    .using(SizeTransform(clip = true))
            },
            label = "ReadBookMenuRoute",
        ) { targetRoute ->
            when (targetRoute) {
                ReadBookMenuRoute.Main -> {
                    MenuBottomBar(state = state, onIntent = onIntent, context = context)
                }

                ReadBookMenuRoute.ReadStyle -> {
                    ReadBookMenuRoutePage(
                        title = stringResource(R.string.read_config),
                        maxHeight = maxHeight,
                        onBack = { onIntent(ReadBookIntent.ReadMenuBack) },
                    ) {
                        ReadStyleContent(
                            onOpenPaddingConfig = {
                                onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.PaddingConfig))
                            },
                            onOpenMoreConfig = {
                                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.MoreConfig))
                            },
                            onOpenBgTextConfig = { index ->
                                onIntent(ReadBookIntent.OpenBgTextConfig(index))
                            },
                            onOpenTextTitle = {
                                onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.TextTitle))
                            },
                            onOpenFontSelect = {
                                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.FontSelect))
                            },
                            onToggleDayNight = {
                                onIntent(ReadBookIntent.ToggleDayNight)
                            },
                        )
                    }
                }

                ReadBookMenuRoute.PaddingConfig -> {
                    ReadBookMenuRoutePage(
                        title = stringResource(R.string.padding),
                        maxHeight = maxHeight,
                        scrollContent = true,
                        onBack = { onIntent(ReadBookIntent.ReadMenuBack) },
                    ) {
                        PaddingConfigContent()
                    }
                }

                ReadBookMenuRoute.TextTitle -> {
                    ReadBookMenuRoutePage(
                        title = stringResource(R.string.read_config_text_effects),
                        maxHeight = maxHeight,
                        onBack = { onIntent(ReadBookIntent.ReadMenuBack) },
                    ) {
                        ReadStyleTextTitleContent(
                            onOpenShadowSet = {
                                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.ShadowSet))
                            },
                            onOpenUnderlineConfig = {
                                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.UnderlineConfig))
                            },
                            onOpenRegexColor = {
                                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.RegexColorConfig))
                            },
                            onOpenFontSelect = {
                                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.FontSelect))
                            },
                        )
                    }
                }

                ReadBookMenuRoute.ReadAloud -> {
                    ReadBookMenuRoutePage(
                        title = stringResource(R.string.aloud_config),
                        maxHeight = maxHeight,
                        scrollContent = true,
                        onBack = { onIntent(ReadBookIntent.ReadMenuBack) },
                    ) {
                        ReadAloudContent(
                            onDismissRequest = { onIntent(ReadBookIntent.HideMenu) },
                            onOpenChapterList = {
                                onIntent(ReadBookIntent.HideMenu)
                                onIntent(ReadBookIntent.OpenChapterList)
                            },
                            onShowMainMenu = {
                                onIntent(ReadBookIntent.ReadMenuBack)
                            },
                            onStopAutoPage = { onIntent(ReadBookIntent.StopAutoPage) },
                            onShowReadAloudConfig = {
                                onIntent(ReadBookIntent.ShowReadAloudConfig)
                            },
                        )
                    }
                }

                is ReadBookMenuRoute.Bookmark -> {
                    ReadBookMenuRoutePage(
                        title = targetRoute.bookmark.chapterName,
                        maxHeight = maxHeight,
                        scrollContent = true,
                        onBack = { onIntent(ReadBookIntent.ReadMenuBack) },
                    ) {
                        BookmarkEditContent(
                            bookmark = targetRoute.bookmark,
                            onSave = { onIntent(ReadBookIntent.SaveBookmark(it)) },
                            onDelete = { onIntent(ReadBookIntent.DeleteBookmark(it)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadBookMenuRoutePage(
    title: String,
    maxHeight: androidx.compose.ui.unit.Dp,
    scrollContent: Boolean = false,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .animateContentSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallTonalButton(
                onClick = onBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack
            )
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                style = LegadoTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(48.dp))
        }

        if (scrollContent) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                content()
            }
        } else {
            content()
        }
    }
}

@Composable
private fun MenuTitleBar(
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
) {
    val bgColor = LegadoTheme.colorScheme.surfaceContainer
    val textColor = LegadoTheme.colorScheme.onSurface
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(top = 24.dp), // status bar padding
    ) {
        // Book name + overflow menu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.bookName,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onIntent(ReadBookIntent.OpenBookInfo) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                style = LegadoTheme.typography.titleLarge,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = textColor,
                    )
                }
                OverflowDropdownMenu(
                    state = state,
                    onIntent = onIntent,
                    expanded = expanded,
                    onDismiss = { expanded = false },
                )
            }
        }

        // Chapter name + source action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.chapterName,
                modifier = Modifier.weight(1f),
                style = LegadoTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (!state.isLocalBook && state.bookSource != null) {
                Text(
                    text = state.bookSource.bookSourceName,
                    modifier = Modifier
                        .clickable { onIntent(ReadBookIntent.OpenSourceEdit) }
                        .padding(start = 8.dp),
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun OverflowDropdownMenu(
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
    expanded: Boolean,
    onDismiss: () -> Unit,
) {
    RoundDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) { dismiss ->
        var imageStyleExpanded by remember { mutableStateOf(false) }

        // Source actions
        if (!state.isLocalBook) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.change_origin),
                onClick = { dismiss(); onIntent(ReadBookIntent.MenuChangeSource) },
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.chapter_change_source),
                onClick = { dismiss(); onIntent(ReadBookIntent.MenuChapterChangeSource) },
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.menu_refresh_dur),
                onClick = { dismiss(); onIntent(ReadBookIntent.MenuRefreshDur) },
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.menu_refresh_after),
                onClick = { dismiss(); onIntent(ReadBookIntent.MenuRefreshAfter) },
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.menu_refresh_all),
                onClick = { dismiss(); onIntent(ReadBookIntent.MenuRefreshAll) },
            )
        }

        // TXT
        if (state.isLocalTxt) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.txt_toc_rule),
                onClick = { dismiss(); onIntent(ReadBookIntent.MenuTocRegex) },
            )
        }

        // Local book
        if (state.isLocalBook) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.set_charset),
                onClick = {
                    dismiss()
                    onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Charset))
                },
            )
        }

        PillDivider()

        // Content operations
        RoundDropdownMenuItem(
            text = stringResource(R.string.bookmark_add),
            onClick = { dismiss(); onIntent(ReadBookIntent.AddBookmark) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.edit_content),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.ContentEdit))
            },
        )
        if (!state.isLocalBook) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.offline_cache),
                onClick = {
                    dismiss()
                    onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Download))
                },
            )
        }
        RoundDropdownMenuItem(
            text = stringResource(R.string.update_toc),
            onClick = { dismiss(); onIntent(ReadBookIntent.MenuUpdateToc) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.simulated_reading),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.SimulatedReading))
            },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.reverse_content),
            onClick = { dismiss(); onIntent(ReadBookIntent.MenuReverseContent) },
        )

        PillDivider()

        // Checkable items
        RoundDropdownMenuItem(
            text = stringResource(R.string.replace_rule_title),
            isSelected = state.useReplaceRule,
            onClick = { onIntent(ReadBookIntent.MenuEnableReplace) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.replace_rule_title_setting),
            onClick = { dismiss(); onIntent(ReadBookIntent.MenuSettingReplace) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.effective_replaces),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.EffectiveReplaces))
            },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.same_title_removed),
            isSelected = state.sameTitleRemoved,
            onClick = { onIntent(ReadBookIntent.MenuSameTitleRemoved) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.re_segment),
            isSelected = state.reSegment,
            onClick = { onIntent(ReadBookIntent.MenuReSegment) },
        )

        // EPUB
        if (state.isEpub) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.del_ruby_tag),
                isSelected = state.delRubyTag,
                onClick = { onIntent(ReadBookIntent.MenuDelRubyTag) },
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.del_h_tag),
                isSelected = state.delHTag,
                onClick = { onIntent(ReadBookIntent.MenuDelHTag) },
            )
        }

        PillDivider()

        // Config
        Box {
            RoundDropdownMenuItem(
                text = stringResource(R.string.image_style),
                onClick = { imageStyleExpanded = true },
            )
            RoundDropdownMenu(
                expanded = imageStyleExpanded,
                onDismissRequest = { imageStyleExpanded = false },
            ) { subDismiss ->
                RoundDropdownMenuItem(
                    text = stringResource(R.string.btn_default_s),
                    onClick = {
                        subDismiss()
                        onIntent(ReadBookIntent.MenuImageStyle(Book.imgStyleDefault))
                    },
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.image_style_full),
                    onClick = {
                        subDismiss()
                        onIntent(ReadBookIntent.MenuImageStyle(Book.imgStyleFull))
                    },
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.image_style_text),
                    onClick = {
                        subDismiss()
                        onIntent(ReadBookIntent.MenuImageStyle(Book.imgStyleText))
                    },
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.image_style_single),
                    onClick = {
                        subDismiss()
                        onIntent(ReadBookIntent.MenuImageStyle(Book.imgStyleSingle))
                    },
                )
            }
        }
        RoundDropdownMenuItem(
            text = stringResource(R.string.book_page_anim),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.PageAnim))
            },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.config_btn),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.ToolButtonConfig))
            },
        )

        // Progress sync
        if (state.isReadingProgressSyncConfigured) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.get_book_progress),
                onClick = { dismiss(); onIntent(ReadBookIntent.MenuGetProgress) },
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.cover_book_progress),
                onClick = { dismiss(); onIntent(ReadBookIntent.MenuCoverProgress) },
            )
        }

        PillDivider()

        RoundDropdownMenuItem(
            text = stringResource(R.string.log),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.AppLog))
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MenuBottomBar(
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
    context: Context,
) {
    val bgColor = LegadoTheme.colorScheme.surfaceContainer
    val sliderProgress = state.seekProgress.toFloat()
    val sliderMax = state.seekMax.toFloat().coerceAtLeast(1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(bottom = 16.dp),
    ) {
        // Seek bar row: prev + slider + next
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onIntent(ReadBookIntent.PrevChapter) }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous chapter",
                )
            }

            AppSlider(
                value = sliderProgress,
                onValueChange = { /* continuous update */ },
                onValueChangeFinished = {
                    val behavior = AppConfig.progressBarBehavior
                    if (behavior == "page") {
                        onIntent(ReadBookIntent.SkipToPage(sliderProgress.toInt()))
                    } else {
                        onIntent(ReadBookIntent.SeekToChapter(sliderProgress.toInt()))
                    }
                },
                valueRange = 0f..sliderMax,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = { onIntent(ReadBookIntent.NextChapter) }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next chapter",
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Tool buttons
        val toolButtons = remember { loadToolButtons(context, onIntent) }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            toolButtons.forEach { button ->
                ToolButtonItem(button = button, state = state)
            }
        }
    }
}

@Composable
private fun ToolButtonItem(
    button: ToolButtonDef,
    state: ReadBookUiState,
) {
    val iconTint = LegadoTheme.colorScheme.onSurface
    val badgeCount = when (button.id) {
        "replace_badge" -> state.effectiveReplaceCount
        else -> 0
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { button.onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Box {
            Icon(
                painter = painterResource(button.iconRes),
                contentDescription = button.description,
                modifier = Modifier.size(24.dp),
                tint = iconTint,
            )
            if (badgeCount > 0) {
                Text(
                    text = badgeCount.toString(),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(
                            LegadoTheme.colorScheme.error,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    style = LegadoTheme.typography.labelSmall,
                    color = LegadoTheme.colorScheme.onError,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = button.description,
            style = LegadoTheme.typography.labelSmall,
            color = iconTint.copy(alpha = 0.7f),
        )
    }
}

private data class ToolButtonDef(
    val id: String,
    val iconRes: Int,
    val description: String,
    val onClick: () -> Unit,
)

private fun loadToolButtons(
    context: Context,
    onIntent: (ReadBookIntent) -> Unit,
): List<ToolButtonDef> {
    val allButtons = listOf(
        ToolButtonDef("search", R.drawable.ic_search, "搜索") {
            onIntent(ReadBookIntent.OpenSearch(null))
        },
        ToolButtonDef("catalog", R.drawable.ic_toc, "目录") {
            onIntent(ReadBookIntent.OpenChapterList)
        },
        ToolButtonDef("read_aloud", R.drawable.ic_read_aloud, "朗读") {
            onIntent(ReadBookIntent.ToggleReadAloud)
        },
        ToolButtonDef("setting", R.drawable.ic_settings, "设置") {
            onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.ReadStyle))
        },
        ToolButtonDef("addBookmark", R.drawable.ic_bookmark, "书签") {
            onIntent(ReadBookIntent.AddBookmark)
        },
        ToolButtonDef("theme", R.drawable.ic_brightness, "日夜") {
            AppConfig.isNightTheme = !AppConfig.isNightTheme
        },
        ToolButtonDef("prev_chapter", R.drawable.ic_previous, "上一章") {
            onIntent(ReadBookIntent.PrevChapter)
        },
        ToolButtonDef("next_chapter", R.drawable.ic_next, "下一章") {
            onIntent(ReadBookIntent.NextChapter)
        },
        ToolButtonDef("replace", R.drawable.ic_find_replace, "替换") {
            onIntent(ReadBookIntent.ChangeReplaceRule(true))
        },
        ToolButtonDef("auto_page", R.drawable.ic_auto_page, "自动翻页") {
            onIntent(ReadBookIntent.ToggleAutoPage)
        },
        ToolButtonDef("translate", R.drawable.ic_translate, "翻译") {
            onIntent(ReadBookIntent.ToggleTranslation)
        },
    )

    val prefs = context.getSharedPreferences("tool_button_config", Context.MODE_PRIVATE)
    val str = prefs.getString("tool_buttons", null)
    val savedList = str?.split(";")?.mapNotNull {
        val parts = it.split(",")
        if (parts.size == 2) parts[0] to parts[1].toBoolean() else null
    } ?: emptyList()

    val allMap = allButtons.associateBy { it.id }

    return if (savedList.isNotEmpty()) {
        val result = mutableListOf<ToolButtonDef>()
        savedList.forEach { (id, enabled) ->
            if (enabled) allMap[id]?.let { result.add(it) }
        }
        // Add any buttons not in saved config
        allButtons.forEach { btn ->
            if (savedList.none { it.first == btn.id }) {
                result.add(btn)
            }
        }
        result
    } else {
        allButtons.take(5)
    }
}
