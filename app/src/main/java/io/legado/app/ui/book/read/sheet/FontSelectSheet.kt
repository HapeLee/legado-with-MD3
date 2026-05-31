package io.legado.app.ui.book.read.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.utils.getPrefString
import java.io.File
import java.net.URLDecoder
import io.legado.app.ui.widget.components.FontSelectSheet as SharedFontSelectSheet

@Composable
fun FontSelectSheet(
    onDismissRequest: () -> Unit,
    onSelectFont: (String) -> Unit,
    onSelectSystemTypeface: (Int) -> Unit,
    onOpenFolderPicker: () -> Unit,
) {
    val context = LocalContext.current
    val fontFolderUri = remember {
        context.getPrefString(PreferKey.fontFolder)?.toUri()
    }
    val curFontPath = remember { ReadBookConfig.textFont }
    val curName = remember {
        runCatching {
            URLDecoder.decode(curFontPath, "utf-8")
        }.getOrNull()?.substringAfterLast(File.separator)
    }
    val systemTypefaces = remember {
        context.resources.getStringArray(R.array.system_typefaces)
    }

    SharedFontSelectSheet(
        title = stringResource(R.string.select_font),
        fontFolderUri = fontFolderUri,
        selectedFontName = curName,
        showPreview = false,
        onDismissRequest = onDismissRequest,
        onSelectFont = { doc -> onSelectFont(doc.toString()) },
        onOpenFolderPicker = onOpenFolderPicker,
        onSelectSystemTypeface = onSelectSystemTypeface,
        systemTypefaces = systemTypefaces,
    )
}
