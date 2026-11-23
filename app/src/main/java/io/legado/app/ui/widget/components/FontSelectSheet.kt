package io.legado.app.ui.widget.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.R
import io.legado.app.help.importFontFiles
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.modalBottomSheet.SheetAction
import io.legado.app.ui.widget.components.modalBottomSheet.SheetMenuAction
import io.legado.app.utils.FileDoc
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.toastOnUi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 字体文件在各家 provider 上的 MIME 并不统一（有的直接给 application/octet-stream），
 * 只按字体 MIME 过滤会让用户“看不到自己的字体”，所以这里放开选择，
 * 导入时再按扩展名过滤。
 */
private val fontPickMimeTypes = arrayOf("*/*")

// 系统字体菜单的稳定 ID：字体名可能重复（同名不同文件），用序号定位。
private const val FontTypefaceIdPrefix = "font_typeface:"

@Composable
fun FontSelectSheet(
    show: Boolean = true,
    title: String,
    folderState: FontFolderState,
    selectedFontPath: String?,
    onDismissRequest: () -> Unit,
    onSelectFont: (FileDoc) -> Unit,
    onOpenFolderPicker: () -> Unit,
    startSheetAction: SheetAction? = null,
    folderIcon: ImageVector = Icons.Default.FolderOpen,
    folderContentDescription: String? = null,
    onSelectSystemTypeface: ((Int) -> Unit)? = null,
    systemTypefaces: Array<String>? = null,
    emptyText: String? = null,
) {
    val context = LocalContext.current
    val selectedFontName = remember(selectedFontPath) {
        selectedFontPath?.let {
            runCatching {
                val uri = it.toUri()
                if (uri.isContentScheme()) {
                    DocumentFile.fromSingleUri(context, uri)?.name
                } else {
                    File(uri.path ?: it).name
                }
            }.getOrNull()
        }
    }
    // 导入字体后递增，让字体网格重新扫描应用私有字体目录
    var reloadKey by remember { mutableIntStateOf(0) }
    // 在组合期取文案，回调里用 LocalContext 取资源会被 lint 判为不跟随配置变化
    val importDoneText = stringResource(R.string.font_import_done)
    val importNoneText = stringResource(R.string.font_import_none)
    val importScope = rememberCoroutineScope()
    val importFontLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        importScope.launch {
            val imported = withContext(Dispatchers.IO) { importFontFiles(context, uris) }
            context.toastOnUi(if (imported > 0) importDoneText else importNoneText)
            if (imported > 0) reloadKey++
        }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
        // 系统字体菜单：条目由传入的字体数组动态生成。字体名可能重复（同名不同文件），
        // 因此 ID 用序号而不是名字，点击时直接带上下标，不再需要回解字符串。
        startSheetActions = buildList {
            startSheetAction?.let(::add)
            val typefaces = systemTypefaces
            val selectTypeface = onSelectSystemTypeface
            if (typefaces != null && selectTypeface != null) {
                add(
                    SheetAction(
                        icon = Icons.Default.TextFields,
                        contentDescription = stringResource(R.string.select_font),
                        menu = typefaces.mapIndexed { index, name ->
                            SheetMenuAction(
                                id = "$FontTypefaceIdPrefix$index",
                                label = name,
                                onClick = {
                                    selectTypeface(index)
                                    onDismissRequest()
                                },
                            )
                        }.toImmutableList(),
                    )
                )
            }
        }.toImmutableList(),
        // 系统文件选择器返回的 tree URI 在部分国产 ROM 上查不到内容，直接导入字体文件
        // 不依赖目录遍历，是唯一在所有设备上都可靠的入口。
        endSheetActions = persistentListOf(
            SheetAction(
                onClick = { importFontLauncher.launch(fontPickMimeTypes) },
                icon = Icons.Default.FileOpen,
                contentDescription = stringResource(R.string.font_import),
            ),
            SheetAction(
                onClick = onOpenFolderPicker,
                icon = folderIcon,
                contentDescription = folderContentDescription
                    ?: stringResource(R.string.select_folder),
            ),
        ),
    ) {
        FontSelectGrid(
            folderState = folderState,
            selectedFontName = selectedFontName,
            onSelectFont = { doc ->
                onSelectFont(doc)
                onDismissRequest()
            },
            emptyText = emptyText,
            reloadKey = reloadKey,
        )
    }
}
