package io.legado.app.ui.book.read.sheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.legado.app.R
import io.legado.app.domain.model.settings.READ_STYLE_PRESET_COUNT
import io.legado.app.domain.model.settings.ReadStyleItem
import io.legado.app.help.config.ReadStyleResolver
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookMenuRoute
import io.legado.app.ui.book.read.ReadBookStyleConfig
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.ActionItem
import io.legado.app.ui.widget.components.CompactSearchField
import io.legado.app.ui.widget.components.SelectionBottomBar
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.reader.ScrollFabButtons
import io.legado.app.ui.widget.components.reader.rememberScrollFabState
import io.legado.app.ui.widget.components.reorderAccessibility
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

/** 管理模式的底栏是浮层：网格底部留这么多 contentPadding，最后一行能滚到它上方。 */
private val BottomBarReserved = 96.dp

/**
 * 顶行与下方列表之间的间距。**必须放在"固定高度内部"**（网格走 contentPadding、流式走外层
 * 定高里的 Spacer）：面板的高度上限只剩几 dp 余量，若在外层加 padding，面板会超上限裁掉网格底部。
 */
private val TopRowGap = 10.dp

/** 网格列数可选项（4 = 默认；当前选中的那个在菜单里打勾）。 */
private val GridColumnOptions = listOf(2, 3, 4, 5, 6)

/**
 * 阅读主题列表内容。宿主是菜单的 dialog-like 路由（ReadBookMenuRoute.ThemeList）：
 * 标题行、居中、宽度、圆角、背景（颜色/不透明度/模糊）全部由菜单面板提供，这里只管内容。
 *
 * **面板高度要恒定**：两种布局都用固定高度（网格钉死 height、流式外层限高 + 内滚），
 * 底栏做成浮层用 contentPadding/padding 让内容避让——面板是垂直居中的，一变矮就整窗上滑。
 *
 * 顶行（固定 32dp，和面板标题行的返回钮同高）：常驻搜索框（占位显示主题总数）
 * + 日夜 + 布局切换（点一下切到另一种布局，图标显示"将要切到"的那个）+ 三点菜单。
 * 网格布局 = 一行 4 个预览卡（fillMaxWidth + 44:56，背景图 FillBounds，会变形，只作用于预览）；
 * 流式布局 = 名字 chip（名字前是**该样式自己的文字色**实心圆点），一行能放几个放几个（FlowRow，无图）。
 * 普通态：点击 = 应用主题；长按 = 选中并进入背景文字配置。
 * 管理态（三点菜单「管理主题」进入）：顶行整排隐去，只留「✕ 已选中 N」；网格与流式**都支持勾选**
 * （勾选态 = 网格同款的圆底勾）与底栏（全选 / 反选 / 删除 / 导出 / 置顶置底），
 * **只有拖动排序在流式下不可用**（FlowRow 不是 lazy 容器、库不支持）。
 * 预设那 [READ_STYLE_PRESET_COUNT] 份钉死：不可拖、不可勾、不可删。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeListContent(
    onIntent: (ReadBookIntent) -> Unit,
    onToggleDayNight: () -> Unit,
    onOpenBgTextConfig: (Int) -> Unit,
    gridMaxHeight: Dp,
    /** 列表区实测高度（px）回调：宿主用它反推"非网格部分"的开销，让网格自动吃满面板（见 ReadBookMenuBar）。 */
    onGridMeasured: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    styleConfig: ReadBookStyleConfig = ReadBookStyleConfig(),
) {
    val isNightTheme = LegadoTheme.isDark
    val hapticFeedback = LocalHapticFeedback.current
    var searchQuery by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    /** ⋮ 菜单是否"翻页"到列数列表（false = 主菜单，true = 2..6 列数）。 */
    var themeMenuShowsColumns by remember { mutableStateOf(false) }
    /**
     * ⋮ 菜单宽度（实测冻结）：一级菜单先按内容自适应渲染一帧，量到自然宽度后锁给两级共用
     * ⇒ 原地翻页时不跳宽。0 = 还没测到（不约束宽度）。关闭菜单时清零，下次重开重新量。
     */
    var themeMenuWidth by remember { mutableStateOf(0.dp) }
    val menuDensity = LocalDensity.current
    var manageMode by remember { mutableStateOf(false) }
    // 布局切换：本次打开有效（不持久化 —— 新加偏好会碰架构守卫的基线）
    var flowMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    /** 删除二次确认：非 null = 待确认的选中项（点确认才真删、才清空选中）。 */
    var pendingDeleteIndices by remember { mutableStateOf<List<Int>?>(null) }
    /** 管理底栏实测高度（px）：给右下角悬浮箭头做抬升用，0 = 还没量到（用兜底值）。 */
    var manageBarHeightPx by remember { mutableStateOf(0) }

    val themes = styleConfig.styleItems
    val selectedIndices = themes.indices.filter { themes[it].id in selectedIds }
    val themeKeys = remember(themes) { buildThemeKeys(themes) }
    val customIndices = themes.indices.filter { it >= READ_STYLE_PRESET_COUNT }
    // 管理态必须全显示（筛选后拖动会让下标映射错乱）；其余情况搜索框有词就过滤
    val displayedThemes = themes.withIndex().filter { (_, config) ->
        manageMode ||
            searchQuery.isBlank() ||
            config.name.contains(searchQuery, ignoreCase = true)
    }
    // 流式在管理态也保留（只是没有拖动排序：FlowRow 不是 lazy 容器、库不支持）
    val useFlowLayout = flowMode

    // 悬浮按钮（定位/回顶/回底）：显隐、方向、延迟收起都在共享组件里；这里只提供滚动来源和三个动作。
    // 方向规矩（用户定）：上滑（去看更靠后的内容）⇒ 向下箭头、点了回底；下滑 ⇒ 向上箭头、点了回顶。
    val gridState = rememberLazyGridState()
    val flowScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val scrollFab = rememberScrollFabState(
        sourceKey = useFlowLayout,      // 切布局时重新订阅
        scrollingProvider = {
            if (useFlowLayout) flowScrollState.isScrollInProgress else gridState.isScrollInProgress
        },
        positionProvider = {
            if (useFlowLayout) {
                flowScrollState.value to 0
            } else {
                gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
            }
        },
    )

    // 导入/新建都会让列表长一份（单份 +1、批量 +N）：先清掉搜索词保证新条目可见，再滚到最后。
    // 网格按下标定位（现成）；流式只有像素目标，而 maxValue 要等新 chip 完成布局才是新值 ⇒ 等一帧。
    var previousThemeCount by remember { mutableStateOf(themes.size) }
    LaunchedEffect(themes.size) {
        if (themes.size > previousThemeCount) {
            searchQuery = ""
            withFrameNanos { }
            if (useFlowLayout) {
                flowScrollState.animateScrollTo(flowScrollState.maxValue)
            } else {
                gridState.animateScrollToItem(themes.lastIndex.coerceAtLeast(0))
            }
        }
        previousThemeCount = themes.size
    }

    fun toggleSelection(index: Int) {
        val id = themes[index].id
        if (id in selectedIds) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
    }

    /** 选择按主题身份保存，拖动或置顶/置底后仍指向同一份主题。 */
    fun moveSelectionToEdge(toTop: Boolean) {
        if (selectedIndices.isEmpty()) return
        onIntent(ReadBookIntent.MoveReadStylesToEdge(selectedIndices, toTop))
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // "主题 / 排版"分段：只切路由（替换栈顶），两个面板其实是同一个面板、几何由 morph 平滑过渡。
            // 管理态也保留（在最上面、不跟选择框打架；隐藏一整行会让列表高度跳一下）。
            CardTabRow(
                tabTitles = listOf(
                    stringResource(R.string.theme_list_tab_bg),
                    stringResource(R.string.compose_type),
                ),
                selectedTabIndex = 0,
                onTabSelected = { index ->
                    if (index == 1) {
                        onIntent(
                            ReadBookIntent.SwitchReadMenuRoute(ReadBookMenuRoute.TypographyConfig)
                        )
                    }
                },
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                if (manageMode) {
                    // 管理态：搜索框/日夜/布局切换/三点整排隐去，只留「✕ 已选中 N」
                    SmallTonalButton(
                        onClick = {
                            selectedIds.clear()
                            manageMode = false
                        },
                        icon = AppIcons.Close,
                        contentDescription = stringResource(R.string.close),
                    )
                    Text(
                        text = stringResource(
                            R.string.theme_list_selected_count,
                            selectedIndices.size,
                        ),
                        style = LegadoTheme.typography.labelMedium,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                } else {
                    CompactSearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = stringResource(
                            R.string.theme_list_search_count,
                            themes.size,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    // 与面板标题行返回钮同款：走 SmallTonalButton 默认色，圈圈一致
                    SmallTonalButton(
                        onClick = onToggleDayNight,
                        // 用 Outlined：笔画与项目自绘的线条保持一致（Filled 版偏重）
                        icon = if (isNightTheme) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                        contentDescription = stringResource(R.string.theme_mode),
                    )
                    SmallTonalButton(
                        onClick = { flowMode = !flowMode },
                        // 显示"将要切到"的那种布局
                        icon = if (flowMode) {
                            Icons.Outlined.GridView
                        } else {
                            Icons.AutoMirrored.Outlined.FormatListBulleted
                        },
                        contentDescription = stringResource(
                            if (flowMode) R.string.theme_list_layout_grid
                            else R.string.theme_list_layout_list
                        ),
                    )
                    Box {
                        SmallTonalButton(
                            onClick = {
                                themeMenuShowsColumns = false
                                menuExpanded = true
                            },
                            icon = AppIcons.MoreVert,
                            contentDescription = stringResource(R.string.more),
                        )
                        // 单菜单"原地翻页"：点「网格列数」后同一个菜单就地换成 2..6；
                        // 宽度锁死 ⇒ 翻页时菜单不会跳宽；点数字即生效并关闭。
                        RoundDropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = {
                                menuExpanded = false
                                themeMenuShowsColumns = false
                                themeMenuWidth = 0.dp      // 关掉就作废 ⇒ 下次重开按当前语言/字号重新量
                            },
                            modifier = Modifier
                                // 测到之前不约束 ⇒ 弹窗按内容自适应（收窄）；测到之后锁成同一个
                                // 宽度 ⇒ 翻到列数列表时不跳宽、勾也自然离数字更近
                                .then(
                                    if (themeMenuWidth > 0.dp) {
                                        Modifier.width(themeMenuWidth)
                                    } else {
                                        Modifier
                                    }
                                )
                                .onSizeChanged {
                                    if (themeMenuWidth == 0.dp) {
                                        themeMenuWidth =
                                            with(menuDensity) { it.width.toDp() }
                                    }
                                },
                        ) { dismiss ->
                            if (!themeMenuShowsColumns) {
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.theme_list_import_as_new),
                                    onClick = {
                                        dismiss()
                                        onIntent(ReadBookIntent.OpenReadStyleImportAsNew)
                                    },
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.theme_list_add_theme),
                                    onClick = {
                                        dismiss()
                                        // 和底部 [+] 完全同一个入口
                                        onIntent(ReadBookIntent.AddReadStyleConfig)
                                    },
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.theme_list_manage),
                                    onClick = {
                                        dismiss()
                                        manageMode = true
                                    },
                                )
                                // 网格专属的收尾项：只在网格模式出现（流式布局没有列数概念）
                                if (!useFlowLayout) {
                                    RoundDropdownMenuItem(
                                        text = stringResource(R.string.a11y_grid_columns),
                                        // 不关菜单，原地翻页到列数列表
                                        onClick = { themeMenuShowsColumns = true },
                                    )
                                }
                            } else {
                                val currentColumns = styleConfig.gridColumns.coerceIn(2, 6)
                                GridColumnOptions.forEach { columns ->
                                    RoundDropdownMenuItem(
                                        text = columns.toString(),
                                        isSelected = columns == currentColumns,
                                        onClick = {
                                            onIntent(ReadBookIntent.SetThemeGridColumns(columns))
                                            dismiss()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (useFlowLayout) {
                // 流式布局：名字 chip，一行能放几个放几个。外层限高 + 内滚 ⇒ 面板总高与网格一致；
                // 与顶行之间的间距用外层定高里的 Spacer（不吃面板高度预算）。
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(gridMaxHeight)
                        .onSizeChanged { onGridMeasured(it.height) },
                ) {
                    Spacer(Modifier.height(TopRowGap))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(flowScrollState),
                    ) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                // 管理底栏是浮层：给内容留出底部空间（放在固定高度内部，面板总高不变）
                                .padding(bottom = if (manageMode) BottomBarReserved else 0.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            displayedThemes.forEach { (index, config) ->
                                val isPreset = index < READ_STYLE_PRESET_COUNT
                                ThemeFlowChip(
                                    config = config,
                                    isNightTheme = isNightTheme,
                                    manageMode = manageMode,
                                    isPreset = isPreset,
                                    isChecked = selectedIndices.contains(index),
                                    isSelected = styleConfig.styleSelect == index,
                                    onClick = {
                                        if (manageMode) {
                                            if (!isPreset) toggleSelection(index)
                                        } else {
                                            onIntent(
                                                ReadBookIntent.UpdateConfig(
                                                    ConfigUpdate.StyleSelect(index)
                                                )
                                            )
                                        }
                                    },
                                    onLongClick = if (manageMode) {
                                        null
                                    } else {
                                        { onOpenBgTextConfig(index) }
                                    },
                                )
                            }
                            ThemeFlowChip(
                                config = null,
                                isNightTheme = isNightTheme,
                                manageMode = manageMode,
                                isPreset = false,
                                isChecked = false,
                                isSelected = false,
                                onClick = { onIntent(ReadBookIntent.AddReadStyleConfig) },
                                onLongClick = null,
                            )
                        }
                    }
                }
            } else {
                val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
                    // 只在自建样式区内重排；拖进预设区就拦在这里，条目自己弹回
                    if (manageMode &&
                        from.index >= READ_STYLE_PRESET_COUNT &&
                        to.index >= READ_STYLE_PRESET_COUNT
                    ) {
                        onIntent(ReadBookIntent.MoveReadStyleConfig(from.index, to.index))
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                }

                LazyVerticalGrid(
                    // 列数来自阅读设置（⋮ → 网格列数，持久化）；coerceIn 兜底防脏数据
                    columns = GridCells.Fixed(styleConfig.gridColumns.coerceIn(2, 6)),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxWidth()
                        // 惰性网格必须有界高（面板内容区不滚动），这里直接钉成固定高度
                        .height(gridMaxHeight)
                        // 实测法要的是"列表区高度" ⇒ onSizeChanged 必须在 padding 之前（否则少算 10dp）
                        .onSizeChanged { onGridMeasured(it.height) }
                        // 与顶行的间距用**视口内边距**：不跟滚动走 ⇒ 卡片滚到裁切边时恒定留出 TopRowGap。
                        // （放在 contentPadding 里会被一起滚走，卡片就会直接顶到裁切边 = "裁切边缘离顶行太近"）
                        .padding(top = TopRowGap),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        // 管理底栏是浮层：用内边距让内容避开，而不是占布局高度
                        bottom = if (manageMode) BottomBarReserved else 8.dp,
                    ),
                ) {
                    itemsIndexed(
                        items = displayedThemes,
                        key = { _, pair -> themeKeys.getOrElse(pair.index) { "theme-${pair.index}" } },
                    ) { _, pair ->
                        val index = pair.index
                        val config = pair.value
                        val isPreset = index < READ_STYLE_PRESET_COUNT
                        val canManage = manageMode && !isPreset
                        val isChecked = selectedIndices.contains(index)
                        // "正在使用"（与勾选无关的独立状态）：网格靠底部主色横条，流式靠外框
                        val isSelected = styleConfig.styleSelect == index
                        ReorderableItem(
                            state = reorderState,
                            key = themeKeys.getOrElse(index) { "theme-$index" },
                            enabled = canManage,
                        ) { isDragging ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .reorderAccessibility(
                                        index = index,
                                        itemCount = themes.size,
                                        enabled = canManage,
                                    ) { from, to ->
                                        if (to >= READ_STYLE_PRESET_COUNT) {
                                            onIntent(ReadBookIntent.MoveReadStyleConfig(from, to))
                                        }
                                    }
                                    .then(
                                        if (canManage) {
                                            Modifier.longPressDraggableHandle(
                                                onDragStarted = {
                                                    hapticFeedback.performHapticFeedback(
                                                        HapticFeedbackType.GestureThresholdActivate
                                                    )
                                                },
                                                onDragStopped = {
                                                    hapticFeedback.performHapticFeedback(
                                                        HapticFeedbackType.GestureEnd
                                                    )
                                                },
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .graphicsLayer { alpha = if (isDragging) 0.5f else 1f },
                                contentAlignment = Alignment.Center,
                            ) {
                                StyleCard(
                                    config = config,
                                    // 铁律：网格卡片**永不画线框**，选中/正在使用都不看框；
                                    // 日常靠底部主色横条，管理态连横条也隐藏、只靠右上角勾选徽章
                                    isSelected = isSelected && !manageMode,
                                    isNightTheme = isNightTheme,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(44f / 56f)
                                        .then(
                                            if (isPreset && manageMode) {
                                                Modifier.alpha(0.5f)
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    imageContentScale = ContentScale.FillBounds,
                                    // 铁律：网格卡片**永远不画线框**（v23-base 原版就是没有）——
                                    // 不管全局覆盖/管理态/是否正在使用：管理态靠右上角勾选徽章，日常靠底部主色横条
                                    onClick = {
                                        if (manageMode) {
                                            if (!isPreset) toggleSelection(index)
                                        } else {
                                            onIntent(
                                                ReadBookIntent.UpdateConfig(
                                                    ConfigUpdate.StyleSelect(index)
                                                )
                                            )
                                        }
                                    },
                                    onLongClick = if (manageMode) null else {
                                        { onOpenBgTextConfig(index) }
                                    },
                                )
                                if (manageMode && isPreset) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = LegadoTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(3.dp)
                                            .size(12.dp),
                                    )
                                }
                                if (manageMode && isChecked) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .size(14.dp)
                                            .background(
                                                // 管理态要高亮：柔和容器色实测看不清哪些被勾选，这一处必须用 primary
                                                LegadoTheme.colorScheme.primary,
                                                CircleShape,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = LegadoTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(10.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item(key = "add-style") {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            NormalCard(
                                onClick = { onIntent(ReadBookIntent.AddReadStyleConfig) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(44f / 56f),
                                cornerRadius = 8.dp,
                                containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = stringResource(R.string.add),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 悬浮按钮：滚动时出现 / 停手 1.5s 后收；底色柔和（secondaryContainer）。
        // 浮层不占布局高度 ⇒ 面板高度预算不变。管理态：只留"回顶/回底"，并整体抬到批量操作栏上方。
        val manageBarVisible = manageMode && selectedIndices.isNotEmpty()
        // 抬升量 = 操作栏实测布局高度 + 16dp（栏自身离底 8dp + 间距 8dp）；没量到前用兜底值，
        // 一帧后就收敛 ⇒ 不是"猜常数"。操作栏隐藏时抬升归零（箭头回原位）。
        val manageLiftTarget = if (manageBarVisible) {
            with(LocalDensity.current) {
                (if (manageBarHeightPx > 0) manageBarHeightPx else 72.dp.roundToPx()).toDp()
            } + 16.dp
        } else {
            0.dp
        }
        val manageLift by animateDpAsState(targetValue = manageLiftTarget, label = "ManageBarLift")
        // 定位"正在使用"的主题：流式没有下标定位；管理态不显示定位钮；搜索过滤后找不到也隐藏
        val locateIndex = displayedThemes.indexOfFirst { it.index == styleConfig.styleSelect }
        ScrollFabButtons(
            state = scrollFab,
            visible = true,
            onLocate = if (!useFlowLayout && !manageMode && locateIndex >= 0) {
                { scope.launch { gridState.animateScrollToItem(locateIndex) } }
            } else {
                null
            },
            onScrollTop = {
                scope.launch {
                    if (useFlowLayout) {
                        flowScrollState.animateScrollTo(0)
                    } else {
                        gridState.animateScrollToItem(0)
                    }
                }
            },
            onScrollBottom = {
                scope.launch {
                    if (useFlowLayout) {
                        flowScrollState.animateScrollTo(flowScrollState.maxValue)
                    } else {
                        gridState.animateScrollToItem(displayedThemes.size)
                    }
                }
            },
            locateContentDescription = stringResource(R.string.theme_list_locate_current),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 12.dp + manageLift),
        )

        // 管理态 + 有选中才出现（与 ListScaffold 的约定一致，"删除"因此天然可用）
        AnimatedVisibility(
            // 与上面"悬浮箭头抬升"共用同一个判断，保证两者不会走散
            visible = manageBarVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .zIndex(1f)
                // 面板已带导航栏内边距，这里抵消组件自带的窗口内边距，否则底栏会被再顶起一段
                .consumeWindowInsets(
                    WindowInsets.navigationBars
                        .union(WindowInsets.tappableElement)
                        .union(WindowInsets.ime)
                ),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            SelectionBottomBar(
                // 弹窗里的底栏整体缩到 0.85（其他界面用默认值，零影响）
                compact = true,
                // 实测操作栏的真实布局高度（含它自带的窗口内边距；scale 只缩绘制、不影响测量）
                // —— 给右下角悬浮箭头做抬升用，引擎无关
                modifier = Modifier.onSizeChanged { manageBarHeightPx = it.height },
                // 分享：打包选中的主题 → 直接调系统分享面板（不改数据，所以不清空选中）
                leadingAction = ActionItem(
                    text = stringResource(R.string.share),
                    icon = Icons.Default.Share,
                ) {
                    onIntent(ReadBookIntent.ShareReadStyleConfigs(selectedIndices.toList()))
                },
                onSelectAll = {
                    selectedIds.clear()
                    selectedIds.addAll(customIndices.map { themes[it].id })
                },
                onSelectInvert = {
                    val inverted = customIndices.filterNot { selectedIndices.contains(it) }
                    selectedIds.clear()
                    selectedIds.addAll(inverted.map { themes[it].id })
                },
                primaryAction = ActionItem(
                    text = stringResource(R.string.delete),
                    icon = Icons.Default.Delete,
                ) {
                    // 二次确认：先挂起，点"确认"才真删（也不在这里清空选中）
                    pendingDeleteIndices = selectedIndices.toList()
                },
                secondaryActions = listOf(
                    ActionItem(
                        text = stringResource(R.string.move_to_top),
                        icon = Icons.Default.VerticalAlignTop,
                    ) { moveSelectionToEdge(toTop = true) },
                    ActionItem(
                        text = stringResource(R.string.move_to_bottom),
                        icon = Icons.Default.VerticalAlignBottom,
                    ) { moveSelectionToEdge(toTop = false) },
                    ActionItem(
                        text = stringResource(R.string.export_str),
                        icon = Icons.Default.Upload,
                    ) {
                        // 导出不改数据，不清空选中，方便继续操作
                        onIntent(ReadBookIntent.ExportReadStyleConfigs(selectedIndices.toList()))
                    },
                ),
            )
        }

        // 删除二次确认（放调用处，不动共享底栏：书架/目录/搜索的删除仍是即时删）
        val pendingDelete = pendingDeleteIndices
        AppAlertDialog(
            show = pendingDelete != null,
            onDismissRequest = { pendingDeleteIndices = null },
            title = stringResource(R.string.delete),
            text = stringResource(R.string.theme_list_delete_confirm, pendingDelete?.size ?: 0),
            confirmText = stringResource(R.string.ok),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                pendingDelete?.let { onIntent(ReadBookIntent.DeleteReadStyleConfigs(it)) }
                selectedIds.clear()
                pendingDeleteIndices = null
            },
            // 取消：什么都不做，选中保留
            onDismiss = { pendingDeleteIndices = null },
        )
    }
}

/**
 * 流式布局的条目：只显示名字（不显示缩略图），宽度随文字长短变化，由 FlowRow 决定一行放几个。
 * [config] 为 null 表示那个"+"（新建）chip。
 *
 * 名字前的"圈/勾"完全照网格那套：未勾选 = 该样式**自己的文字色**实心圆点（按日/夜/墨水屏解析）；
 * 管理态选中 = 网格同款（primary 圆底 + 勾），非管理态下"正在使用"那一个也用它（chip 里只有一个徽章位，
 * 管理态仍按"被勾选"显示，避免两种状态同脸）。**"正在使用"另有外框一圈 primary**：chip 只有约 30dp 高，
 * 所以全局"覆盖卡片边框"关着时用 1dp（比网格卡细才不臃肿）；全局开着时颜色仍锁主色、粗细 = 全局 + 1dp，
 * 免得和全局框混在一起看不清（流式没有横条兜底，框不能被吃掉）。**管理态整圈框都不显示**（那个态只认勾选）。
 * 预设只降透明、点不动。
 */
@Composable
private fun ThemeFlowChip(
    config: ReadStyleItem?,
    isNightTheme: Boolean,
    manageMode: Boolean,
    isPreset: Boolean,
    isChecked: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    // 外框必须高亮：柔和容器色在 chip 底(surfaceContainerLow)上实测直接隐形 ⇒ 用 primary。
    // 管理态：只给"被勾选"的加框（没勾的不加，免得满屏是框）；日常：只给"正在使用"的加框。
    // 防覆盖照旧（靠下面三个参数）：全局没开 = 1dp 主色框；全局开了 = 全局 + 1dp 主色框。
    val ringColor = when {
        manageMode -> if (isChecked) LegadoTheme.colorScheme.primary else null
        isSelected -> LegadoTheme.colorScheme.primary
        else -> null
    }
    NormalCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.then(if (isPreset && manageMode) Modifier.alpha(0.5f) else Modifier),
        cornerRadius = 8.dp,
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
        // 全局覆盖关着：就这一圈 1dp（外框不占空间 ⇒ 不影响 chip 宽度、也不影响"一行放几个"）
        border = ringColor?.let { BorderStroke(1.dp, it) },
        // 颜色与"比全局粗 1dp"都只给"正在使用"那一个；普通 chip 全局开着时就是全局的粗细与颜色
        forcedBorderColor = ringColor,
        forcedBorderWidthExtra = if (ringColor != null) 1.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (config == null) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add),
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Box(
                    modifier = Modifier.size(14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // 管理态看"被勾选"、非管理态看"正在使用"（chip 里只有一个徽章位，两种状态不能同脸）
                    if (if (manageMode) isChecked else isSelected) {
                        // 配色分两档：管理态（批量勾选）要高亮 primary 才看得清；
                        // 日常"正在使用"用小徽章 + 柔和容器色（观感精致、不抢眼）
                        val badgeColor = if (manageMode) {
                            LegadoTheme.colorScheme.primary
                        } else {
                            LegadoTheme.colorScheme.secondaryContainer
                        }
                        val badgeContentColor = if (manageMode) {
                            LegadoTheme.colorScheme.onPrimary
                        } else {
                            LegadoTheme.colorScheme.onSecondaryContainer
                        }
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(badgeColor, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = badgeContentColor,
                                modifier = Modifier.size(10.dp),
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(readStyleTextColor(config, isNightTheme), CircleShape),
                        )
                    }
                }
                AppText(
                    text = config.name.ifBlank { stringResource(R.string.text_bg_style) },
                    style = LegadoTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 与 StyleCard 同一套取色：按当前 日/夜/墨水屏 解析该样式**自己的**文字色。 */
private fun readStyleTextColor(config: ReadStyleItem, isNightTheme: Boolean): Color {
    val mode = ReadStyleResolver.currentMode(isNightTheme)
    return Color(
        when (mode) {
            ReadStyleResolver.ReadStyleMode.Day -> config.textColor
            ReadStyleResolver.ReadStyleMode.Night -> config.textColorNight
            ReadStyleResolver.ReadStyleMode.EInk -> config.textColorEInk
        }
    )
}

/** 名称可以重复或重命名；主题身份与新增按钮使用不同的 key 空间。 */
internal fun buildThemeKeys(themes: List<ReadStyleItem>): List<String> =
    themes.map { "theme:${it.id}" }
