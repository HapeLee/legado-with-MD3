package io.legado.app.ui.config.themePack

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.legado.app.R
import io.legado.app.help.config.ThemePackConfig
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePackScreen(
    onBackClick: () -> Unit
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var listVersion by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newThemeName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ThemePackConfig.ThemePack?>(null) }
    var applyTarget by remember { mutableStateOf<ThemePackConfig.ThemePack?>(null) }
    var exportingPack by remember { mutableStateOf<ThemePackConfig.ThemePack?>(null) }

    val packList = remember(listVersion) { ThemePackConfig.packList.toList() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            val pack = exportingPack ?: return@let
            exportingPack = null
            scope.launch(Dispatchers.IO) {
                kotlin.runCatching {
                    val tempFile = File(context.cacheDir, "theme_export_${pack.name}.zip")
                    ThemePackConfig.exportToZip(pack, tempFile)
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        tempFile.inputStream().use { it.copyTo(os) }
                    }
                    tempFile.delete()
                    withContext(Dispatchers.Main) {
                        context.toastOnUi("导出成功")
                    }
                }.onFailure {
                    withContext(Dispatchers.Main) {
                        context.toastOnUi("导出失败: ${it.message}")
                    }
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                kotlin.runCatching {
                    val tempFile = File(context.cacheDir, "theme_import_${System.currentTimeMillis()}.zip")
                    context.contentResolver.openInputStream(it)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    val pack = ThemePackConfig.importFromZip(tempFile)
                    tempFile.delete()
                    if (pack != null) {
                        listVersion++
                        withContext(Dispatchers.Main) {
                            context.toastOnUi("导入成功: ${pack.name}")
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            context.toastOnUi("导入失败: 无效的主题包")
                        }
                    }
                }.onFailure {
                    withContext(Dispatchers.Main) {
                        context.toastOnUi("导入失败: ${it.message}")
                    }
                }
            }
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.theme_pack),
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
                        title = "新增主题",
                        description = "保存当前设置为新主题",
                        onClick = {
                            newThemeName = ""
                            showAddDialog = true
                        }
                    )
                    ClickableSettingItem(
                        title = "导入主题",
                        description = "从zip包导入主题",
                        onClick = {
                            importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                        }
                    )
                }
            }

            if (packList.isNotEmpty()) {
                item {
                    AppText(
                        text = "主题列表",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                    )
                }

                items(packList, key = { it.folderName }) { pack ->
                    ThemePackItem(
                        pack = pack,
                        onApply = { applyTarget = pack },
                        onExport = { exportingPack = pack },
                        onDelete = { deleteTarget = pack }
                    )
                }
            }
        }
    }

    AppAlertDialog(
        data = if (showAddDialog) "add" else null,
        onDismissRequest = { showAddDialog = false },
        title = "新增主题",
        confirmText = "保存",
        onConfirm = {
            if (newThemeName.isNotBlank()) {
                scope.launch(Dispatchers.IO) {
                    ThemePackConfig.createFromCurrent(newThemeName)
                    listVersion++
                }
                showAddDialog = false
            }
        },
        dismissText = "取消",
        onDismiss = { showAddDialog = false },
        content = {
            io.legado.app.ui.widget.components.AppTextField(
                value = newThemeName,
                onValueChange = { newThemeName = it },
                placeholder = { AppText(text = "请输入主题名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )

    AppAlertDialog(
        data = applyTarget,
        onDismissRequest = { applyTarget = null },
        title = "应用主题",
        confirmText = "应用",
        onConfirm = { pack ->
            scope.launch(Dispatchers.IO) {
                ThemePackConfig.applyPack(pack)
                withContext(Dispatchers.Main) {
                    context.toastOnUi("主题已应用，请重启生效")
                }
            }
            applyTarget = null
        },
        dismissText = "取消",
        onDismiss = { applyTarget = null },
        text = "确定应用主题「${applyTarget?.name}」？应用后需要重启才能完全生效。"
    )

    AppAlertDialog(
        data = deleteTarget,
        onDismissRequest = { deleteTarget = null },
        title = "删除主题",
        confirmText = "删除",
        onConfirm = { pack ->
            scope.launch(Dispatchers.IO) {
                ThemePackConfig.deletePack(pack)
                listVersion++
            }
            deleteTarget = null
        },
        dismissText = "取消",
        onDismiss = { deleteTarget = null },
        text = "确定删除主题「${deleteTarget?.name}」？此操作不可恢复。"
    )

    LaunchedEffect(exportingPack) {
        exportingPack?.let { pack ->
            kotlin.runCatching {
                exportLauncher.launch("${pack.name}.zip")
            }.onFailure {
                exportingPack = null
                context.toastOnUi("导出失败: ${it.message}")
            }
        }
    }
}

@Composable
private fun ThemePackItem(
    pack: ThemePackConfig.ThemePack,
    onApply: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    GlassCard(
        onClick = onApply,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        cornerRadius = 20.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            val packDir = remember { ThemePackConfig.getPackDir(pack) }
            val bgFile = remember(pack.folderName) {
                packDir.listFiles()?.firstOrNull {
                    it.isFile && (it.name.startsWith("bg_light") || it.name.startsWith("bg_dark"))
                }
            }
            val coverFile = remember(pack.folderName) {
                packDir.listFiles()?.firstOrNull {
                    it.isFile && it.name.startsWith("cover_day_")
                }
            }
            val previewFile = bgFile ?: coverFile

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(
                        if (pack.themeColor != 0) Color(pack.themeColor)
                        else MaterialTheme.colorScheme.primary
                    )
            ) {
                if (previewFile != null) {
                    AsyncImage(
                        model = previewFile,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    AppText(
                        text = pack.name,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    val hasCustomIcon = pack.navIconBookshelf.isNotEmpty() ||
                            pack.navIconExplore.isNotEmpty() ||
                            pack.navIconRss.isNotEmpty() ||
                            pack.navIconMy.isNotEmpty()
                    val hasCover = pack.defaultCover.isNotEmpty() || pack.defaultCoverDark.isNotEmpty()
                    val features = mutableListOf<String>()
                    if (pack.appFontPath != null) features.add("应用字体")
                    if (pack.bgImageLight != null || pack.bgImageDark != null) features.add("应用背景")
                    if (hasCover) features.add("封面图片")
                    if (hasCustomIcon) features.add("图标")
                    if (features.isNotEmpty()) {
                        AppText(
                            text = features.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onExport) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "导出",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
