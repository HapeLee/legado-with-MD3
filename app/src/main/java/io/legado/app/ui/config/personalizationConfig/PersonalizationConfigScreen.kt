package io.legado.app.ui.config.personalizationConfig

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.MutableIntState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.TagColorGenerator
import io.legado.app.help.config.PersonalizationThemeConfig
import io.legado.app.ui.config.mainConfig.MainConfig
import io.legado.app.ui.config.themeConfig.TagColorPair
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.config.personalizationConfig.PersonalizationThemeListDialog
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.MediumOutlinedIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalizationConfigScreen(
    onBackClick: () -> Unit,
    onNavigateToFontSelect: () -> Unit
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    var showColorPicker by remember { mutableStateOf(false) }
    var currentColorKey by remember { mutableStateOf("cTopBarColor") }
    var showThemeListDialog by remember { mutableStateOf(false) }
    var saveThemeKey by remember { mutableStateOf<String?>(null) }
    var themeName by remember { mutableStateOf("") }
    var listVersion by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    val topBarColor = ThemeConfig.cTopBarColor
    val navBarColor = ThemeConfig.cNavBarColor
    val fontColor = ThemeConfig.cFontColor
    val bgColor = ThemeConfig.cBgColor
    val enableDeepPersonalization = ThemeConfig.enableDeepPersonalization
    val bookInfoInputColor = ThemeConfig.cBookInfoInputColor
    
    // Material Design 3 color roles
    val md3Primary = ThemeConfig.cMD3Primary
    val md3OnPrimary = ThemeConfig.cMD3OnPrimary
    val md3PrimaryContainer = ThemeConfig.cMD3PrimaryContainer
    val md3OnPrimaryContainer = ThemeConfig.cMD3OnPrimaryContainer
    val md3Secondary = ThemeConfig.cMD3Secondary
    val md3OnSecondary = ThemeConfig.cMD3OnSecondary
    val md3SecondaryContainer = ThemeConfig.cMD3SecondaryContainer
    val md3Tertiary = ThemeConfig.cMD3Tertiary
    val md3Error = ThemeConfig.cMD3Error
    val md3Surface = ThemeConfig.cMD3Surface
    val md3OnSurface = ThemeConfig.cMD3OnSurface
    val md3Background = ThemeConfig.cMD3Background
    val md3Outline = ThemeConfig.cMD3Outline
    val md3SurfaceContainerLow = ThemeConfig.cMD3SurfaceContainerLow
    val md3SurfaceVariant = ThemeConfig.cMD3SurfaceVariant
    
    // 边框设置
    val enableContainerBorder = ThemeConfig.enableContainerBorder
    val containerBorderWidth = ThemeConfig.containerBorderWidth
    val containerBorderStyle = ThemeConfig.containerBorderStyle
    val containerBorderColor = ThemeConfig.containerBorderColor

    // 中间单线间隔设置
    val enableItemDivider = ThemeConfig.enableItemDivider
    val itemDividerWidth = ThemeConfig.itemDividerWidth
    val itemDividerLength = ThemeConfig.itemDividerLength
    val itemDividerColor = ThemeConfig.itemDividerColor

    // 标签颜色设置
    val enableCustomTagColors = ThemeConfig.enableCustomTagColors
    val tagColors = remember(listVersion) { ThemeConfig.getCustomTagColors().toMutableStateList() }
    var showTagColorPicker by remember { mutableStateOf(false) }
    var editingTagColorIndex by remember { mutableIntStateOf(-1) }
    var showTagColorManagement by remember { mutableStateOf(false) }
    var editingTagTextColor by remember { mutableIntStateOf(0) }
    
    // 模糊设置
    val enableBlur = ThemeConfig.enableBlur
    val topBarBlurRadius = ThemeConfig.topBarBlurRadius
    val bottomBarBlurRadius = ThemeConfig.bottomBarBlurRadius
    val topBarBlurAlpha = ThemeConfig.topBarBlurAlpha
    val bottomBarBlurAlpha = ThemeConfig.bottomBarBlurAlpha
    val bottomBarLensRadius = ThemeConfig.bottomBarLensRadius
    val primaryColor = MaterialTheme.colorScheme.primary

    var editingNavIconDestination by remember { mutableStateOf<String?>(null) }
    val navIconLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val dest = editingNavIconDestination ?: return@rememberLauncherForActivityResult
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            val iconDir = java.io.File(context.filesDir, "nav_icons")
            iconDir.mkdirs()
            val destFile = java.io.File(iconDir, "$dest.png")
            inputStream?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val path = destFile.absolutePath
            when (dest) {
                "bookshelf" -> MainConfig.navIconBookshelf = path
                "explore" -> MainConfig.navIconExplore = path
                "rss" -> MainConfig.navIconRss = path
                "my" -> MainConfig.navIconMy = path
            }
        }
        editingNavIconDestination = null
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.personalization_setting),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            )
        ) {
            item {
                SplicedColumnGroup {
                    ClickableSettingItem(
                        title = stringResource(R.string.font_setting),
                        onClick = onNavigateToFontSelect
                    )
                }
            }

            item {
                SplicedColumnGroup {
                    SwitchSettingItem(
                        title = stringResource(R.string.personalization_setting),
                        checked = enableDeepPersonalization,
                        onCheckedChange = { ThemeConfig.enableDeepPersonalization = it }
                    )
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.color_setting)) {
                    ClickableSettingItem(
                        title = stringResource(R.string.reset_color),
                        description = "恢复为 Material Design 生成的主题颜色",
                        onClick = {
                            // 重置所有 MD3 颜色为默认值
                            ThemeConfig.cMD3Primary = 0
                            ThemeConfig.cMD3OnPrimary = 0
                            ThemeConfig.cMD3PrimaryContainer = 0
                            ThemeConfig.cMD3OnPrimaryContainer = 0
                            ThemeConfig.cMD3Secondary = 0
                            ThemeConfig.cMD3OnSecondary = 0
                            ThemeConfig.cMD3SecondaryContainer = 0
                            ThemeConfig.cMD3Tertiary = 0
                            ThemeConfig.cMD3Error = 0
                            ThemeConfig.cMD3Surface = 0
                            ThemeConfig.cMD3OnSurface = 0
                            ThemeConfig.cMD3Background = 0
                            ThemeConfig.cMD3Outline = 0
                            ThemeConfig.cMD3SurfaceContainerLow = 0
                            ThemeConfig.cMD3SurfaceVariant = 0
                            // 同时重置非 MD3 颜色配置
                            ThemeConfig.cTopBarColor = 0
                            ThemeConfig.cNavBarColor = 0
                            ThemeConfig.cFontColor = 0
                            ThemeConfig.cBgColor = 0
                        }
                    )
                    // Primary colors
                    ClickableSettingItem(
                        title = "主题色",
                        option = if (md3Primary != 0) "#${Integer.toHexString(md3Primary).uppercase()}" else stringResource(R.string.click_to_select),
                        onClick = {
                            currentColorKey = "cMD3Primary"
                            showColorPicker = true
                        },
                        trailingContent = {
                            if (md3Primary != 0) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(md3Primary))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
                                )
                            }
                        }
                    )

                    ClickableSettingItem(
                        title = "次要主题色",
                        option = if (md3Secondary != 0) "#${Integer.toHexString(md3Secondary).uppercase()}" else stringResource(R.string.click_to_select),
                        onClick = {
                            currentColorKey = "cMD3Secondary"
                            showColorPicker = true
                        },
                        trailingContent = {
                            if (md3Secondary != 0) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(md3Secondary))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
                                )
                            }
                        }
                    )

                    ClickableSettingItem(
                        title = "主要字体色",
                        option = if (md3OnSurface != 0) "#${Integer.toHexString(md3OnSurface).uppercase()}" else stringResource(R.string.click_to_select),
                        onClick = {
                            currentColorKey = "cMD3OnSurface"
                            showColorPicker = true
                        },
                        trailingContent = {
                            if (md3OnSurface != 0) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(md3OnSurface))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
                                )
                            }
                        }
                    )

                    ClickableSettingItem(
                        title = "次要字体色",
                        option = if (md3OnPrimaryContainer != 0) "#${Integer.toHexString(md3OnPrimaryContainer).uppercase()}" else stringResource(R.string.click_to_select),
                        onClick = {
                            currentColorKey = "cMD3OnPrimaryContainer"
                            showColorPicker = true
                        },
                        trailingContent = {
                            if (md3OnPrimaryContainer != 0) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(md3OnPrimaryContainer))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
                                )
                            }
                        }
                    )

                    ClickableSettingItem(
                        title = "背景色",
                        option = if (md3Background != 0) "#${Integer.toHexString(md3Background).uppercase()}" else stringResource(R.string.click_to_select),
                        onClick = {
                            currentColorKey = "cMD3Background"
                            showColorPicker = true
                        },
                        trailingContent = {
                            if (md3Background != 0) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(md3Background))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
                                )
                            }
                        }
                    )

                    ClickableSettingItem(
                        title = "标签容器色",
                        option = if (md3SurfaceContainerLow != 0) "#${Integer.toHexString(md3SurfaceContainerLow).uppercase()}" else stringResource(R.string.click_to_select),
                        onClick = {
                            currentColorKey = "cMD3SurfaceContainerLow"
                            showColorPicker = true
                        },
                        trailingContent = {
                            if (md3SurfaceContainerLow != 0) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(md3SurfaceContainerLow))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
                                )
                            }
                        }
                    )
                }
            }

            item {
                SplicedColumnGroup(title = "边框设置") {
                    SwitchSettingItem(
                        title = "显示容器边框",
                        checked = enableContainerBorder,
                        onCheckedChange = { ThemeConfig.enableContainerBorder = it }
                    )

                    SliderSettingItem(
                        title = "边框粗细",
                        description = "${containerBorderWidth}dp",
                        value = containerBorderWidth,
                        defaultValue = 1f,
                        valueRange = 0f..5f,
                        steps = 49,
                        onValueChange = { ThemeConfig.containerBorderWidth = it }
                    )

                    DropdownListSettingItem(
                        title = "边框样式",
                        selectedValue = containerBorderStyle,
                        displayEntries = arrayOf("实线", "虚线"),
                        entryValues = arrayOf("solid", "dashed"),
                        onValueChange = { ThemeConfig.containerBorderStyle = it }
                    )

                    if (containerBorderStyle == "dashed") {
                        SliderSettingItem(
                            title = "虚线间隔",
                            description = "${ThemeConfig.containerBorderDashWidth}dp",
                            value = ThemeConfig.containerBorderDashWidth,
                            defaultValue = 4f,
                            valueRange = 1f..10f,
                            steps = 89,
                            onValueChange = { ThemeConfig.containerBorderDashWidth = it }
                        )
                    }

                    ClickableSettingItem(
                        title = "边框颜色",
                        option = if (containerBorderColor != 0) "#${Integer.toHexString(containerBorderColor).uppercase()}" else stringResource(R.string.click_to_select),
                        onClick = {
                            currentColorKey = "containerBorderColor"
                            showColorPicker = true
                        },
                        trailingContent = {
                            if (containerBorderColor != 0) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(containerBorderColor))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
                                )
                            }
                        }
                    )

                    SwitchSettingItem(
                        title = "显示中间单线",
                        checked = enableItemDivider,
                        onCheckedChange = { ThemeConfig.enableItemDivider = it }
                    )

                    if (enableItemDivider) {
                        SliderSettingItem(
                            title = "单线粗细",
                            description = "${itemDividerWidth}dp",
                            value = itemDividerWidth,
                            defaultValue = 1f,
                            valueRange = 0f..5f,
                            steps = 49,
                            onValueChange = { ThemeConfig.itemDividerWidth = it }
                        )

                        SliderSettingItem(
                            title = "单线长度",
                            description = "${itemDividerLength.toInt()}%",
                            value = itemDividerLength,
                            defaultValue = 80f,
                            valueRange = 30f..100f,
                            steps = 14,
                            onValueChange = { ThemeConfig.itemDividerLength = it }
                        )

                        ClickableSettingItem(
                            title = "单线颜色",
                            option = if (itemDividerColor != 0) "#${Integer.toHexString(itemDividerColor).uppercase()}" else stringResource(R.string.click_to_select),
                            onClick = {
                                currentColorKey = "itemDividerColor"
                                showColorPicker = true
                            },
                            trailingContent = {
                                if (itemDividerColor != 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(itemDividerColor))
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outlineVariant,
                                                CircleShape
                                            )
                                    )
                                }
                            }
                        )
                    }
                }
            }

            item {
                SplicedColumnGroup(title = "模糊效果设置") {
                    SwitchSettingItem(
                        title = "启用模糊效果",
                        checked = enableBlur,
                        onCheckedChange = { ThemeConfig.enableBlur = it }
                    )

                    if (enableBlur) {
                        SliderSettingItem(
                            title = "顶栏模糊半径",
                            description = "模糊半径越大，系统运行越卡顿",
                            value = topBarBlurRadius.toFloat(),
                            defaultValue = 24f,
                            valueRange = 0f..30f,
                            onValueChange = { ThemeConfig.topBarBlurRadius = it.toInt() }
                        )

                        SliderSettingItem(
                            title = "底栏模糊半径",
                            description = "模糊半径越大，系统运行越卡顿",
                            value = bottomBarBlurRadius.toFloat(),
                            defaultValue = 8f,
                            valueRange = 0f..10f,
                            onValueChange = { ThemeConfig.bottomBarBlurRadius = it.toInt() }
                        )

                        SliderSettingItem(
                            title = "顶栏模糊透明度",
                            value = topBarBlurAlpha.toFloat(),
                            defaultValue = 73f,
                            valueRange = 0f..100f,
                            onValueChange = { ThemeConfig.topBarBlurAlpha = it.toInt() }
                        )

                        SliderSettingItem(
                            title = "底栏模糊透明度",
                            value = bottomBarBlurAlpha.toFloat(),
                            defaultValue = 40f,
                            valueRange = 0f..100f,
                            onValueChange = { ThemeConfig.bottomBarBlurAlpha = it.toInt() }
                        )

                        SliderSettingItem(
                            title = "底栏液化强度",
                            description = "控制液态玻璃的扭曲程度",
                            value = bottomBarLensRadius,
                            defaultValue = 24f,
                            valueRange = 0f..50f,
                            onValueChange = { ThemeConfig.bottomBarLensRadius = it }
                        )
                    }
                }
            }

            item {
                SplicedColumnGroup(title = "导航栏图标设置") {
                    ClickableSettingItem(
                        title = "书架图标",
                        description = if (MainConfig.navIconBookshelf.isNotEmpty()) "已设置自定义图标" else "使用默认图标",
                        onClick = {
                            editingNavIconDestination = "bookshelf"
                            navIconLauncher.launch("image/png")
                        },
                        trailingContent = {
                            if (MainConfig.navIconBookshelf.isNotEmpty()) {
                                Row {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(2.dp)
                                    ) {
                                        val bmp = remember(MainConfig.navIconBookshelf) {
                                            android.graphics.BitmapFactory.decodeFile(MainConfig.navIconBookshelf)
                                        }
                                        if (bmp != null) {
                                            Icon(
                                                painter = androidx.compose.ui.graphics.painter.BitmapPainter(bmp.asImageBitmap()),
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = Color.Unspecified
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { MainConfig.navIconBookshelf = "" },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "删除",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    )
                    ClickableSettingItem(
                        title = "发现图标",
                        description = if (MainConfig.navIconExplore.isNotEmpty()) "已设置自定义图标" else "使用默认图标",
                        onClick = {
                            editingNavIconDestination = "explore"
                            navIconLauncher.launch("image/png")
                        },
                        trailingContent = {
                            if (MainConfig.navIconExplore.isNotEmpty()) {
                                Row {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(2.dp)
                                    ) {
                                        val bmp = remember(MainConfig.navIconExplore) {
                                            android.graphics.BitmapFactory.decodeFile(MainConfig.navIconExplore)
                                        }
                                        if (bmp != null) {
                                            Icon(
                                                painter = androidx.compose.ui.graphics.painter.BitmapPainter(bmp.asImageBitmap()),
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = Color.Unspecified
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { MainConfig.navIconExplore = "" },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "删除",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    )
                    ClickableSettingItem(
                        title = "订阅图标",
                        description = if (MainConfig.navIconRss.isNotEmpty()) "已设置自定义图标" else "使用默认图标",
                        onClick = {
                            editingNavIconDestination = "rss"
                            navIconLauncher.launch("image/png")
                        },
                        trailingContent = {
                            if (MainConfig.navIconRss.isNotEmpty()) {
                                Row {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(2.dp)
                                    ) {
                                        val bmp = remember(MainConfig.navIconRss) {
                                            android.graphics.BitmapFactory.decodeFile(MainConfig.navIconRss)
                                        }
                                        if (bmp != null) {
                                            Icon(
                                                painter = androidx.compose.ui.graphics.painter.BitmapPainter(bmp.asImageBitmap()),
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = Color.Unspecified
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { MainConfig.navIconRss = "" },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "删除",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    )
                    ClickableSettingItem(
                        title = "我的图标",
                        description = if (MainConfig.navIconMy.isNotEmpty()) "已设置自定义图标" else "使用默认图标",
                        onClick = {
                            editingNavIconDestination = "my"
                            navIconLauncher.launch("image/png")
                        },
                        trailingContent = {
                            if (MainConfig.navIconMy.isNotEmpty()) {
                                Row {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(2.dp)
                                    ) {
                                        val bmp = remember(MainConfig.navIconMy) {
                                            android.graphics.BitmapFactory.decodeFile(MainConfig.navIconMy)
                                        }
                                        if (bmp != null) {
                                            Icon(
                                                painter = androidx.compose.ui.graphics.painter.BitmapPainter(bmp.asImageBitmap()),
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = Color.Unspecified
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { MainConfig.navIconMy = "" },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "删除",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }

            item {
                SplicedColumnGroup(title = "自定义标签颜色设置") {
                    SwitchSettingItem(
                        title = "启用自定义标签颜色",
                        checked = enableCustomTagColors,
                        onCheckedChange = { ThemeConfig.enableCustomTagColors = it }
                    )

                    if (enableCustomTagColors) {
                        ClickableSettingItem(
                            title = "自动生成标签颜色",
                            description = "根据主题色自动生成一组标签颜色",
                            onClick = {
                                val baseColor = if (md3Primary != 0) Color(md3Primary) else primaryColor
                                val generatedColors = TagColorGenerator.generateTagColors(baseColor)
                                tagColors.clear()
                                tagColors.addAll(generatedColors)
                                ThemeConfig.saveCustomTagColors(tagColors.toList())
                                listVersion++
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "自动生成",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )

                        ClickableSettingItem(
                            title = "管理标签颜色",
                            description = if (tagColors.isEmpty()) "暂无自定义颜色" else "已设置 ${tagColors.size} 个颜色",
                            onClick = { showTagColorManagement = true }
                        )

                        if (tagColors.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                tagColors.forEach { colorPair ->
                                    Box(
                                        modifier = Modifier
                                            .width(48.dp)
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(colorPair.bgColor))
                                            .border(
                                                1.dp,
                                                Color(colorPair.textColor),
                                                RoundedCornerShape(8.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "A",
                                            color = Color(colorPair.textColor),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                SplicedColumnGroup(title = "主题管理") {
                    ClickableSettingItem(
                        title = stringResource(R.string.theme_list),
                        description = stringResource(R.string.theme_list_summary),
                        onClick = { showThemeListDialog = true }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.save_theme_config),
                        description = stringResource(R.string.save_day_theme_summary),
                        onClick = {
                            saveThemeKey = "savePersonalizationTheme"
                            themeName = ""
                        }
                    )
                }
            }
        }

    // 保存个性化主题设置
    fun savePersonalizationTheme(themeName: String) {
        PersonalizationThemeConfig.savePersonalizationTheme(themeName)
        listVersion++
    }

        ColorPickerSheet(
            show = showColorPicker,
            initialColor = when (currentColorKey) {
                "cMD3Primary" -> md3Primary
                "cMD3OnPrimary" -> md3OnPrimary
                "cMD3PrimaryContainer" -> md3PrimaryContainer
                "cMD3OnPrimaryContainer" -> md3OnPrimaryContainer
                "cMD3Secondary" -> md3Secondary
                "cMD3OnSecondary" -> md3OnSecondary
                "cMD3SecondaryContainer" -> md3SecondaryContainer
                "cMD3Tertiary" -> md3Tertiary
                "cMD3Error" -> md3Error
                "cMD3Surface" -> md3Surface
                "cMD3OnSurface" -> md3OnSurface
                "cMD3Background" -> md3Background
                "cMD3Outline" -> md3Outline
                "cMD3SurfaceContainerLow" -> md3SurfaceContainerLow
                "cMD3SurfaceVariant" -> md3SurfaceVariant
                "cTopBarColor" -> topBarColor
                "cNavBarColor" -> navBarColor
                "cFontColor" -> fontColor
                "cBgColor" -> bgColor
                "cBookInfoInputColor" -> bookInfoInputColor
                "containerBorderColor" -> containerBorderColor
                "itemDividerColor" -> itemDividerColor
                else -> 0
            },
            onDismissRequest = { showColorPicker = false },
            onColorSelected = {
                when (currentColorKey) {
                    "cMD3Primary" -> ThemeConfig.cMD3Primary = it
                    "cMD3OnPrimary" -> ThemeConfig.cMD3OnPrimary = it
                    "cMD3PrimaryContainer" -> ThemeConfig.cMD3PrimaryContainer = it
                    "cMD3OnPrimaryContainer" -> ThemeConfig.cMD3OnPrimaryContainer = it
                    "cMD3Secondary" -> ThemeConfig.cMD3Secondary = it
                    "cMD3OnSecondary" -> ThemeConfig.cMD3OnSecondary = it
                    "cMD3SecondaryContainer" -> ThemeConfig.cMD3SecondaryContainer = it
                    "cMD3Tertiary" -> ThemeConfig.cMD3Tertiary = it
                    "cMD3Error" -> ThemeConfig.cMD3Error = it
                    "cMD3Surface" -> ThemeConfig.cMD3Surface = it
                    "cMD3OnSurface" -> ThemeConfig.cMD3OnSurface = it
                    "cMD3Background" -> ThemeConfig.cMD3Background = it
                    "cMD3Outline" -> ThemeConfig.cMD3Outline = it
                    "cMD3SurfaceContainerLow" -> ThemeConfig.cMD3SurfaceContainerLow = it
                    "cMD3SurfaceVariant" -> ThemeConfig.cMD3SurfaceVariant = it
                    "cTopBarColor" -> ThemeConfig.cTopBarColor = it
                    "cNavBarColor" -> ThemeConfig.cNavBarColor = it
                    "cFontColor" -> ThemeConfig.cFontColor = it
                    "cBgColor" -> ThemeConfig.cBgColor = it
                    "cBookInfoInputColor" -> ThemeConfig.cBookInfoInputColor = it
                    "containerBorderColor" -> ThemeConfig.containerBorderColor = it
                    "itemDividerColor" -> ThemeConfig.itemDividerColor = it
                }
            }
        )

        PersonalizationThemeListDialog(
            show = showThemeListDialog,
            onDismissRequest = { showThemeListDialog = false },
            listVersion = listVersion
        )

        AppAlertDialog(
            data = saveThemeKey,
            onDismissRequest = { saveThemeKey = null },
            title = stringResource(R.string.theme_name),
            content = { _ ->
                AppTextField(
                    value = themeName,
                    onValueChange = { themeName = it },
                    label = "name",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = LegadoTheme.colorScheme.surface
                )
            },
            confirmText = stringResource(android.R.string.ok),
            onConfirm = { _ ->
                if (themeName.isNotBlank()) {
                    savePersonalizationTheme(themeName)
                }
                saveThemeKey = null
            },
            dismissText = stringResource(android.R.string.cancel),
            onDismiss = { saveThemeKey = null }
        )

        // 标签颜色管理对话框
        AppAlertDialog(
            data = if (showTagColorManagement) "tagColorManagement" else null,
            onDismissRequest = { showTagColorManagement = false },
            title = "管理标签颜色",
            content = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tagColors.size) { index ->
                        val colorPair = tagColors[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(colorPair.bgColor))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "标签 ${index + 1}",
                                color = Color(colorPair.textColor),
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                MediumOutlinedIconButton(
                                    onClick = {
                                        editingTagColorIndex = index
                                        editingTagTextColor = colorPair.textColor
                                        showTagColorPicker = true
                                    },
                                    imageVector = Icons.Default.Edit
                                )
                                MediumOutlinedIconButton(
                                    onClick = {
                                        tagColors.removeAt(index)
                                        ThemeConfig.saveCustomTagColors(tagColors.toList())
                                        listVersion++
                                    },
                                    imageVector = Icons.Default.Delete
                                )
                            }
                        }
                    }
                    item {
                        MediumOutlinedIconButton(
                            onClick = {
                                tagColors.add(TagColorPair(0, 0))
                                editingTagColorIndex = tagColors.size - 1
                                editingTagTextColor = 0
                                showTagColorPicker = true
                            },
                            imageVector = Icons.Default.Add,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmText = stringResource(android.R.string.ok),
            onConfirm = {
                ThemeConfig.saveCustomTagColors(tagColors.toList())
                listVersion++
                showTagColorManagement = false
            },
            dismissText = stringResource(android.R.string.cancel),
            onDismiss = { showTagColorManagement = false }
        )

        // 标签颜色选择器
        ColorPickerSheet(
            show = showTagColorPicker && editingTagColorIndex >= 0,
            initialColor = editingTagTextColor,
            onDismissRequest = { showTagColorPicker = false },
            onColorSelected = { selectedColor ->
                if (editingTagColorIndex >= 0 && editingTagColorIndex < tagColors.size) {
                    val hsl = FloatArray(3)
                    ColorUtils.colorToHSL(selectedColor, hsl)
                    hsl[1] = (hsl[1] * 0.4f).coerceAtMost(0.35f)
                    hsl[2] = 0.90f
                    val bgColor = Color.hsl(hsl[0], hsl[1], hsl[2]).toArgb()
                    tagColors[editingTagColorIndex] = TagColorPair(
                        textColor = selectedColor,
                        bgColor = bgColor
                    )
                    showTagColorPicker = false
                }
            }
        )
    }
}
