package io.legado.app.ui.config.personalizationConfig

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.help.loadFontFiles
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.utils.FileDoc
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import io.legado.app.utils.takePersistablePermissionSafely

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontSelectScreen(
    onBackClick: () -> Unit
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val context = LocalContext.current
    var fontItems by remember { mutableStateOf<List<FileDoc>>(emptyList()) }
    var selectedFolderUri by remember { mutableStateOf<Uri?>(null) }

    // 加载字体文件
    fun loadFonts() {
        fontItems = loadFontFiles(context, selectedFolderUri)
    }

    // 从偏好设置中加载保存的字体文件夹路径
    remember {
        val savedFontPath = context.getPrefString(PreferKey.fontFolder)
        if (!savedFontPath.isNullOrEmpty()) {
            selectedFolderUri = Uri.parse(savedFontPath)
        }
        loadFonts()
    }

    // 文件夹选择启动器
    val selectFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            selectedFolderUri = uri
            // 获取持久化权限
            uri.takePersistablePermissionSafely(context, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // 保存选择的文件夹路径到偏好设置
            context.putPrefString(PreferKey.fontFolder, uri.toString())
            loadFonts()
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.font_setting),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        if (fontItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "没有字体文件",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(onClick = { selectFolderLauncher.launch(null) }) {
                        Text(text = stringResource(R.string.select_font))
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = adaptiveContentPadding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(fontItems) {
                    FontItem(it) {
                        // 选择字体的逻辑
                        ThemeConfig.appFontPath = it.uri.toString()
                    }
                }
            }
        }
    }
}

@Composable
fun FontItem(fontDoc: FileDoc, onFontSelected: (FileDoc) -> Unit) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        onClick = {
            onFontSelected(fontDoc)
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        // 使用AndroidView显示使用对应字体的文本
        AndroidView(
            factory = { ctx ->
                android.widget.TextView(ctx).apply {
                    text = fontDoc.name
                    textSize = 16f
                    // 设置垂直和水平居中
                    gravity = android.view.Gravity.CENTER
                    // 设置最大行数为2，超过省略
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    // 加载并设置字体
                    runCatching {
                        val typeface: Typeface? = if (fontDoc.uri.scheme == "content") {
                            ctx.contentResolver.openFileDescriptor(fontDoc.uri, "r")?.use {
                                Typeface.Builder(it.fileDescriptor).build()
                            }
                        } else {
                            Typeface.createFromFile(fontDoc.uri.path!!)
                        }
                        this.typeface = typeface
                    }.onFailure {
                        // 字体加载失败，使用默认字体
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

