package io.legado.app.ui.book.read.sheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.FontFolderState
import io.legado.app.ui.widget.components.FontSelectSheet
import io.legado.app.ui.widget.components.SectionTitle
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.pager.rememberPagerFlingPassThroughConnection
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyColorSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import io.legado.app.utils.getCompatColor
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// Color picker IDs
private const val COLOR_HEADER = 7
private const val COLOR_FOOTER = 8
private const val COLOR_DIVIDER = 9

@Composable
internal fun HeaderFooterPage(
    modifier: Modifier = Modifier,
    onIntent: (ReadBookIntent) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val tabTitles = listOf(
        stringResource(R.string.header),
        stringResource(R.string.footer),
        stringResource(R.string.general),
    )
    val pagerState = rememberPagerState(pageCount = { 3 })
    var selectedTab by remember { mutableIntStateOf(0) }
    var clickScrollCount by remember { mutableIntStateOf(0) }
    val childPagerNestedScrollConnection = rememberPagerFlingPassThroughConnection(
        state = pagerState,
        orientation = Orientation.Horizontal,
    )

    val pageHeights = remember { mutableStateMapOf<Int, Int>() }
    val animatedHeight by rememberPagerAnimatedHeight(pagerState, pageHeights)

    // Header state
    var headerMode by remember { mutableIntStateOf(ReadBookConfig.headerMode) }
    var headerLeft by remember { mutableIntStateOf(ReadBookConfig.tipHeaderLeft) }
    var headerMiddle by remember { mutableIntStateOf(ReadBookConfig.tipHeaderMiddle) }
    var headerRight by remember { mutableIntStateOf(ReadBookConfig.tipHeaderRight) }

    // Footer state
    var footerMode by remember { mutableIntStateOf(ReadBookConfig.footerMode) }
    var footerLeft by remember { mutableIntStateOf(ReadBookConfig.tipFooterLeft) }
    var footerMiddle by remember { mutableIntStateOf(ReadBookConfig.tipFooterMiddle) }
    var footerRight by remember { mutableIntStateOf(ReadBookConfig.tipFooterRight) }

    // Line toggles
    var showHeaderLine by remember { mutableStateOf(ReadBookConfig.showHeaderLine) }
    var showFooterLine by remember { mutableStateOf(ReadBookConfig.showFooterLine) }

    // Global state
    var headerFontSize by remember { mutableIntStateOf(ReadBookConfig.headerFontSize) }

    var showColorPicker by remember { mutableStateOf(false) }
    var colorPickerId by remember { mutableIntStateOf(0) }
    var colorPickerInitial by remember { mutableIntStateOf(0) }
    var showFontSelect by remember { mutableStateOf(false) }

    var expandHeaderPadding by remember { mutableStateOf(false) }
    var expandFooterPadding by remember { mutableStateOf(false) }

    val headerScrollState = rememberScrollState()
    val footerScrollState = rememberScrollState()

    LaunchedEffect(expandHeaderPadding, headerScrollState.maxValue) {
        if (expandHeaderPadding) {
            headerScrollState.scrollTo(headerScrollState.maxValue)
        }
    }

    LaunchedEffect(expandFooterPadding, footerScrollState.maxValue) {
        if (expandFooterPadding) {
            footerScrollState.scrollTo(footerScrollState.maxValue)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onIntent(ReadBookIntent.SaveReadStyleConfig)
        }
    }

    // Header padding state
    var headerPaddingTop by remember { mutableFloatStateOf(ReadBookConfig.headerPaddingTop.toFloat()) }
    var headerPaddingBottom by remember { mutableFloatStateOf(ReadBookConfig.headerPaddingBottom.toFloat()) }
    var headerPaddingLeft by remember { mutableFloatStateOf(ReadBookConfig.headerPaddingLeft.toFloat()) }
    var headerPaddingRight by remember { mutableFloatStateOf(ReadBookConfig.headerPaddingRight.toFloat()) }

    // Footer padding state
    var footerPaddingTop by remember { mutableFloatStateOf(ReadBookConfig.footerPaddingTop.toFloat()) }
    var footerPaddingBottom by remember { mutableFloatStateOf(ReadBookConfig.footerPaddingBottom.toFloat()) }
    var footerPaddingLeft by remember { mutableFloatStateOf(ReadBookConfig.footerPaddingLeft.toFloat()) }
    var footerPaddingRight by remember { mutableFloatStateOf(ReadBookConfig.footerPaddingRight.toFloat()) }

    val tipNames = remember { ReadBookConfig.tipNames }
    val tipValues = remember { ReadBookConfig.tipValues }

    fun clearRepeat(repeat: Int) {
        if (repeat == ReadBookConfig.tipNone) return
        if (headerLeft == repeat) {
            headerLeft = ReadBookConfig.tipNone
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipHeaderLeft(ReadBookConfig.tipNone)))
        }
        if (headerMiddle == repeat) {
            headerMiddle = ReadBookConfig.tipNone
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipHeaderMiddle(ReadBookConfig.tipNone)))
        }
        if (headerRight == repeat) {
            headerRight = ReadBookConfig.tipNone
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipHeaderRight(ReadBookConfig.tipNone)))
        }
        if (footerLeft == repeat) {
            footerLeft = ReadBookConfig.tipNone
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipFooterLeft(ReadBookConfig.tipNone)))
        }
        if (footerMiddle == repeat) {
            footerMiddle = ReadBookConfig.tipNone
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipFooterMiddle(ReadBookConfig.tipNone)))
        }
        if (footerRight == repeat) {
            footerRight = ReadBookConfig.tipNone
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipFooterRight(ReadBookConfig.tipNone)))
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (clickScrollCount == 0) {
                selectedTab = page
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        CardTabRow(
            tabTitles = tabTitles,
            selectedTabIndex = selectedTab,
            onTabSelected = { index ->
                selectedTab = index
                clickScrollCount++
                scope.launch {
                    try {
                        pagerState.animateScrollToPage(
                            page = index,
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                        )
                    } finally {
                        clickScrollCount = (clickScrollCount - 1).coerceAtLeast(0)
                    }
                }
            },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        HorizontalPager(
            state = pagerState,
            verticalAlignment = Alignment.Top,
            pageNestedScrollConnection = childPagerNestedScrollConnection,
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .pagerHeight(animatedHeight),
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        pageHeights[page] = size.height
                    }
            ) {
                when (page) {
                0 -> {
                    // Header tab
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .verticalScroll(headerScrollState),
                    ) {
                        TinySwitchSettingItem(
                            title = stringResource(R.string.showLine),
                            checked = showHeaderLine,
                            onCheckedChange = {
                                showHeaderLine = it
                                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ShowHeaderLine(it)))
                            },
                        )
                        val headerModes = ReadBookConfig.getHeaderModes(context)
                        TinyDropdownSettingItem(
                            title = stringResource(R.string.header),
                            selectedValue = headerMode.toString(),
                            displayEntries = headerModes.values.toTypedArray(),
                            entryValues = headerModes.keys.map { it.toString() }.toTypedArray(),
                            onValueChange = {
                                headerMode = it.toInt()
                                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.HeaderMode(headerMode)))
                            },
                        )
                        TipPositionDropdown(
                            label = stringResource(R.string.left),
                            value = headerLeft,
                            tipNames = tipNames,
                            tipValues = tipValues,
                            onValueChange = {
                                clearRepeat(it)
                                headerLeft = it
                                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipHeaderLeft(it)))
                            },
                        )
                        TipPositionDropdown(
                            label = stringResource(R.string.middle),
                            value = headerMiddle,
                            tipNames = tipNames,
                            tipValues = tipValues,
                            onValueChange = {
                                clearRepeat(it)
                                headerMiddle = it
                                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipHeaderMiddle(it)))
                            },
                        )
                        TipPositionDropdown(
                            label = stringResource(R.string.right),
                            value = headerRight,
                            tipNames = tipNames,
                            tipValues = tipValues,
                            onValueChange = {
                                clearRepeat(it)
                                headerRight = it
                                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipHeaderRight(it)))
                            },
                        )
                        TinyColorSettingItem(
                            title = stringResource(R.string.header_color),
                            colorValue = if (ReadBookConfig.tipHeaderColor != 0) {
                                ReadBookConfig.tipHeaderColor
                            } else {
                                ReadBookConfig.textColor
                            },
                            onClick = {
                                colorPickerId = COLOR_HEADER
                                colorPickerInitial = if (ReadBookConfig.tipHeaderColor != 0) {
                                    ReadBookConfig.tipHeaderColor
                                } else {
                                    ReadBookConfig.textColor
                                }
                                showColorPicker = true
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        TinyClickableSettingItem(
                            title = stringResource(R.string.padding),
                            description = stringResource(
                                R.string.padding_format,
                                headerPaddingTop.toInt(),
                                headerPaddingBottom.toInt(),
                                headerPaddingLeft.toInt(),
                                headerPaddingRight.toInt(),
                            ),
                            trailingContent = {
                                Icon(
                                    imageVector = if (expandHeaderPadding) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = LegadoTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            onClick = {
                                expandHeaderPadding = !expandHeaderPadding
                            },
                        )
                        AnimatedVisibility(
                            visible = expandHeaderPadding,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                PaddingSliders(
                                    top = headerPaddingTop, bottom = headerPaddingBottom,
                                    left = headerPaddingLeft, right = headerPaddingRight,
                                    onTopChange = {
                                        headerPaddingTop = it
                                        onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.HeaderPaddingTop(it.toInt())))
                                    },
                                    onBottomChange = {
                                        headerPaddingBottom = it
                                        onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.HeaderPaddingBottom(it.toInt())))
                                    },
                                    onLeftChange = {
                                        headerPaddingLeft = it
                                        onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.HeaderPaddingLeft(it.toInt())))
                                    },
                                    onRightChange = {
                                        headerPaddingRight = it
                                        onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.HeaderPaddingRight(it.toInt())))
                                    },
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // Footer tab
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .verticalScroll(footerScrollState),
                    ) {
                        TinySwitchSettingItem(
                            title = stringResource(R.string.showLine),
                            checked = showFooterLine,
                            onCheckedChange = {
                                showFooterLine = it
                                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ShowFooterLine(it)))
                            },
                        )
                        val footerModes = ReadBookConfig.getFooterModes(context)
                        TinyDropdownSettingItem(
                            title = stringResource(R.string.footer),
                            selectedValue = footerMode.toString(),
                            displayEntries = footerModes.values.toTypedArray(),
                            entryValues = footerModes.keys.map { it.toString() }.toTypedArray(),
                            onValueChange = {
                                footerMode = it.toInt()
                                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.FooterMode(footerMode)))
                            },
                        )
                        TipPositionDropdown(
                            label = stringResource(R.string.left),
                            value = footerLeft,
                            tipNames = tipNames,
                            tipValues = tipValues,
                            onValueChange = {
                                clearRepeat(it)
                                footerLeft = it
                                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipFooterLeft(it)))
                            },
                        )
                        TipPositionDropdown(
                            label = stringResource(R.string.middle),
                            value = footerMiddle,
                            tipNames = tipNames,
                            tipValues = tipValues,
                            onValueChange = {
                                clearRepeat(it)
                                footerMiddle = it
                                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipFooterMiddle(it)))
                            },
                        )
                        TipPositionDropdown(
                            label = stringResource(R.string.right),
                            value = footerRight,
                            tipNames = tipNames,
                            tipValues = tipValues,
                            onValueChange = {
                                clearRepeat(it)
                                footerRight = it
                                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipFooterRight(it)))
                            },
                        )
                        TinyColorSettingItem(
                            title = stringResource(R.string.footer_color),
                            colorValue = if (ReadBookConfig.tipFooterColor != 0) {
                                ReadBookConfig.tipFooterColor
                            } else {
                                ReadBookConfig.textColor
                            },
                            onClick = {
                                colorPickerId = COLOR_FOOTER
                                colorPickerInitial = if (ReadBookConfig.tipFooterColor != 0) {
                                    ReadBookConfig.tipFooterColor
                                } else {
                                    ReadBookConfig.textColor
                                }
                                showColorPicker = true
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        TinyClickableSettingItem(
                            title = stringResource(R.string.padding),
                            description = stringResource(
                                R.string.padding_format,
                                footerPaddingTop.toInt(),
                                footerPaddingBottom.toInt(),
                                footerPaddingLeft.toInt(),
                                footerPaddingRight.toInt(),
                            ),
                            trailingContent = {
                                Icon(
                                    imageVector = if (expandFooterPadding) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = LegadoTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            onClick = {
                                expandFooterPadding = !expandFooterPadding
                            },
                        )
                        AnimatedVisibility(
                            visible = expandFooterPadding,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                PaddingSliders(
                                    top = footerPaddingTop, bottom = footerPaddingBottom,
                                    left = footerPaddingLeft, right = footerPaddingRight,
                                    onTopChange = {
                                        footerPaddingTop = it
                                        onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.FooterPaddingTop(it.toInt())))
                                    },
                                    onBottomChange = {
                                        footerPaddingBottom = it
                                        onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.FooterPaddingBottom(it.toInt())))
                                    },
                                    onLeftChange = {
                                        footerPaddingLeft = it
                                        onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.FooterPaddingLeft(it.toInt())))
                                    },
                                    onRightChange = {
                                        footerPaddingRight = it
                                        onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.FooterPaddingRight(it.toInt())))
                                    },
                                )
                            }
                        }
                    }
                }

                2 -> {
                    // Global tab
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        SectionTitle(stringResource(R.string.read_config_divider_line))
                        TinyColorSettingItem(
                            title = stringResource(R.string.tip_divider_color),
                            colorValue = when (ReadBookConfig.tipDividerColor) {
                                -1 -> context.getCompatColor(R.color.divider)
                                0 -> ReadBookConfig.textColor
                                else -> ReadBookConfig.tipDividerColor
                            },
                            onClick = {
                                colorPickerId = COLOR_DIVIDER
                                colorPickerInitial = when (ReadBookConfig.tipDividerColor) {
                                    -1 -> context.getCompatColor(R.color.divider)
                                    0 -> ReadBookConfig.textColor
                                    else -> ReadBookConfig.tipDividerColor
                                }
                                showColorPicker = true
                            },
                        )

                        SectionTitle(stringResource(R.string.text_typeface))
                        TinyClickableSettingItem(
                            title = stringResource(R.string.header_font),
                            description = stringResource(R.string.select_font),
                            imageVector = Icons.Default.TextFields,
                            onClick = { showFontSelect = true },
                        )
                        TinySliderSettingItem(
                            title = stringResource(R.string.header_font_size),
                            value = headerFontSize.toFloat(),
                            valueRange = 0f..100f,
                            onValueChange = { value ->
                                headerFontSize = value.toInt()
                                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.HeaderFontSize(value.toInt())))
                                onIntent(ReadBookIntent.SaveReadStyleConfig)
                            },
                        )
                    }
                }
            }
        }
    }
}

    // Color picker
    ColorPickerSheet(
        show = showColorPicker,
        initialColor = colorPickerInitial,
        onDismissRequest = { showColorPicker = false },
        onColorSelected = { color ->
            when (colorPickerId) {
                COLOR_HEADER -> {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipHeaderColor(color)))
                }

                COLOR_FOOTER -> {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipFooterColor(color)))
                }

                COLOR_DIVIDER -> {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TipDividerColor(color)))
                }
            }
            showColorPicker = false
        },
    )

    // Font selector for header/footer
    val readSettingsRepository: ReadSettingsRepository = koinInject()
    val preferences by readSettingsRepository.preferences.collectAsStateWithLifecycle(
        initialValue = null
    )
    val fontFolderState = remember(preferences) {
        val pref = preferences
        if (pref == null) {
            FontFolderState.Loading
        } else {
            FontFolderState.Loaded(pref.fontFolder.takeIf { it.isNotEmpty() }?.toUri())
        }
    }
    val systemTypefaces = stringArrayResource(R.array.system_typefaces)

    FontSelectSheet(
        show = showFontSelect,
        title = stringResource(R.string.select_font),
        folderState = fontFolderState,
        selectedFontPath = ReadBookConfig.headerFont,
        onDismissRequest = { showFontSelect = false },
        onSelectFont = {
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.HeaderFont(it.uri.toString())))
            onIntent(ReadBookIntent.SaveReadStyleConfig)
            showFontSelect = false
        },
        onSelectSystemTypeface = {
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.HeaderFont("")))
            onIntent(ReadBookIntent.SaveReadStyleConfig)
            showFontSelect = false
        },
        onOpenFolderPicker = { /* handled by FontSelectSheet internally */ },
        systemTypefaces = systemTypefaces,
    )
}

@Composable
internal fun TipPositionDropdown(
    label: String,
    value: Int,
    tipNames: List<String>,
    tipValues: Array<Int>,
    onValueChange: (Int) -> Unit,
) {
    TinyDropdownSettingItem(
        title = label,
        selectedValue = value.toString(),
        displayEntries = tipNames.toTypedArray(),
        entryValues = tipValues.map { it.toString() }.toTypedArray(),
        onValueChange = { onValueChange(it.toInt()) },
    )
}
