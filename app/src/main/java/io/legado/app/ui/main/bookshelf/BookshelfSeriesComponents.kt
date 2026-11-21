package io.legado.app.ui.main.bookshelf

import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.domain.model.settings.BookshelfSettings
import io.legado.app.ui.book.group.groupCoverFileExtension
import io.legado.app.ui.config.themeConfig.TagColorPair
import io.legado.app.ui.main.bookCoverSharedElementKey
import io.legado.app.ui.theme.BookshelfCoverInnerInset
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.isMiuixHyperOs4Enabled
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menu.AppAction
import io.legado.app.ui.widget.components.menu.AppActionGroup
import io.legado.app.ui.widget.components.menu.AppActionMenu
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.externalFiles
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.FileOutputStream

/** BookGroupUi 只用于复用分组封面/文字排版，系列不是真实分组，这里给一个稳定的伪 id。 */
private const val SERIES_FAKE_GROUP_ID = Long.MIN_VALUE

/**
 * 书架上的折叠系列：视觉上等价一个分组封面，占一个格子。
 * 点击在书架内就地展开，长按出管理菜单。
 */
@Composable
internal fun BookshelfSeriesCard(
    settings: BookshelfSettings,
    series: BookshelfEntry.Series,
    /** 已剔除锁定私密书的预览封面，由调用方按私密状态过滤 */
    previewBooks: List<BookUiItem>,
    layoutMode: Int,
    gridStyle: Int,
    immersive: Boolean,
    isCompact: Boolean,
    titleSmallFont: Boolean,
    titleCenter: Boolean,
    titleMaxLines: Int,
    coverShadow: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onPickCover: () -> Unit,
    onResetCover: () -> Unit,
    onRename: () -> Unit,
    onManageMembers: () -> Unit,
    onDissolve: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val groupUi = remember(series.name, series.coverPath) {
        BookGroupUi(
            groupId = SERIES_FAKE_GROUP_ID,
            groupName = series.name,
            cover = series.coverPath,
            order = 0,
            enableRefresh = true,
            show = true,
            bookSort = 0,
            isPrivate = false,
        )
    }
    val countText = seriesCountText(series.progress)

    Box(modifier = modifier) {
        if (layoutMode == 0) {
            BookGroupItemList(
                settings = settings,
                group = groupUi,
                previewBooks = previewBooks,
                countText = countText,
                isCompact = isCompact,
                titleMaxLines = titleMaxLines,
                coverShadow = coverShadow,
                onClick = onClick,
                onLongClick = { menuExpanded = true },
            )
        } else {
            BookGroupItemGrid(
                settings = settings,
                group = groupUi,
                previewBooks = previewBooks,
                countText = countText,
                gridStyle = gridStyle,
                immersive = immersive,
                titleSmallFont = titleSmallFont,
                titleCenter = titleCenter,
                titleMaxLines = titleMaxLines,
                coverShadow = coverShadow,
                onClick = onClick,
                onLongClick = { menuExpanded = true },
            )
        }
        // 长按走同一套「四引擎分流」的命令菜单：受控展开，锚点就是整张卡片。
        // 菜单组件常驻、只切 embeddedExpanded，首帧才有已测量的锚点可用。
        AppActionMenu(
            groups = seriesActionGroups(
                hasCustomCover = series.coverPath != null,
                canCollapse = expanded,
            ),
            onAction = { id ->
                menuExpanded = false
                when (id) {
                    SeriesActionId.Collapse -> onClick()
                    SeriesActionId.PickCover -> onPickCover()
                    SeriesActionId.ResetCover -> onResetCover()
                    SeriesActionId.Rename -> onRename()
                    SeriesActionId.Manage -> onManageMembers()
                    SeriesActionId.Dissolve -> onDissolve()
                }
            },
            modifier = Modifier.matchParentSize(),
            embeddedExpanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            trigger = {},
        )
    }
}

/** 系列管理命令的稳定 ID：折叠卡长按菜单与抽屉溢出菜单共用同一份。 */
private object SeriesActionId {
    const val Collapse = "series_collapse"
    const val PickCover = "series_pick_cover"
    const val ResetCover = "series_reset_cover"
    const val Rename = "series_rename"
    const val Manage = "series_manage"
    const val Dissolve = "series_dissolve"
}

/**
 * 系列管理菜单的分组数据。
 *
 * [canCollapse] 只对折叠卡长按需要；抽屉里已经有固定的收起图标，不再重复一项。
 * 解散标成 destructive，四套引擎都会给出对应的危险色。
 */
@Composable
private fun seriesActionGroups(
    hasCustomCover: Boolean,
    canCollapse: Boolean,
): ImmutableList<AppActionGroup> = persistentListOf(
    AppActionGroup(
        id = "series_actions",
        actions = buildList {
            if (canCollapse) {
                add(
                    AppAction(
                        id = SeriesActionId.Collapse,
                        label = stringResource(R.string.bookshelf_series_collapse),
                    )
                )
            }
            add(
                AppAction(
                    id = SeriesActionId.PickCover,
                    label = stringResource(R.string.bookshelf_series_pick_cover),
                )
            )
            if (hasCustomCover) {
                add(
                    AppAction(
                        id = SeriesActionId.ResetCover,
                        label = stringResource(R.string.bookshelf_series_reset_cover),
                    )
                )
            }
            add(
                AppAction(
                    id = SeriesActionId.Rename,
                    label = stringResource(R.string.bookshelf_series_rename),
                )
            )
            add(
                AppAction(
                    id = SeriesActionId.Manage,
                    label = stringResource(R.string.bookshelf_series_manage),
                )
            )
            add(
                AppAction(
                    id = SeriesActionId.Dissolve,
                    label = stringResource(R.string.bookshelf_series_dissolve),
                    destructive = true,
                )
            )
        }.toImmutableList()
    )
)

/**
 * 点击系列后在书架内就地展开的抽屉：独占一整行，
 * 上下各是一条操作条（上沿：系列名/进度/收起；下沿：管理动作），
 * 由这两条操作条承担「书架 ⇄ 抽屉」的视觉分割，中间成员区不留任何额外内边距。
 *
 * 成员区完全复用书架的单本卡片，并按书架真实列数分行，因此网格/列表、每行列数、
 * 网格样式、沉浸、标题字号与行数、封面阴影、未读角标、私密脱敏都跟随书架当前布局，
 * 展开后的卡片与书架单本同宽同起点。
 *
 * 不做嵌套滚动：成员按当前列数分行铺开，由外层书架网格滚动。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookshelfSeriesPanel(
    settings: BookshelfSettings,
    customTagColors: ImmutableList<TagColorPair>,
    series: BookshelfEntry.Series,
    /** 固定列数时使用（列表模式的每行列数也在这里）；[adaptiveColumns] 为 true 时忽略 */
    columns: Int,
    /** 列数由封面宽度推出（网格关闭「固定列数」），需要在面板内按可用宽度重算 */
    adaptiveColumns: Boolean,
    /** 自适应列数时的单元格最小宽度（封面宽 + 左右内边距），单位 dp */
    minCellWidthDp: Float,
    /** 书架当前网格间距；成员之间的水平/垂直间距都取它 */
    spacing: Dp,
    /**
     * 书架网格内容区的左右内边距。抽屉壳（上下操作条与分割线）向两侧各铺出这么多，
     * 从而横贯到屏幕边；成员区再缩回同样的量，与书架卡片保持同一列宽/同一起点。
     */
    edgeInset: Dp,
    layoutMode: Int,
    gridStyle: Int,
    immersive: Boolean,
    isCompact: Boolean,
    titleSmallFont: Boolean,
    titleCenter: Boolean,
    titleMaxLines: Int,
    coverShadow: Boolean,
    isLocked: (BookUiItem) -> Boolean,
    isUpdating: (BookUiItem) -> Boolean,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    /**
     * 共享元素来源标识（与书架单本同一口径）。抽屉里的成员必须用和书架卡片完全相同的
     * 封面共享键，否则进出详情页/阅读器时封面没有转场——键不同就等于两根互不相识的节点。
     */
    sharedCoverSourceId: String,
    onBookClick: (BookUiItem, sharedCoverKey: String) -> Unit,
    onBookLongClick: (BookUiItem, sharedCoverKey: String) -> Unit,
    onCollapse: () -> Unit,
    onPickCover: () -> Unit,
    onResetCover: () -> Unit,
    onRename: () -> Unit,
    onManageMembers: () -> Unit,
    onDissolve: () -> Unit,
) {
    val progressText = seriesProgressText(series.progress)
    val collapseLabel = stringResource(R.string.bookshelf_series_collapse)
    val barColor = LegadoTheme.colorScheme.surfaceContainerLow
    val accent = LegadoTheme.colorScheme.primary
    // 顶部条的内边距就按书架网格的内容边距来：壳已经铺出 edgeInset，
    // 这里再补上封面自身那 4dp，标题与按钮容器就都落在封面这条线上。
    val barHorizontalPadding = edgeInset + BookshelfCoverInnerInset
    // 受控菜单：OS4 下 ⋮ 是原生玻璃按钮，自己持有可点节点，弹层只能由外部驱动开合
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 抽屉壳向两侧铺出书架网格的内容边距：顶部进度矩形与底部操作区横贯到屏幕边，
            // 而布局上报的宽度仍是网格内容宽，不影响 LazyGrid 的列排布。
            .layout { measurable, constraints ->
                val extra = edgeInset.roundToPx()
                if (extra == 0 || !constraints.hasBoundedWidth) {
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                } else {
                    val width = constraints.maxWidth + extra * 2
                    val placeable = measurable.measure(
                        constraints.copy(minWidth = width, maxWidth = width)
                    )
                    layout(constraints.maxWidth, placeable.height) {
                        placeable.place(-extra, 0)
                    }
                }
            }
    ) {
        // 顶部：整块矩形作为进度填充区，thumb 实心、向起点渐隐，不再单占一行
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(barColor)
                .drawBehind {
                    val ratio = series.progress.ratio.coerceIn(0f, 1f)
                    if (ratio <= 0f) return@drawBehind
                    val thumbWidth = 1.dp.toPx()
                    val end = size.width * ratio
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(accent.copy(alpha = 0f), accent.copy(alpha = 0.45f)),
                            startX = 0f,
                            endX = end,
                        ),
                        size = Size(end, size.height),
                    )
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(end - thumbWidth, 0f),
                        size = Size(thumbWidth, size.height),
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = barHorizontalPadding, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = series.name,
                        color = LegadoTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = progressText,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    SeriesPanelActionButton(
                        icon = AppIcons.MoreVert,
                        contentDescription = stringResource(R.string.more_actions),
                        onClick = { menuExpanded = true },
                    )
                    // 卡片同款受控菜单：按钮自己收点击，弹层只跟随 menuExpanded。
                    // 锚点即这颗按钮，与长按折叠卡的菜单同一套四引擎分流。
                    AppActionMenu(
                        groups = seriesActionGroups(
                            hasCustomCover = series.coverPath != null,
                            canCollapse = false,
                        ),
                        onAction = { id ->
                            menuExpanded = false
                            when (id) {
                                SeriesActionId.PickCover -> onPickCover()
                                SeriesActionId.ResetCover -> onResetCover()
                                SeriesActionId.Rename -> onRename()
                                SeriesActionId.Manage -> onManageMembers()
                                SeriesActionId.Dissolve -> onDissolve()
                            }
                        },
                        modifier = Modifier.matchParentSize(),
                        embeddedExpanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        trigger = {},
                    )
                }
                SeriesPanelActionButton(
                    icon = AppIcons.ExpandLess,
                    contentDescription = collapseLabel,
                    onClick = onCollapse,
                )
            }
        }
        // 成员区：先缩回书架网格的内容边距，再按真实列数分行、间距取书架同一口径，
        // 展开后的卡片因此与书架单本同宽同起点，抽屉只是插进书架，不改网格节奏。
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = edgeInset)
                .padding(vertical = 12.dp)
        ) {
            val columnCount = resolveBookshelfColumnCount(
                availableWidthDp = maxWidth.value,
                spacingDp = spacing.value,
                fixedColumns = if (adaptiveColumns) null else columns,
                minCellWidthDp = minCellWidthDp,
            )
            // 列表模式的卡片自带 4dp 垂直外边距，书架本身行间距就是 0，这里保持一致。
            val rowSpacing = if (layoutMode == 0) 0.dp else spacing
            Column(verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
                series.members.chunked(columnCount).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        rowItems.forEach { item ->
                            // 与书架单本同一份封面共享键：抽屉里点进去也要有封面转场
                            val sharedCoverKey = bookCoverSharedElementKey(
                                item.book.bookUrl,
                                sharedCoverSourceId,
                            )
                            // weight 必须挂在这一层：直接透传进 BookItem 会被列表分支内部
                            // Column 当成垂直权重消费（Column 高度无界 → 整卡被量成 0）。
                            Box(modifier = Modifier.weight(1f)) {
                                SeriesBookItem(
                                    settings = settings,
                                    customTagColors = customTagColors,
                                    item = item,
                                    layoutMode = layoutMode,
                                    gridStyle = gridStyle,
                                    immersive = immersive,
                                    isCompact = isCompact,
                                    titleSmallFont = titleSmallFont,
                                    titleCenter = titleCenter,
                                    titleMaxLines = titleMaxLines,
                                    coverShadow = coverShadow,
                                    locked = isLocked(item),
                                    updating = isUpdating(item),
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    sharedCoverKey = sharedCoverKey,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { onBookClick(item, sharedCoverKey) },
                                    onLongClick = { onBookLongClick(item, sharedCoverKey) },
                                )
                            }
                        }
                        repeat(columnCount - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        // 底部：居中再放一个收起，滑到底部时不必回到顶部
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            SeriesPanelActionButton(
                icon = AppIcons.ExpandLess,
                contentDescription = collapseLabel,
                onClick = onCollapse,
            )
        }
    }
}

/**
 * 抽屉操作按钮。
 *
 * HyperOS 4（Miuix 引擎 + 该开关 + 系统模糊可用）下用库的玻璃图标按钮，
 * 与顶栏、标签页同一套外形；其余引擎沿用系列 Tonal 按钮。
 * 不加这个分支时，整页原生外观里会突兀地出现两颗 M3 药丸。
 *
 * 关键：这里必须传 `backdrop = null`。抽屉是 `LazyGrid` 里跨整行的 item，
 * 在 item 内部对 Scaffold 的 backdrop 做采样会让 RenderThread 的 RenderNode
 * 树自引用，`prepareTreeImpl` 无限递归到栈溢出（真机 SIGSEGV 直接闪退，实测复现）。
 * 传 null 后走库的静态外观：外形与 OS4 按钮一致，只是不含实时模糊。
 */
@Composable
private fun SeriesPanelActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isMiuixHyperOs4Enabled()) {
        GlassIconButton(
            onClick = onClick,
            modifier = modifier.semantics { role = Role.Button },
            backdrop = null,
            size = 40.dp,
        ) {
            AppIcon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.onSurface,
            )
        }
        return
    }
    MediumTonalButton(
        onClick = onClick,
        icon = icon,
        contentDescription = contentDescription,
        containerColor = LegadoTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    )
}

@Composable
private fun SeriesBookItem(
    settings: BookshelfSettings,
    customTagColors: ImmutableList<TagColorPair>,
    item: BookUiItem,
    layoutMode: Int,
    gridStyle: Int,
    immersive: Boolean,
    isCompact: Boolean,
    titleSmallFont: Boolean,
    titleCenter: Boolean,
    titleMaxLines: Int,
    coverShadow: Boolean,
    locked: Boolean,
    updating: Boolean,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    sharedCoverKey: String?,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    BookItem(
        settings = settings,
        customTagColors = customTagColors,
        bookUi = item,
        layoutMode = layoutMode,
        modifier = modifier,
        gridStyle = gridStyle,
        immersive = immersive,
        isCompact = isCompact,
        isUpdating = updating,
        locked = locked,
        titleSmallFont = titleSmallFont,
        titleCenter = titleCenter,
        titleMaxLines = titleMaxLines,
        coverShadow = coverShadow,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        sharedCoverKey = sharedCoverKey,
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

@Composable
private fun seriesCountText(progress: SeriesProgress): String {
    return if (progress.finished > 0) {
        stringResource(
            R.string.bookshelf_series_count_read,
            progress.total,
            progress.finished,
        )
    } else {
        stringResource(R.string.bookshelf_series_count, progress.total)
    }
}

@Composable
private fun seriesProgressText(progress: SeriesProgress): String {
    val readText = stringResource(
        R.string.bookshelf_series_progress_summary,
        progress.finished,
        progress.total,
    )
    val readingVolume = progress.readingVolume ?: return readText
    return readText + " · " + stringResource(
        R.string.bookshelf_series_reading_volume,
        readingVolume,
    )
}

/**
 * 收纳 / 改名共用的命名对话框。
 *
 * [suggestions] 是当前分组里已有的系列名：点一下即可把选中的书追加进那个系列（移入），
 * 不必手动记住并输入完全一致的系列名。
 */
@Composable
internal fun BookshelfSeriesNameDialog(
    show: Boolean,
    title: String,
    initialName: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    suggestions: List<String> = emptyList(),
) {
    if (!show) return
    var name by rememberSaveable(title, initialName) { mutableStateOf(initialName) }
    AppAlertDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = title,
        content = {
            Column {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.bookshelf_series_name_label),
                    backgroundColor = LegadoTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .padding(top = 8.dp),
                )
                if (suggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        suggestions.forEach { suggestion ->
                            AssistChip(
                                onClick = { name = suggestion },
                                label = {
                                    Text(
                                        text = suggestion,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmText = confirmText,
        confirmEnabled = name.isNotBlank(),
        onConfirm = { if (name.isNotBlank()) onConfirm(name.trim()) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = onDismiss,
    )
}

/**
 * 把用户选中的图片复制到 externalFiles/covers，返回本地路径；失败返回 null。
 * 与书架分组封面同一套存储约定：内容 MD5 命名，重复选同一张图不会堆积文件。
 */
internal suspend fun saveSeriesCover(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val extension = groupCoverFileExtension(resolver.getType(uri))
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@runCatching null
            val fileName = MD5Utils.md5Encode(bytes.inputStream()) + ".$extension"
            val file = FileUtils.createFileIfNotExist(context.externalFiles, "covers", fileName)
            FileOutputStream(file).use { it.write(bytes) }
            file.absolutePath
        }.getOrNull()
    }
