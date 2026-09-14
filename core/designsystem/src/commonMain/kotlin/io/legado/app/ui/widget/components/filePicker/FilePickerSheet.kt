package io.legado.app.ui.widget.components.filePicker

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import io.legado.app.core.designsystem.res.Res
import io.legado.app.core.designsystem.res.manual_input
import io.legado.app.core.designsystem.res.multi_select_items
import io.legado.app.core.designsystem.res.select_operation
import io.legado.app.core.designsystem.res.sys_file_picker
import io.legado.app.core.designsystem.res.sys_folder_picker
import io.legado.app.core.designsystem.res.upload_url
import io.legado.app.core.platform.MimeTypeResolverProvider
import io.legado.app.ui.widget.components.modalBottomSheet.OptionCard
import io.legado.app.ui.widget.components.modalBottomSheet.OptionSheet
import org.jetbrains.compose.resources.stringResource

enum class FilePickerSheetMode {
    DIR, FILE, EXPORT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePickerSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String = stringResource(Res.string.select_operation),
    onSelectSysDir: (() -> Unit)? = null,
    onSelectSysFile: ((Array<String>) -> Unit)? = null,
    onSelectSysFiles: ((Array<String>) -> Unit)? = null,
    onManualInput: (() -> Unit)? = null,
    onUpload: (() -> Unit)? = null,
    allowExtensions: Array<String>? = null,
) {
    OptionSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title
    ) {
        onSelectSysDir?.let {
            OptionCard(
                icon = Icons.Default.FolderOpen,
                text = stringResource(Res.string.sys_folder_picker),
                onClick = it
            )
        }

        onSelectSysFile?.let {
            OptionCard(
                icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                text = stringResource(Res.string.sys_file_picker),
                onClick = { it(typesOfExtensions(allowExtensions)) }
            )
        }

        onSelectSysFiles?.let {
            OptionCard(
                icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                text = stringResource(Res.string.multi_select_items),
                onClick = { it(typesOfExtensions(allowExtensions)) }
            )
        }

        onManualInput?.let {
            OptionCard(
                icon = Icons.Default.EditNote,
                text = stringResource(Res.string.manual_input),
                onClick = it
            )
        }

        onUpload?.let {
            OptionCard(
                icon = Icons.Default.CloudUpload,
                text = stringResource(Res.string.upload_url),
                onClick = it
            )
        }
    }
}

/**
 * 把调用方给的扩展名列表翻译成系统文件选择器要的 MIME 数组。
 *
 * 全仓 22 个调用点的入参只有三种：`null`、`arrayOf("json")`、`arrayOf("json", "txt")`，
 * 所以实际产出的集合只有三种取值（函数体里逐字可见）：不过滤的全通配、`application/json`
 * （`"json"` 走平台查表，并未特判）与文本通配。
 *
 * M1-3n：原先直接调 `android.webkit.MimeTypeMap`（Android 独有），本函数因此把这个组件
 * 钉在 `:core:ui`。现改走 [MimeTypeResolverProvider] 窄契约，语义**逐字保持**：
 *
 * - 未注入解析器（desktop / iOS，那里也没有系统文件选择器）⇒ 一律 `null`
 *   ⇒ 回落 `application/octet-stream`，与 Android 上「未知扩展名」是同一条路径，不会崩；
 * - Android 由 `io.legado.app.platform.AndroidPlatformCapabilities.mimeTypeResolver()`
 *   注入，委托 `MimeTypeMap.getSingleton()`，行为与迁移前完全一致。
 *
 * `internal` 而非 `private`：便于 `commonTest` 直接钉住上面这张映射表（含 `*` / `txt` /
 * `xml` 的特判与回落路径），避免将来「顺手改一下」时静默改变用户看到的文件过滤结果。
 */
internal fun typesOfExtensions(allowExtensions: Array<String>?): Array<String> {
    val mimeTypeResolver = MimeTypeResolverProvider.current
    val types = hashSetOf<String>()
    if (allowExtensions.isNullOrEmpty()) {
        types.add("*/*")
    } else {
        allowExtensions.forEach {
            when (it) {
                "*" -> types.add("*/*")
                "txt", "xml" -> types.add("text/*")
                else -> types.add(
                    mimeTypeResolver.mimeTypeOf(it) ?: "application/octet-stream"
                )
            }
        }
    }
    return types.toTypedArray()
}
