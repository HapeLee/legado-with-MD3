package io.legado.app.ui.book.read.sheet

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.HighlightRule
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.data.repository.configNames
import io.legado.app.data.repository.toJsonArray
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.FontFolderState
import io.legado.app.ui.widget.components.FontSelectSheet
import io.legado.app.ui.widget.components.SectionTitle
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyColorSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import io.legado.app.utils.toastOnUi
import java.io.File

@Composable
fun HighlightRuleEditSheet(
    show: Boolean,
    rule: HighlightRule?,
    onDismissRequest: () -> Unit,
    onSave: (HighlightRule) -> Unit,
) {
    val isNew = rule == null
    val initial = remember(show, rule) { rule ?: HighlightRule() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Rule info state
    var pattern by remember(show, rule) { mutableStateOf(initial.pattern) }
    var name by remember(show, rule) { mutableStateOf(initial.name) }
    var targetScope by remember(show, rule) { mutableIntStateOf(initial.targetScope) }
    var enabled by remember(show, rule) { mutableStateOf(initial.enabled) }
    var sampleText by remember(show, rule) {
        mutableStateOf(initial.sampleText.ifBlank { "她轻声说：今晚就出发。" })
    }

    // Style state
    var textColor by remember(show, rule) {
        mutableIntStateOf(
            initial.textColor ?: 0xFF63C37D.toInt()
        )
    }
    var hasTextColor by remember(show, rule) { mutableStateOf(initial.textColor != null) }
    var bgColor by remember(show, rule) { mutableIntStateOf(initial.bgColor ?: 0x20FFEB3B) }
    var hasBgColor by remember(show, rule) { mutableStateOf(initial.bgColor != null) }
    var hasUnderline by remember(show, rule) { mutableStateOf(initial.underlineMode > 0) }
    var underlineMode by remember(
        show,
        rule
    ) { mutableIntStateOf(if (initial.underlineMode > 0) initial.underlineMode else 1) }
    var underlineColor by remember(show, rule) {
        mutableIntStateOf(
            initial.underlineColor ?: 0xFF63C37D.toInt()
        )
    }
    var hasUnderlineColor by remember(show, rule) { mutableStateOf(initial.underlineColor != null) }
    var underlineWidth by remember(show, rule) { mutableFloatStateOf(initial.underlineWidth) }
    var underlineOffset by remember(show, rule) { mutableFloatStateOf(initial.underlineOffset) }
    var underlineSvgPath by remember(
        show,
        rule
    ) { mutableStateOf(initial.underlineSvgPath.orEmpty()) }
    var bgImage by remember(show, rule) { mutableStateOf(initial.bgImage.orEmpty()) }
    var bgImageFit by remember(show, rule) { mutableIntStateOf(initial.bgImageFit) }
    var bgImageScale by remember(show, rule) { mutableFloatStateOf(initial.bgImageScale) }
    var hasBgImage by remember(show, rule) { mutableStateOf(initial.bgImage?.isNotBlank() == true) }

    // Config binding state — empty set = global (applies to all configs)
    val allConfigNames = remember { ReadBookConfig.configList.map { it.name }.filter { it.isNotBlank() } }
    var configNames by remember(show, rule) {
        mutableStateOf(initial.configName.orEmpty().configNames().toSet())
    }

    // Font state
    var hasFont by remember(show, rule) { mutableStateOf(initial.fontPath?.isNotBlank() == true) }
    var fontPath by remember(show, rule) { mutableStateOf(initial.fontPath.orEmpty()) }

    // Color picker state
    var showTextColorPicker by remember(show, rule) { mutableStateOf(false) }
    var showBgColorPicker by remember(show, rule) { mutableStateOf(false) }
    var showUnderlineColorPicker by remember(show, rule) { mutableStateOf(false) }
    var showFontSelect by remember(show, rule) { mutableStateOf(false) }

    // Validation
    var patternError by remember(show, rule) { mutableStateOf<String?>(null) }

    // System photo picker, with the contract's built-in fallback on older devices.
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val dir = File(appCtx.filesDir, "bg_images")
                        if (!dir.exists()) dir.mkdirs()
                        val displayName = context.contentResolver.query(
                            uri,
                            arrayOf(OpenableColumns.DISPLAY_NAME),
                            null,
                            null,
                            null,
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                    .takeIf { it >= 0 }
                                    ?.let(cursor::getString)
                            } else {
                                null
                            }
                        }
                        val suffix = when {
                            displayName?.endsWith(".9.png", ignoreCase = true) == true -> ".9.png"
                            displayName?.substringAfterLast('.', "").isNullOrBlank() -> ".img"
                            else -> ".${displayName.substringAfterLast('.')}"
                        }
                        val target = File(dir, "bg_${System.currentTimeMillis()}$suffix")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            target.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        } ?: throw java.io.FileNotFoundException("Open input stream failed")
                        target.absolutePath
                    }
                }.onSuccess { path ->
                    bgImage = path
                }.onFailure { throwable ->
                    context.toastOnUi(R.string.error)
                    AppLog.put("选择高亮背景图失败", throwable)
                }
            }
        }
    }

    val titleRes = if (isNew) R.string.new_rule else R.string.edit_rule

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(titleRes),
        endAction = {
            androidx.compose.material3.IconButton(onClick = {
                if (pattern.isNotBlank()) {
                    val result = runCatching { Regex(pattern) }
                    if (result.isFailure) {
                        patternError = result.exceptionOrNull()?.message
                        return@IconButton
                    }
                }
                patternError = null
                onSave(
                    HighlightRule(
                        id = initial.id,
                        name = name,
                        pattern = pattern,
                        sampleText = sampleText,
                        targetScope = targetScope,
                        enabled = enabled,
                        position = initial.position,
                        textColor = if (hasTextColor) textColor else null,
                        bgColor = if (hasBgColor) bgColor else null,
                        underlineMode = if (hasUnderline) underlineMode else 0,
                        underlineColor = if (hasUnderlineColor && hasUnderline) underlineColor else null,
                        underlineWidth = underlineWidth,
                        underlineOffset = underlineOffset,
                        underlineSvgPath = underlineSvgPath.ifBlank { null },
                        bgImage = if (hasBgImage) bgImage.ifBlank { null } else null,
                        bgImageFit = bgImageFit,
                        bgImageScale = bgImageScale,
                        configName = if (configNames.isEmpty()) null else configNames.toList().toJsonArray(),
                        fontPath = if (hasFont) fontPath.ifBlank { null } else null,
                    )
                )
            }) {
                androidx.compose.material3.Icon(
                    Icons.Default.Done,
                    contentDescription = null,
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // === Section 1: Rule Info ===
            SectionTitle(stringResource(R.string.rule_info))

            AppTextField(
                value = pattern,
                onValueChange = {
                    pattern = it
                    patternError = null
                },
                label = stringResource(R.string.rule_pattern),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = patternError != null,
                supportingText = patternError?.let {
                    { AppText(it, color = MaterialTheme.colorScheme.error) }
                },
            )

            Spacer(Modifier.height(8.dp))

            AppTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.rule_name),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            val scopeEntries = arrayOf(
                stringResource(R.string.target_all),
                stringResource(R.string.target_title),
                stringResource(R.string.target_body),
            )
            val scopeValues = arrayOf(
                HighlightRule.TARGET_ALL.toString(),
                HighlightRule.TARGET_TITLE.toString(),
                HighlightRule.TARGET_BODY.toString(),
            )
            TinyDropdownSettingItem(
                title = stringResource(R.string.target_scope),
                selectedValue = targetScope.toString(),
                displayEntries = scopeEntries,
                entryValues = scopeValues,
                onValueChange = { targetScope = it.toIntOrNull() ?: HighlightRule.TARGET_ALL },
            )

            TinySwitchSettingItem(
                title = stringResource(R.string.enable_rule),
                checked = enabled,
                onCheckedChange = { enabled = it },
            )

            // === Section 2: Style Settings ===
            SectionTitle(stringResource(R.string.style_settings))

            // Text color
            TinySwitchSettingItem(
                title = stringResource(R.string.text_color),
                checked = hasTextColor,
                onCheckedChange = { hasTextColor = it },
            )
            if (hasTextColor) {
                TinyColorSettingItem(
                    title = stringResource(R.string.select_color),
                    colorValue = textColor,
                    onClick = { showTextColorPicker = true },
                )
            }

            // Underline
            TinySwitchSettingItem(
                title = stringResource(R.string.underline_style),
                checked = hasUnderline,
                onCheckedChange = { hasUnderline = it },
            )
            if (hasUnderline) {
                val underlineEntries = arrayOf(
                    stringResource(R.string.underline_solid),
                    stringResource(R.string.underline_dashed),
                    stringResource(R.string.underline_wave),
                    stringResource(R.string.underline_title_bar),
                    stringResource(R.string.underline_svg),
                )
                val underlineValues = arrayOf("1", "2", "3", "4", "5")
                TinyDropdownSettingItem(
                    title = stringResource(R.string.underline_style),
                    selectedValue = underlineMode.toString(),
                    displayEntries = underlineEntries,
                    entryValues = underlineValues,
                    onValueChange = { underlineMode = it.toIntOrNull() ?: 1 },
                )

                TinySwitchSettingItem(
                    title = stringResource(R.string.underline_color),
                    checked = hasUnderlineColor,
                    onCheckedChange = { hasUnderlineColor = it },
                )
                if (hasUnderlineColor) {
                    TinyColorSettingItem(
                        title = stringResource(R.string.select_color),
                        colorValue = underlineColor,
                        onClick = { showUnderlineColorPicker = true },
                    )
                }

                TinySliderSettingItem(
                    title = stringResource(R.string.underline_width),
                    value = underlineWidth,
                    valueRange = 0.1f..10f,
                    description = String.format("%.1f dp", underlineWidth),
                    onValueChange = { underlineWidth = (it * 10).toInt() / 10f },
                )

                TinySliderSettingItem(
                    title = stringResource(R.string.underline_offset),
                    value = underlineOffset,
                    valueRange = 0f..20f,
                    description = String.format("%.1f dp", underlineOffset),
                    onValueChange = { underlineOffset = (it * 10).toInt() / 10f },
                )

                if (underlineMode == 5) {
                    AppTextField(
                        value = underlineSvgPath,
                        onValueChange = { underlineSvgPath = it },
                        label = stringResource(R.string.svg_path),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Background color
            TinySwitchSettingItem(
                title = stringResource(R.string.bg_color),
                checked = hasBgColor,
                onCheckedChange = { hasBgColor = it },
            )
            if (hasBgColor) {
                TinyColorSettingItem(
                    title = stringResource(R.string.select_color),
                    colorValue = bgColor,
                    onClick = { showBgColorPicker = true },
                )
            }

            // Background image
            TinySwitchSettingItem(
                title = stringResource(R.string.highlight_bg_image),
                checked = hasBgImage,
                onCheckedChange = { hasBgImage = it },
            )
            if (hasBgImage) {
                TinyClickableSettingItem(
                    title = stringResource(R.string.highlight_bg_image),
                    description = bgImage.ifBlank { null }?.let { File(it).name },
                    onClick = {
                        imagePicker.launch("image/*")
                    },
                )
            }
            if (hasBgImage && bgImage.isNotBlank()) {
                val fitEntries = arrayOf(
                    stringResource(R.string.bg_fit_tile),
                    stringResource(R.string.bg_fit_stretch),
                    stringResource(R.string.bg_fit_crop),
                )
                val fitValues = arrayOf("0", "1", "2")
                TinyDropdownSettingItem(
                    title = stringResource(R.string.bg_image_fit),
                    selectedValue = bgImageFit.toString(),
                    displayEntries = fitEntries,
                    entryValues = fitValues,
                    onValueChange = { bgImageFit = it.toIntOrNull() ?: 0 },
                )

                TinySliderSettingItem(
                    title = stringResource(R.string.highlight_bg_image_scale),
                    value = bgImageScale,
                    valueRange = 0.1f..5f,
                    description = String.format("%.1fx", bgImageScale),
                    onValueChange = { bgImageScale = (it * 10).toInt() / 10f },
                )
            }

            // === Section 3: Config Binding ===
            if (allConfigNames.isNotEmpty()) {
                SectionTitle("应用排版")
                LazyRow(
                    modifier = Modifier.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Global toggle
                    item {
                        val selected = configNames.isEmpty()
                        val bg = if (selected) LegadoTheme.colorScheme.secondaryContainer
                        else LegadoTheme.colorScheme.surfaceContainerLow
                        val fg = if (selected) LegadoTheme.colorScheme.onSecondaryContainer
                        else LegadoTheme.colorScheme.onSurfaceVariant
                        NormalCard(
                            onClick = { configNames = emptySet() },
                            containerColor = bg,
                            cornerRadius = 8.dp,
                        ) {
                            AppText(
                                "全局",
                                style = LegadoTheme.typography.labelMedium,
                                color = fg,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    itemsIndexed(allConfigNames) { _, cn ->
                        val selected = cn in configNames
                        val bg = if (selected) LegadoTheme.colorScheme.secondaryContainer
                        else LegadoTheme.colorScheme.surfaceContainerLow
                        val fg = if (selected) LegadoTheme.colorScheme.onSecondaryContainer
                        else LegadoTheme.colorScheme.onSurfaceVariant
                        NormalCard(
                            onClick = {
                                configNames = if (selected) configNames - cn
                                else configNames + cn
                            },
                            containerColor = bg,
                            cornerRadius = 8.dp,
                        ) {
                            AppText(
                                cn,
                                style = LegadoTheme.typography.labelMedium,
                                color = fg,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            // === Section 4: Font ===
            SectionTitle("字体替换")
            TinySwitchSettingItem(
                title = "自定义字体",
                checked = hasFont,
                onCheckedChange = { hasFont = it },
            )
            if (hasFont) {
                TinyClickableSettingItem(
                    title = stringResource(R.string.select_font),
                    description = fontPath.ifBlank { null }?.let { File(it).name },
                    onClick = { showFontSelect = true },
                )
            }

            // === Section 5: Preview ===
            SectionTitle(stringResource(R.string.preview_effect))

            AppTextField(
                value = sampleText,
                onValueChange = { sampleText = it },
                label = stringResource(R.string.sample_text),
                modifier = Modifier.fillMaxWidth(),
            )

            HighlightRulePreview(
                sampleText = sampleText,
                textColor = if (hasTextColor) textColor else null,
                bgColor = if (hasBgColor) bgColor else null,
                underlineMode = if (hasUnderline) underlineMode else 0,
                underlineColor = if (hasUnderlineColor && hasUnderline) underlineColor else null,
                underlineWidth = underlineWidth,
                underlineOffset = underlineOffset,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(top = 8.dp),
            )
        }
    }

    // Color pickers
    ColorPickerSheet(
        show = showTextColorPicker,
        initialColor = textColor,
        onDismissRequest = { showTextColorPicker = false },
        onColorSelected = { color ->
            textColor = color
            showTextColorPicker = false
        },
    )
    ColorPickerSheet(
        show = showBgColorPicker,
        initialColor = bgColor,
        onDismissRequest = { showBgColorPicker = false },
        onColorSelected = { color ->
            bgColor = color
            showBgColorPicker = false
        },
    )
    ColorPickerSheet(
        show = showUnderlineColorPicker,
        initialColor = underlineColor,
        onDismissRequest = { showUnderlineColorPicker = false },
        onColorSelected = { color ->
            underlineColor = color
            showUnderlineColorPicker = false
        },
    )

    // Font selector
    val readSettingsRepository: ReadSettingsRepository = org.koin.compose.koinInject()
    val fontSelectScope = rememberCoroutineScope()
    val fontSelectPreferences by readSettingsRepository.preferences.collectAsStateWithLifecycle(
        initialValue = null
    )
    val fontFolderState = remember(fontSelectPreferences) {
        val pref = fontSelectPreferences
        if (pref == null) {
            FontFolderState.Loading
        } else {
            FontFolderState.Loaded(pref.fontFolder.takeIf { it.isNotEmpty() }?.toUri())
        }
    }
    val systemTypefaces = stringArrayResource(R.array.system_typefaces)
    val fontFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            fontSelectScope.launch {
                readSettingsRepository.setFontFolder(it.toString())
            }
        }
    }
    FontSelectSheet(
        show = showFontSelect,
        title = stringResource(R.string.select_font),
        folderState = fontFolderState,
        selectedFontPath = fontPath,
        onDismissRequest = { showFontSelect = false },
        onSelectFont = { fontPath = it.uri.toString(); showFontSelect = false },
        onSelectSystemTypeface = { fontPath = ""; showFontSelect = false },
        onOpenFolderPicker = { fontFolderLauncher.launch(null) },
        systemTypefaces = systemTypefaces,
    )
}

@Composable
private fun HighlightRulePreview(
    sampleText: String,
    textColor: Int?,
    bgColor: Int?,
    underlineMode: Int,
    underlineColor: Int?,
    underlineWidth: Float,
    underlineOffset: Float,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val defaultTextColor = LegadoTheme.colorScheme.onSurface
    val resolvedTextColor = textColor?.let { Color(it) } ?: defaultTextColor
    val resolvedUnderlineColor = underlineColor?.let { Color(it) } ?: resolvedTextColor

    val textStyle = TextStyle(
        fontSize = 16.sp,
        color = resolvedTextColor,
    )

    Canvas(modifier = modifier) {
        val textResult = textMeasurer.measure(
            text = sampleText,
            style = textStyle,
            maxLines = 3,
        )
        if (bgColor != null) {
            drawRect(
                color = Color(bgColor),
                topLeft = Offset(0f, 0f),
                size = size.copy(height = textResult.size.height.toFloat()),
            )
        }
        drawText(textResult)

        if (underlineMode > 0) {
            val strokeWidth = underlineWidth.dp.toPx()
            val yBaseline = textResult.size.height.toFloat() - underlineOffset.dp.toPx()

            when (underlineMode) {
                1 -> {
                    drawLine(
                        color = resolvedUnderlineColor,
                        start = Offset(0f, yBaseline),
                        end = Offset(textResult.size.width.toFloat(), yBaseline),
                        strokeWidth = strokeWidth,
                    )
                }
                2 -> {
                    val dashLength = 8.dp.toPx()
                    val gapLength = 4.dp.toPx()
                    var x = 0f
                    while (x < textResult.size.width) {
                        val endX = minOf(x + dashLength, textResult.size.width.toFloat())
                        drawLine(
                            color = resolvedUnderlineColor,
                            start = Offset(x, yBaseline),
                            end = Offset(endX, yBaseline),
                            strokeWidth = strokeWidth,
                        )
                        x += dashLength + gapLength
                    }
                }
                3 -> {
                    val amplitude = 2.dp.toPx()
                    val period = 12.dp.toPx()
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, yBaseline)
                        var x = 0f
                        while (x < textResult.size.width) {
                            val nextX = minOf(x + period / 2, textResult.size.width.toFloat())
                            val controlY = if ((x / period).toInt() % 2 == 0) {
                                yBaseline - amplitude
                            } else {
                                yBaseline + amplitude
                            }
                            quadraticTo(x, controlY, nextX, yBaseline)
                            x += period / 2
                        }
                    }
                    drawPath(
                        path = path,
                        color = resolvedUnderlineColor,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
                4 -> {
                    val barHeight = 3.dp.toPx()
                    drawLine(
                        color = resolvedUnderlineColor,
                        start = Offset(0f, yBaseline),
                        end = Offset(textResult.size.width.toFloat(), yBaseline),
                        strokeWidth = barHeight,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}
