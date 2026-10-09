package io.legado.app.ui.book.readaloud.cast

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.feature.reader.core.cast.CastCapsuleGeometry
import io.legado.app.feature.reader.core.cast.CastCapsuleStyle
import io.legado.app.help.readaloud.cast.CastCapsuleImageCache
import io.legado.app.help.readaloud.cast.CastCapsuleStyleStore
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.tabRow.rememberTabPagerState
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

/**
 * 朗读胶囊设置（我的 → 朗读规则 → 朗读胶囊设置）。
 *
 * 正文里的三类胶囊（角色、未分配占位、背景音乐）各存一套外观：圆角、底色、底图，
 * 带头像的那两类还能改头像的圆角与位置。深浅模式各一份颜色和底图 —— 同一张图压在
 * 两种正文底色上的对比完全不同，所以预览同时画浅色与深色两颗。
 *
 * 数值全部按百分比存（100 = 现在这颗胶囊），落到绘制侧再乘正文字号，字号变了比例不变。
 */

@Composable
fun CastCapsuleStyleRouteScreen(onBackClick: () -> Unit) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val types = CastCapsuleStyleStore.TYPES
    var type by rememberSaveable { mutableStateOf(CastCapsuleStyleStore.ROLE) }
    // 三类胶囊各留一份草稿：pager 会把相邻页一起挂着，共用一份 style 的话「滑到一半顺手拖个
    // 滑杆」就改到别的类型头上了。底部预览、取色与选图都按各自的目标类型取这里的草稿。
    val drafts = remember {
        mutableStateMapOf<String, CastCapsuleStyle>().apply {
            types.forEach { put(it, CastCapsuleStyleStore.raw(it)) }
        }
    }
    // 取色/选图作用于哪一类：点的是哪一页就改哪一类，而不是「当前选中」那一类
    var pickerType by remember { mutableStateOf(type) }
    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    var colorNightSlot by rememberSaveable { mutableStateOf(false) }
    var imageNightSlot by rememberSaveable { mutableStateOf(false) }
    val typeNames = mapOf(
        CastCapsuleStyleStore.ROLE to stringResource(R.string.capsule_style_role),
        CastCapsuleStyleStore.PLACEHOLDER to stringResource(R.string.capsule_style_placeholder),
        CastCapsuleStyleStore.BGM to stringResource(R.string.capsule_style_bgm),
    )
    val style = drafts.getValue(type)
    val pagerState = rememberTabPagerState(
        selectedIndex = types.indexOf(type).coerceAtLeast(0),
        pageCount = types.size,
        onPageSelected = { page -> types.getOrNull(page)?.let { type = it } },
    )

    fun applyStyle(target: String, next: CastCapsuleStyle) {
        drafts[target] = next
        CastCapsuleStyleStore.set(target, next)
    }

    // 底图换掉或清空后，旧文件由 store 删除；这里只负责把新文件读成位图给预览用
    var dayImage by remember { mutableStateOf<Bitmap?>(null) }
    var nightImage by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(style.bgImage) {
        dayImage = style.bgImage.takeIf { it.isNotEmpty() }
            ?.let { CastCapsuleImageCache.cached(it) ?: CastCapsuleImageCache.load(it) }
    }
    LaunchedEffect(style.bgImageNight) {
        nightImage = style.bgImageNight.takeIf { it.isNotEmpty() }
            ?.let { CastCapsuleImageCache.cached(it) ?: CastCapsuleImageCache.load(it) }
    }
    val imageFailedMsg = stringResource(R.string.capsule_style_image_failed)
    val styleSavedMsg = stringResource(R.string.capsule_style_saved)
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val target = pickerType
        val nightSlot = imageNightSlot
        val current = drafts[target] ?: return@rememberLauncherForActivityResult
        val replaced = if (nightSlot) current.bgImageNight else current.bgImage
        scope.launch {
            val saved = runCatching {
                withContext(Dispatchers.IO) { CastCapsuleStyleStore.saveImage(appCtx, uri) }
            }.getOrNull()
            if (saved == null) {
                context.toastOnUi(imageFailedMsg)
                return@launch
            }
            val next = if (nightSlot) {
                current.copy(bgImageNight = saved)
            } else {
                current.copy(bgImage = saved)
            }
            applyStyle(target, next)
            if (replaced.isNotEmpty() &&
                replaced != next.bgImage &&
                replaced != next.bgImageNight
            ) {
                withContext(Dispatchers.IO) { CastCapsuleStyleStore.deleteImage(replaced) }
            }
            context.toastOnUi(styleSavedMsg)
        }
    }

    /**
     * 钉底预览条的真实高度（px）：列表要按它留底，不然最后一行「恢复默认」会被压在预览下面。
     * Scaffold 传进来的 bottom 内边距并不含我们这条自绘的 bottomBar，所以只能自己量。
     */
    var previewBarPx by remember { mutableStateOf(0) }
    val barDensity = LocalDensity.current

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // 本页没有官方页可并，样式全在这三类胶囊上
        contentColor = LegadoTheme.colorScheme.onSurface,
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.capsule_style_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = { TopBarNavigationButton(onClick = onBackClick) },
                bottomContent = {
                    AppTabRow(
                        tabTitles = types.map { typeNames.getValue(it) },
                        selectedTabIndex = types.indexOf(type).coerceAtLeast(0),
                        // 只派发选中项；滚页交给 rememberTabPagerState，保持一条同步路径
                        onTabSelected = { type = types[it] },
                        isScrollable = false,
                    )
                },
            )
        },
        // 预览钉在底部：放进列表里往下调滑块就会滚出屏幕，改完看不到效果
        bottomBar = {
            PreviewCard(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 12.dp)
                    .onSizeChanged { previewBarPx = it.height },
                style = style,
                dayImage = dayImage,
                nightImage = nightImage,
                type = type,
            )
        },
    ) { paddingValues ->
        // 三页各自一个编辑列表（各自保留滚动位置）；页内容按该页自己的草稿画
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val pageType = types[page]
            val draft = drafts.getValue(pageType)
            CapsuleStyleEditor(
                type = pageType,
                typeName = typeNames.getValue(pageType),
                style = draft,
                contentPadding = adaptiveContentPadding(
                    top = paddingValues.calculateTopPadding(),
                    // 钉住的那条预览是浮在列表之上的，Scaffold 给的 bottom 内边距不含它，
                    // 所以按量到的真实高度留底，最后一行「恢复默认」才不会被压住。
                    bottom = paddingValues.calculateBottomPadding() +
                            barDensity.run { (previewBarPx / density).dp } + 24.dp,
                ),
                onStyleChange = { applyStyle(pageType, it) },
                onPickColor = { night ->
                    pickerType = pageType
                    colorNightSlot = night
                    showColorPicker = true
                },
                onPickImage = { night ->
                    pickerType = pageType
                    imageNightSlot = night
                    imagePicker.launch(arrayOf("image/*"))
                },
                onClearImage = { night ->
                    val old = if (night) draft.bgImageNight else draft.bgImage
                    applyStyle(
                        pageType,
                        if (night) draft.copy(bgImageNight = "") else draft.copy(bgImage = ""),
                    )
                    scope.launch(Dispatchers.IO) { CastCapsuleStyleStore.deleteImage(old) }
                },
                onReset = {
                    drafts[pageType] = CastCapsuleStyle.Default
                    CastCapsuleStyleStore.reset(pageType)
                    scope.launch(Dispatchers.IO) {
                        listOf(draft.bgImage, draft.bgImageNight)
                            .filter { it.isNotEmpty() }
                            .forEach { CastCapsuleStyleStore.deleteImage(it) }
                    }
                },
            )
        }
    }

    ColorPickerSheet(
        show = showColorPicker,
        // 一直读「点它的那一类」的草稿：pager 滑到一半点时也不会串到另一种胶囊上
        initialColor = (if (colorNightSlot) drafts.getValue(pickerType).bgColorNight
        else drafts.getValue(pickerType).bgColor)
            .takeIf { it != 0 }
            ?: LegadoTheme.colorScheme.secondaryContainer.toArgb(),
        onDismissRequest = { showColorPicker = false },
        onColorSelected = { color ->
            val current = drafts.getValue(pickerType)
            applyStyle(
                pickerType,
                if (colorNightSlot) {
                    current.copy(bgColorNight = color)
                } else {
                    current.copy(bgColor = color)
                },
            )
        },
    )
}

/**
 * 一类胶囊的编辑列表（pager 的一页）。
 *
 * 三类共享同一套表单，只有「角色专属开关」和「头像滑杆」两段按类型出现；
 * 读哪一份草稿、写回哪一类，以及取色/选图/复位怎么落地，全部由调用方决定——
 * 这样一页只管把它自己那份草稿画出来、改回去。
 */
@Composable
private fun CapsuleStyleEditor(
    type: String,
    typeName: String,
    style: CastCapsuleStyle,
    contentPadding: PaddingValues,
    onStyleChange: (CastCapsuleStyle) -> Unit,
    onPickColor: (night: Boolean) -> Unit,
    onPickImage: (night: Boolean) -> Unit,
    onClearImage: (night: Boolean) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AppText(
                text = stringResource(R.string.capsule_style_summary, typeName),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        item {
            SplicedColumnGroup {
                SliderSettingItem(
                    title = stringResource(R.string.capsule_style_corner),
                    description = stringResource(R.string.capsule_style_corner_summary),
                    value = style.cornerRadius.toFloat(),
                    defaultValue = CastCapsuleStyle.FULL.toFloat(),
                    valueRange = 0f..CastCapsuleStyle.FULL.toFloat(),
                    valueLabel = { "${it.toInt()}%" },
                    onValueChange = { onStyleChange(style.copy(cornerRadius = it.toInt())) },
                )
                StyleColorRow(
                    title = stringResource(R.string.capsule_style_bg_color_day),
                    color = style.bgColor,
                    onClick = { onPickColor(false) },
                    onClear = { onStyleChange(style.copy(bgColor = 0)) },
                )
                StyleColorRow(
                    title = stringResource(R.string.capsule_style_bg_color_night),
                    color = style.bgColorNight,
                    onClick = { onPickColor(true) },
                    onClear = { onStyleChange(style.copy(bgColorNight = 0)) },
                )
                StyleImageRow(
                    title = stringResource(R.string.capsule_style_bg_image_day),
                    path = style.bgImage,
                    onPick = { onPickImage(false) },
                    onClear = { onClearImage(false) },
                )
                StyleImageRow(
                    title = stringResource(R.string.capsule_style_bg_image_night),
                    path = style.bgImageNight,
                    onPick = { onPickImage(true) },
                    onClear = { onClearImage(true) },
                )
                if (type == CastCapsuleStyleStore.ROLE) {
                    SwitchSettingItem(
                        title = stringResource(R.string.capsule_style_show_avatar),
                        description = stringResource(R.string.capsule_style_show_avatar_summary),
                        checked = style.showAvatar,
                        onCheckedChange = { onStyleChange(style.copy(showAvatar = it)) },
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.capsule_style_show_name),
                        description = stringResource(R.string.capsule_style_show_name_summary),
                        checked = style.showName,
                        onCheckedChange = { onStyleChange(style.copy(showName = it)) },
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.capsule_style_show_pool),
                        description = stringResource(R.string.capsule_style_show_pool_summary),
                        checked = style.showPool,
                        onCheckedChange = { onStyleChange(style.copy(showPool = it)) },
                    )
                }
                if (type != CastCapsuleStyleStore.BGM && style.showAvatar) {
                    SliderSettingItem(
                        title = stringResource(R.string.capsule_style_avatar_size),
                        description = stringResource(R.string.capsule_style_avatar_size_summary),
                        value = style.avatarScale.toFloat(),
                        defaultValue = CastCapsuleStyle.FULL.toFloat(),
                        valueRange = CastCapsuleStyle.AVATAR_SCALE_MIN.toFloat()..CastCapsuleStyle.AVATAR_SCALE_MAX.toFloat(),
                        valueLabel = { "${it.toInt()}%" },
                        onValueChange = { onStyleChange(style.copy(avatarScale = it.toInt())) },
                    )
                    SliderSettingItem(
                        title = stringResource(R.string.capsule_style_avatar_corner),
                        description = stringResource(R.string.capsule_style_avatar_corner_summary),
                        value = style.avatarRadius.toFloat(),
                        defaultValue = CastCapsuleStyle.FULL.toFloat(),
                        valueRange = 0f..CastCapsuleStyle.FULL.toFloat(),
                        valueLabel = { "${it.toInt()}%" },
                        onValueChange = { onStyleChange(style.copy(avatarRadius = it.toInt())) },
                    )
                    SliderSettingItem(
                        title = stringResource(R.string.capsule_style_avatar_left_right),
                        value = style.avatarDx.toFloat(),
                        defaultValue = 0f,
                        valueRange = (-CastCapsuleStyle.SHIFT_FULL).toFloat()..CastCapsuleStyle.SHIFT_FULL.toFloat(),
                        valueLabel = { "${it.toInt()}%" },
                        onValueChange = { onStyleChange(style.copy(avatarDx = it.toInt())) },
                    )
                    SliderSettingItem(
                        title = stringResource(R.string.capsule_style_avatar_up_down),
                        value = style.avatarDy.toFloat(),
                        defaultValue = 0f,
                        valueRange = (-CastCapsuleStyle.SHIFT_FULL).toFloat()..CastCapsuleStyle.SHIFT_FULL.toFloat(),
                        valueLabel = { "${it.toInt()}%" },
                        onValueChange = { onStyleChange(style.copy(avatarDy = it.toInt())) },
                    )
                }
            }
        }

        item {
            SplicedColumnGroup {
                ClickableSettingItem(
                    title = stringResource(R.string.capsule_style_reset),
                    description = stringResource(R.string.capsule_style_reset_summary),
                    onClick = onReset,
                )
            }
        }
    }
}

/** 一行底色设置：色块直接当说明看，未设置时描述写「跟随主题」。 */
@Composable
private fun StyleColorRow(
    title: String,
    color: Int,
    onClick: () -> Unit,
    onClear: () -> Unit,
) {
    ClickableSettingItem(
        title = title,
        description = if (color == 0) {
            stringResource(R.string.capsule_style_color_follow)
        } else {
            String.format(java.util.Locale.US, "#%08X", color)
        },
        onClick = onClick,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(
                            1.dp,
                            LegadoTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(6.dp),
                        )
                        .background(if (color == 0) Color.Transparent else Color(color)),
                )
                if (color != 0) {
                    MediumTonalButton(
                        onClick = onClear,
                        icon = Icons.Default.Close,
                        contentDescription = stringResource(R.string.reset),
                    )
                }
            }
        },
    )
}

/** 一行底图设置：点进去选图，选了就能清掉。 */
@Composable
private fun StyleImageRow(
    title: String,
    path: String,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    ClickableSettingItem(
        title = title,
        description = if (path.isEmpty()) {
            stringResource(R.string.capsule_style_image_none)
        } else {
            stringResource(R.string.capsule_style_image_set)
        },
        onClick = onPick,
        trailingContent = {
            if (path.isNotEmpty()) {
                MediumTonalButton(
                    onClick = onClear,
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.reset),
                )
            }
        },
    )
}

/**
 * 大胶囊的投影：自己画，不用平台的 elevation 阴影——在这么小的圆头形状上、投影色又淡，
 * 平台阴影几乎看不出模糊（越淡越像一条边）。这里把轮廓均匀往外放大若干层、逐层淡出，
 * 等效出一圈柔和光晕：[PREVIEW_SHADOW_BLUR] 是扩散半径，[PREVIEW_SHADOW_ALPHA] 是
 * 贴着轮廓处的浓度，这两个值就能调软硬。
 */
private val PREVIEW_SHADOW_BLUR = 10.dp
private val PREVIEW_SHADOW_ALPHA = 0.06f
private val PREVIEW_SHADOW_OFFSET_Y = 3.dp
private const val PREVIEW_SHADOW_LAYERS = 12

/** 投影要往外扩，给底下留点余量，不然会被屏幕边裁掉。 */
private val PREVIEW_SHADOW_ROOM = 10.dp

/** 外层那颗「大胶囊」的形状：整颗圆头，与里面两颗按样式自己算出来的圆角互不牵连。 */
private val PREVIEW_PILL = RoundedCornerShape(percent = 100)

/**
 * 大胶囊与小胶囊之间的留白：四个方向同一个值，左右和上下才不会一边松一边紧。
 * 两颗小胶囊之间也用同一个值。
 */
private val PREVIEW_PILL_PADDING = 8.dp

/** 预览用的浅/深页面底色：与正文里那两种底色同一组值。 */
private val PREVIEW_PAGE_DAY = Color(0xFFFAFAFA)
private val PREVIEW_PAGE_NIGHT = Color(0xFF17181A)

/**
 * 大胶囊的底：左半浅色页面、右半深色页面，中间是硬边界。
 *
 * 上面两颗胶囊 + 间距是对称的，边界正好落在两颗中间那道 8dp 间隙上，
 * 于是两种页面底色各占一半，又不会从中间切开任何一颗胶囊。
 */
private val PREVIEW_PAGE_SPLIT = Brush.horizontalGradient(
    0f to PREVIEW_PAGE_DAY,
    0.5f to PREVIEW_PAGE_DAY,
    0.5f to PREVIEW_PAGE_NIGHT,
    1f to PREVIEW_PAGE_NIGHT,
)

/**
 * 沿轮廓往外叠出一圈柔和投影。
 *
 * 每一层都是「把圆角矩形整体放大一点」的圆头矩形（圆角半径同步加上同样的量，所以
 * 每一层仍然是正圆头），由外向内画：外圈先铺、里圈再叠上去，于是贴着轮廓处层数最多、
 * 浓度最高，越往外越淡，直到 [PREVIEW_SHADOW_BLUR] 处归零——比平台阴影可控，
 * 也真的看得出模糊。
 */
private fun Modifier.previewShadow(): Modifier = drawBehind {
    val blur = PREVIEW_SHADOW_BLUR.toPx()
    val dy = PREVIEW_SHADOW_OFFSET_Y.toPx()
    val radius = size.height / 2f
    val layerAlpha = PREVIEW_SHADOW_ALPHA / PREVIEW_SHADOW_LAYERS
    for (i in PREVIEW_SHADOW_LAYERS downTo 1) {
        val grow = blur * i / PREVIEW_SHADOW_LAYERS
        drawRoundRect(
            color = Color.Black.copy(alpha = layerAlpha),
            topLeft = Offset(-grow, -grow + dy),
            size = Size(size.width + grow * 2f, size.height + grow * 2f),
            cornerRadius = CornerRadius(radius + grow, radius + grow),
        )
    }
}

/**
 * 预览：外面一颗大胶囊，里面套着两颗完整的胶囊——浅色页面一颗、深色页面一颗。
 *
 * 大胶囊的底就是那两种页面底色（左半浅、右半深），所以两颗胶囊各自压在自己那种底色上；
 * 里层两颗只有胶囊本身，不再各带一块黑白底板。整条宽度由两颗胶囊撑出来，不铺屏宽。
 */
@Composable
private fun PreviewCard(
    modifier: Modifier = Modifier,
    style: CastCapsuleStyle,
    dayImage: Bitmap?,
    nightImage: Bitmap?,
    type: String,
) {
    // 未分配那颗正文里只有一个人形图标，一个字都没有
    val withAvatar = type != CastCapsuleStyleStore.BGM && style.showAvatar
    // 关掉的那一栏在预览里同样连位置一起没有，和正文量出来的是同一颗胶囊
    val nameText = when (type) {
        CastCapsuleStyleStore.PLACEHOLDER -> ""
        CastCapsuleStyleStore.BGM -> stringResource(R.string.capsule_style_preview_bgm)
        else -> stringResource(R.string.capsule_style_preview_name).takeIf { style.showName }.orEmpty()
    }
    val poolText = if (type == CastCapsuleStyleStore.ROLE && style.showPool) {
        stringResource(R.string.capsule_style_preview_pool)
    } else {
        ""
    }
    Box(
        // 撑满宽度只为把大胶囊摆在中间：看得见的只有它，宽高都由里面两颗决定
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = PREVIEW_SHADOW_ROOM),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                // 整颗连着投影一起浮在列表之上；点击也整颗吃掉，正从它下面滑过的设置项
                // 才不会被点到——和 FloatingBottomBar 同一个做法。
                .previewShadow()
                .clip(PREVIEW_PILL)
                // 大胶囊的底：左半浅色页面、右半深色页面（硬边界落在两颗中间的空隙上）
                .background(PREVIEW_PAGE_SPLIT)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(PREVIEW_PILL_PADDING),
            horizontalArrangement = Arrangement.spacedBy(PREVIEW_PILL_PADDING),
        ) {
            PreviewCapsule(
                textColor = Color.Black,
                style = style,
                bgColor = style.bgColor,
                image = dayImage ?: nightImage,
                withAvatar = withAvatar,
                nameText = nameText,
                poolText = poolText,
                squareAvatarOnly = type == CastCapsuleStyleStore.ROLE,
            )
            PreviewCapsule(
                textColor = Color.White,
                style = style,
                bgColor = style.bgColorNight,
                image = nightImage ?: dayImage,
                withAvatar = withAvatar,
                nameText = nameText,
                poolText = poolText,
                squareAvatarOnly = type == CastCapsuleStyleStore.ROLE,
            )
        }
    }
}

/**
 * 里层那颗胶囊：只有胶囊本身（底色、头像、名字/池名），不带页面底色——
 * 页面底色由外面的大胶囊给，左半浅、右半深。
 */
@Composable
private fun PreviewCapsule(
    textColor: Color,
    style: CastCapsuleStyle,
    bgColor: Int,
    image: Bitmap?,
    withAvatar: Boolean,
    nameText: String,
    poolText: String,
    squareAvatarOnly: Boolean,
) {
    val height = 40.dp
    val pad = height * CastCapsuleGeometry.padRatio
    val withText = nameText.isNotEmpty() || poolText.isNotEmpty()
    // 与正文同一口径：只显头像的角色那颗（见 widthOf）、以及未分配占位那颗（见
    // placeholderWidthPx）都是正方形，圆角拉满就是正圆。
    val square = if (squareAvatarOnly) withAvatar && !withText else true
    val avatarSize = style.avatarDiameterDp(height)
    // 落点与正文同源：两颗共用 CastCapsuleStyle.avatarLeft，都不随名字/池小字开关漂移。
    val avatarLeft = style.avatarLeftDp(height)
    val avatarShiftY = style.avatarShiftYDp(height)
    val textLeft = if (withAvatar) {
        maxOf(pad, avatarLeft + avatarSize + height * CastCapsuleGeometry.gapRatio)
    } else {
        pad
    }
    val shape = RoundedCornerShape(percent = style.cornerRadius.coerceIn(0, CastCapsuleStyle.FULL))
    Box(
        modifier = Modifier
            .height(height)
            .then(
                if (square) {
                    Modifier.width(height)
                } else {
                    Modifier.widthIn(min = if (withText) 96.dp else avatarSize + pad * 2f)
                },
            )
            .clip(shape)
            .background(
                if (bgColor == 0) {
                    textColor.copy(alpha = 0.13f)
                } else {
                    Color(bgColor)
                },
            ),
    ) {
        if (image != null && !image.isRecycled) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        val onColor = if (bgColor == 0) {
            textColor
        } else {
            contrastColor(bgColor)
        }
        if (withText) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = textLeft, end = pad),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (nameText.isNotEmpty()) {
                    AppText(
                        text = nameText,
                        fontSize = 13.sp,
                        color = onColor,
                        maxLines = 1,
                    )
                }
                if (poolText.isNotEmpty()) {
                    AppText(
                        text = poolText,
                        fontSize = 10.sp,
                        color = onColor.copy(alpha = 0.72f),
                        maxLines = 1,
                    )
                }
            }
        }
        if (withAvatar) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = avatarLeft, y = avatarShiftY)
                    .size(avatarSize)
                    .clip(
                        RoundedCornerShape(
                            percent = style.avatarRadius.coerceIn(0, CastCapsuleStyle.FULL),
                        ),
                    )
                    .background(onColor.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                if (nameText.isNotEmpty()) {
                    // 角色那颗画的是头像，占位那颗只有人形图标 → 这里用名字首字代表头像
                    AppText(
                        text = nameText.take(1),
                        fontSize = 11.sp,
                        color = onColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * 预览的几何换算：Dp 只是 Float 的单位包装，这里直接转发 [CastCapsuleStyle] 的同名函数，
 * 预览与正文共用同一份夹取规则（头像大小、位移上限），不再各抄一遍公式。
 */
private fun CastCapsuleStyle.avatarLeftDp(height: Dp): Dp = avatarLeft(height.value).dp

private fun CastCapsuleStyle.avatarShiftYDp(height: Dp): Dp =
    avatarCenterOffset(height.value).dp

private fun CastCapsuleStyle.avatarDiameterDp(height: Dp): Dp =
    avatarDiameter(height.value).dp

/** 自定义底色上的字色：按亮度翻黑白，和正文绘制侧同一个判据。 */
private fun contrastColor(argb: Int): Color {
    val r = (argb shr 16 and 0xFF) / 255f
    val g = (argb shr 8 and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    return if (0.299f * r + 0.587f * g + 0.114f * b > 0.6f) Color.Black else Color.White
}
