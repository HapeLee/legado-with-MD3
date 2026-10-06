package io.legado.app.ui.book.readaloud.cast

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
    var type by rememberSaveable { mutableStateOf(CastCapsuleStyleStore.ROLE) }
    var style by remember { mutableStateOf(CastCapsuleStyleStore.raw(type)) }
    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    var colorNightSlot by rememberSaveable { mutableStateOf(false) }
    var imageNightSlot by rememberSaveable { mutableStateOf(false) }
    val typeNames = mapOf(
        CastCapsuleStyleStore.ROLE to stringResource(R.string.capsule_style_role),
        CastCapsuleStyleStore.PLACEHOLDER to stringResource(R.string.capsule_style_placeholder),
        CastCapsuleStyleStore.BGM to stringResource(R.string.capsule_style_bgm),
    )
    val currentName = typeNames.getValue(type)
    // 底图换掉或清空后，旧文件由 store 删除；这里只负责把新文件读成位图给预览用
    var dayImage by remember { mutableStateOf<Bitmap?>(null) }
    var nightImage by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(type) { style = CastCapsuleStyleStore.raw(type) }
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
        val nightSlot = imageNightSlot
        val replaced = if (nightSlot) style.bgImageNight else style.bgImage
        scope.launch {
            val saved = runCatching {
                withContext(Dispatchers.IO) { CastCapsuleStyleStore.saveImage(appCtx, uri) }
            }.getOrNull()
            if (saved == null) {
                context.toastOnUi(imageFailedMsg)
                return@launch
            }
            val next = if (nightSlot) {
                style.copy(bgImageNight = saved)
            } else {
                style.copy(bgImage = saved)
            }
            style = next
            CastCapsuleStyleStore.set(type, next)
            if (replaced.isNotEmpty() &&
                replaced != next.bgImage &&
                replaced != next.bgImageNight
            ) {
                withContext(Dispatchers.IO) { CastCapsuleStyleStore.deleteImage(replaced) }
            }
            context.toastOnUi(styleSavedMsg)
        }
    }

    fun update(next: CastCapsuleStyle) {
        style = next
        CastCapsuleStyleStore.set(type, next)
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
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                // 钉住的那条预览是浮在列表之上的，Scaffold 给的 bottom 内边距不含它，
                // 所以按量到的真实高度留底，最后一行「恢复默认」才不会被压住。
                bottom = paddingValues.calculateBottomPadding() +
                    barDensity.run { (previewBarPx / density).dp } + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        typeNames.forEach { (key, name) ->
                            MediumTonalButton(
                                onClick = { type = key },
                                modifier = Modifier.weight(1f),
                                selected = key == type,
                                text = name,
                            )
                        }
                    }
                    AppText(
                        text = stringResource(R.string.capsule_style_summary, currentName),
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                        onValueChange = { update(style.copy(cornerRadius = it.toInt())) },
                    )
                    StyleColorRow(
                        title = stringResource(R.string.capsule_style_bg_color_day),
                        color = style.bgColor,
                        onClick = {
                            colorNightSlot = false
                            showColorPicker = true
                        },
                        onClear = { update(style.copy(bgColor = 0)) },
                    )
                    StyleColorRow(
                        title = stringResource(R.string.capsule_style_bg_color_night),
                        color = style.bgColorNight,
                        onClick = {
                            colorNightSlot = true
                            showColorPicker = true
                        },
                        onClear = { update(style.copy(bgColorNight = 0)) },
                    )
                    StyleImageRow(
                        title = stringResource(R.string.capsule_style_bg_image_day),
                        path = style.bgImage,
                        onPick = {
                            imageNightSlot = false
                            imagePicker.launch(arrayOf("image/*"))
                        },
                        onClear = {
                            val old = style.bgImage
                            val next = style.copy(bgImage = "")
                            update(next)
                            scope.launch(Dispatchers.IO) {
                                CastCapsuleStyleStore.deleteImage(old)
                            }
                        },
                    )
                    StyleImageRow(
                        title = stringResource(R.string.capsule_style_bg_image_night),
                        path = style.bgImageNight,
                        onPick = {
                            imageNightSlot = true
                            imagePicker.launch(arrayOf("image/*"))
                        },
                        onClear = {
                            val old = style.bgImageNight
                            val next = style.copy(bgImageNight = "")
                            update(next)
                            scope.launch(Dispatchers.IO) {
                                CastCapsuleStyleStore.deleteImage(old)
                            }
                        },
                    )
                    if (type == CastCapsuleStyleStore.ROLE) {
                        SwitchSettingItem(
                            title = stringResource(R.string.capsule_style_show_avatar),
                            description = stringResource(R.string.capsule_style_show_avatar_summary),
                            checked = style.showAvatar,
                            onCheckedChange = { update(style.copy(showAvatar = it)) },
                        )
                        SwitchSettingItem(
                            title = stringResource(R.string.capsule_style_show_name),
                            description = stringResource(R.string.capsule_style_show_name_summary),
                            checked = style.showName,
                            onCheckedChange = { update(style.copy(showName = it)) },
                        )
                        SwitchSettingItem(
                            title = stringResource(R.string.capsule_style_show_pool),
                            description = stringResource(R.string.capsule_style_show_pool_summary),
                            checked = style.showPool,
                            onCheckedChange = { update(style.copy(showPool = it)) },
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
                            onValueChange = { update(style.copy(avatarScale = it.toInt())) },
                        )
                        SliderSettingItem(
                            title = stringResource(R.string.capsule_style_avatar_corner),
                            description = stringResource(R.string.capsule_style_avatar_corner_summary),
                            value = style.avatarRadius.toFloat(),
                            defaultValue = CastCapsuleStyle.FULL.toFloat(),
                            valueRange = 0f..CastCapsuleStyle.FULL.toFloat(),
                            valueLabel = { "${it.toInt()}%" },
                            onValueChange = { update(style.copy(avatarRadius = it.toInt())) },
                        )
                        SliderSettingItem(
                            title = stringResource(R.string.capsule_style_avatar_left_right),
                            value = style.avatarDx.toFloat(),
                            defaultValue = 0f,
                            valueRange = (-CastCapsuleStyle.SHIFT_FULL).toFloat()..CastCapsuleStyle.SHIFT_FULL.toFloat(),
                            valueLabel = { "${it.toInt()}%" },
                            onValueChange = { update(style.copy(avatarDx = it.toInt())) },
                        )
                        SliderSettingItem(
                            title = stringResource(R.string.capsule_style_avatar_up_down),
                            value = style.avatarDy.toFloat(),
                            defaultValue = 0f,
                            valueRange = (-CastCapsuleStyle.SHIFT_FULL).toFloat()..CastCapsuleStyle.SHIFT_FULL.toFloat(),
                            valueLabel = { "${it.toInt()}%" },
                            onValueChange = { update(style.copy(avatarDy = it.toInt())) },
                        )
                    }
                }
            }

            item {
                SplicedColumnGroup {
                    ClickableSettingItem(
                        title = stringResource(R.string.capsule_style_reset),
                        description = stringResource(R.string.capsule_style_reset_summary),
                        onClick = {
                            val old = style
                            style = CastCapsuleStyle.Default
                            CastCapsuleStyleStore.reset(type)
                            scope.launch(Dispatchers.IO) {
                                listOf(old.bgImage, old.bgImageNight)
                                    .filter { it.isNotEmpty() }
                                    .forEach { CastCapsuleStyleStore.deleteImage(it) }
                            }
                        },
                    )
                }
            }
        }
    }

    ColorPickerSheet(
        show = showColorPicker,
        initialColor = (if (colorNightSlot) style.bgColorNight else style.bgColor)
            .takeIf { it != 0 }
            ?: LegadoTheme.colorScheme.secondaryContainer.toArgb(),
        onDismissRequest = { showColorPicker = false },
        onColorSelected = { color ->
            update(
                if (colorNightSlot) {
                    style.copy(bgColorNight = color)
                } else {
                    style.copy(bgColor = color)
                },
            )
        },
    )
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
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.reset),
                        )
                    }
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
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.reset),
                    )
                }
            }
        },
    )
}

/** 深浅两块底板各画一颗胶囊：同一份数值，两种正文底色下长得什么样当场就能看到。 */
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(LegadoTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PreviewBoard(
            modifier = Modifier.weight(1f),
            board = Color(0xFFFAFAFA),
            label = stringResource(R.string.capsule_style_preview_day),
            textColor = Color.Black,
            style = style,
            bgColor = style.bgColor,
            image = dayImage ?: nightImage,
            withAvatar = withAvatar,
            nameText = nameText,
            poolText = poolText,
            squareAvatarOnly = type == CastCapsuleStyleStore.ROLE,
        )
        PreviewBoard(
            modifier = Modifier.weight(1f),
            board = Color(0xFF17181A),
            label = stringResource(R.string.capsule_style_preview_night),
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

@Composable
private fun PreviewBoard(
    modifier: Modifier,
    board: Color,
    label: String,
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
    Column(
        modifier = modifier.background(board).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        // 胶囊钉在底板左沿，与正文的行内左起一致：宽度变化不许把头像平移走
        horizontalAlignment = Alignment.Start,
    ) {
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
        AppText(
            text = label,
            style = LegadoTheme.typography.labelMedium,
            color = LegadoTheme.colorScheme.onSurfaceVariant,
        )
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
