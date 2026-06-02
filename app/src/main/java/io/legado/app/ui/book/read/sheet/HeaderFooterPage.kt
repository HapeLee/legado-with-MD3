package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyColorSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import io.legado.app.utils.getCompatColor
import kotlinx.coroutines.launch

// Color picker IDs
private const val COLOR_HEADER = 7
private const val COLOR_FOOTER = 8
private const val COLOR_DIVIDER = 9

@Composable
internal fun HeaderFooterPage(
    onOpenFontSelect: () -> Unit,
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

    // Global state
    var headerFontSize by remember { mutableIntStateOf(ReadBookConfig.headerFontSize) }

    var showColorPicker by remember { mutableStateOf(false) }
    var colorPickerId by remember { mutableIntStateOf(0) }
    var colorPickerInitial by remember { mutableIntStateOf(0) }

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
        snapshotFlow { pagerState.settledPage }.collect { selectedTab = it }
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
                scope.launch { pagerState.animateScrollToPage(index) }
            },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            when (page) {
                0 -> {
                    // Header tab
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
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
                    }
                }

                1 -> {
                    // Footer tab
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
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
                        Text(
                            text = stringResource(R.string.read_config_divider_line),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
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

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.text_typeface),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                        TinyClickableSettingItem(
                            title = stringResource(R.string.header_font),
                            description = stringResource(R.string.select_font),
                            imageVector = Icons.Default.TextFields,
                            onClick = onOpenFontSelect,
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

    // Color picker
    if (showColorPicker) {
        ColorPickerSheet(
            show = true,
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
    }
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
