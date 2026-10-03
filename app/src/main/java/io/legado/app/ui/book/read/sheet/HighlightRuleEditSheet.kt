package io.legado.app.ui.book.read.sheet

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.HighlightRule
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.data.repository.configNames
import io.legado.app.data.repository.toJsonArray
import io.legado.app.feature.reader.core.model.ReaderTextBackgroundRun
import io.legado.app.feature.reader.core.model.contentClipRect
import io.legado.app.feature.reader.drawTextBackground
import io.legado.app.feature.reader.platform.ReaderTextBackgroundLoader
import io.legado.app.ui.book.read.ReadSheetConfigUiState
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.FontFolderState
import io.legado.app.ui.widget.components.FontSelectSheet
import io.legado.app.ui.widget.components.SectionTitle
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyColorSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.dpToPx
import io.legado.app.utils.spToPx
import io.legado.app.utils.toastOnUi
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalWindowInfo
import io.legado.app.feature.reader.core.layout.ReaderChapterBlockMeasurer
import io.legado.app.feature.reader.core.layout.ReaderChapterMeasureResult
import io.legado.app.feature.reader.core.layout.ReaderChapterMeasureStyle
import io.legado.app.feature.reader.core.layout.ReaderImageDimensionsResolver
import io.legado.app.feature.reader.core.layout.ReaderPaginationConfig
import io.legado.app.feature.reader.core.layout.ReaderPaginator
import io.legado.app.feature.reader.core.layout.ReaderTextAlignment
import io.legado.app.feature.reader.core.layout.ReaderTextShaperFactory
import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import io.legado.app.feature.reader.core.model.textBackgroundRuns
import io.legado.app.feature.reader.core.source.ReaderChapterSource
import io.legado.app.feature.reader.core.source.ReaderChapterSourceBlock
import io.legado.app.feature.reader.core.style.ReaderStyleTarget
import io.legado.app.feature.reader.core.style.mergeBackgroundBounds
import io.legado.app.feature.reader.legacy.LegacyReaderPaginationStyleFactory
import io.legado.app.feature.reader.legacy.LegacyReaderStyleRangeMapper
import io.legado.app.feature.reader.platform.AndroidReaderTextShaper
import io.legado.app.feature.reader.platform.ReaderAndroidPaintFactory
import io.legado.app.feature.reader.platform.ReaderPageDecorationDrawCache

@Composable
fun HighlightRuleEditSheet(
    show: Boolean,
    rule: HighlightRule?,
    allConfigNames: List<String>,
    /** 正文那一份排版快照：预览的字号/字距/行距/缩进/颜色/页边距全部沿用它的，见 [HighlightRulePreview]。 */
    config: ReadSheetConfigUiState,
    onDismissRequest: () -> Unit,
    onSave: (HighlightRule) -> Unit,
    /** false = 角色气泡那一份：样式/命中排版/应用排版/字体替换全都在，只去掉「规则信息」。 */
    showRuleInfo: Boolean = true,
    title: String? = null,
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
    var sampleText by remember(show, rule) { mutableStateOf(initial.sampleText) }

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

    // Font weight state
    var fontWeight by remember(show, rule) { mutableIntStateOf(initial.fontWeight) }
    var isItalic by remember(show, rule) { mutableStateOf(initial.isItalic) }
    var fontSizeOffset by remember(show, rule) { mutableIntStateOf(initial.fontSizeOffset) }

    // Nine-slice state
    var npLeft by remember(show, rule) { mutableFloatStateOf(initial.npLeft) }
    var npRight by remember(show, rule) { mutableFloatStateOf(initial.npRight) }
    var npTop by remember(show, rule) { mutableFloatStateOf(initial.npTop) }
    var npBottom by remember(show, rule) { mutableFloatStateOf(initial.npBottom) }
    var showNinePatchEditor by remember(show, rule) { mutableStateOf(false) }
    var manualNineSlice by remember(show, rule) { mutableStateOf(initial.manualNineSlice) }

    // 命中排版 state：只作用在正则命中的那一段上，没设（0）就一个浮点差异都不引入。
    var matchSpacingBefore by remember(show, rule) {
        mutableFloatStateOf(initial.letterSpacingBefore)
    }
    var matchSpacingAfter by remember(show, rule) { mutableFloatStateOf(initial.letterSpacingAfter) }
    var hitLineSpacingTop by remember(show, rule) { mutableFloatStateOf(initial.lineSpacingTop) }
    var hitLineSpacingBottom by remember(show, rule) {
        mutableFloatStateOf(initial.lineSpacingBottom)
    }
    var bgLengthOffsetLeft by remember(show, rule) {
        mutableFloatStateOf(initial.bgLengthOffsetLeft)
    }
    var bgLengthOffsetRight by remember(show, rule) {
        mutableFloatStateOf(initial.bgLengthOffsetRight)
    }

    // Config binding state — empty set = global (applies to all configs)
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

    // File picker for background images (uses OpenDocument to avoid MediaStore transcoding)
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
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

    /**
     * 预览与保存共用这一份数据：预览看到的就是按下保存会写进库的那条规则，
     * 不存在「预览一套、正文一套」的第二套口径。
     */
    val previewRule = HighlightRule(
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
        fontWeight = fontWeight,
        isItalic = isItalic,
        fontSizeOffset = fontSizeOffset,
        npLeft = npLeft,
        npRight = npRight,
        npTop = npTop,
        npBottom = npBottom,
        manualNineSlice = manualNineSlice,
        letterSpacingBefore = matchSpacingBefore,
        letterSpacingAfter = matchSpacingAfter,
        lineSpacingTop = hitLineSpacingTop,
        lineSpacingBottom = hitLineSpacingBottom,
        bgLengthOffsetLeft = bgLengthOffsetLeft,
        bgLengthOffsetRight = bgLengthOffsetRight,
    )

    val titleRes = if (isNew) R.string.new_rule else R.string.edit_rule

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title ?: stringResource(titleRes),
        endAction = {
            MediumTonalButton(
                onClick = {
                    if (showRuleInfo && pattern.isNotBlank()) {
                        val result = runCatching { Regex(pattern) }
                        if (result.isFailure) {
                            patternError = result.exceptionOrNull()?.message
                            return@MediumTonalButton
                        }
                    }
                    patternError = null
                    onSave(previewRule)
                },
                icon = Icons.Default.Done,
                contentDescription = stringResource(R.string.save),
            )
        },
    ) {
        val scrollState = rememberScrollState()
        // 键盘顶着屏幕时不浮出预览：悬浮卡钉在弹层底边，输入框一拿到焦点就收回。
        var typingFocused by remember(show) { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .verticalScroll(scrollState),
            ) {
                // === Section 1: Rule Info ===
                // 角色气泡复用这一整套编辑器，只是没有「规则信息」：哪一句归哪个角色由分配表
                // 给定，不需要正则、作用域与停用开关（见 CastBubbleSheet）。
                if (showRuleInfo) {
                    SectionTitle(stringResource(R.string.rule_info))

                    AppTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = stringResource(R.string.rule_name),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(8.dp))

                    AppTextField(
                        value = pattern,
                        onValueChange = {
                            pattern = it
                            patternError = null
                        },
                        label = stringResource(R.string.rule_pattern),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusEvent { typingFocused = it.hasFocus },
                        isError = patternError != null,
                        supportingText = patternError?.let {
                            { AppText(it, color = MaterialTheme.colorScheme.error) }
                        },
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
                        onValueChange = {
                            targetScope = it.toIntOrNull() ?: HighlightRule.TARGET_ALL
                        },
                    )

                    TinySwitchSettingItem(
                        title = stringResource(R.string.enable_rule),
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                    )
                }

                // === Section 2: Style Settings ===
                SectionTitle(stringResource(R.string.style_settings))

                // Text color
                TinySwitchSettingItem(
                    title = stringResource(R.string.text_color),
                    checked = hasTextColor,
                    onCheckedChange = { hasTextColor = it },
                )
                AnimatedVisibility(visible = hasTextColor) {
                    TinyColorSettingItem(
                        title = stringResource(R.string.select_color),
                        colorValue = textColor,
                        onClick = { showTextColorPicker = true },
                    )
                }

                // Font weight — three options: Regular(400), Bold(700), Light(300)
                val weightEntries = stringArrayResource(R.array.text_font_weight)
                TinyDropdownSettingItem(
                    title = stringResource(R.string.font_weight_text),
                    selectedValue = fontWeight.toString(),
                    displayEntries = weightEntries,
                    entryValues = arrayOf("400", "700", "300"),
                    onValueChange = { fontWeight = it.toIntOrNull() ?: 400 },
                )

                // Italic
                TinySwitchSettingItem(
                    title = stringResource(R.string.read_config_italic),
                    checked = isItalic,
                    onCheckedChange = { isItalic = it },
                )

                // Font size offset
                TinySliderSettingItem(
                    title = stringResource(R.string.font_size_offset),
                    value = fontSizeOffset.toFloat(),
                    valueRange = -10f..10f,
                    steps = 19,
                    description = if (fontSizeOffset == 0) {
                        stringResource(R.string.text_default)
                    } else {
                        stringResource(R.string.font_size_offset_value, fontSizeOffset)
                    },
                    onValueChange = { fontSizeOffset = it.toInt() },
                )

                // Underline
                TinySwitchSettingItem(
                    title = stringResource(R.string.underline_style),
                    checked = hasUnderline,
                    onCheckedChange = { hasUnderline = it },
                )
                AnimatedVisibility(visible = hasUnderline) {
                    Column {
                        val underlineEntries = arrayOf(
                            stringResource(R.string.underline_solid),
                            stringResource(R.string.underline_dashed),
                            stringResource(R.string.underline_wave),
                            stringResource(R.string.underline_title_bar),
                            stringResource(R.string.underline_svg),
                            stringResource(R.string.bookmark_mark_effect_strike),
                            stringResource(R.string.bookmark_mark_effect_highlight),
                        )
                        val underlineValues = arrayOf("1", "2", "3", "4", "5", "6", "7")
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
                        AnimatedVisibility(visible = hasUnderlineColor) {
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

                        AnimatedVisibility(visible = underlineMode == 5) {
                            AppTextField(
                                value = underlineSvgPath,
                                onValueChange = { underlineSvgPath = it },
                                label = stringResource(R.string.svg_path),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                // Background color
                TinySwitchSettingItem(
                    title = stringResource(R.string.bg_color),
                    checked = hasBgColor,
                    onCheckedChange = { hasBgColor = it },
                )
                AnimatedVisibility(visible = hasBgColor) {
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
                AnimatedVisibility(visible = hasBgImage) {
                    TinyClickableSettingItem(
                        title = stringResource(R.string.highlight_bg_image),
                        description = bgImage.ifBlank { null }?.let { File(it).name },
                        onClick = {
                            imagePicker.launch(arrayOf("image/*"))
                        },
                    )
                }
                AnimatedVisibility(visible = hasBgImage && bgImage.isNotBlank()) {
                    Column {
                        val fitEntries = arrayOf(
                            stringResource(R.string.bg_fit_tile),
                            stringResource(R.string.bg_fit_stretch),
                            stringResource(R.string.bg_fit_crop),
                            stringResource(R.string.bg_fit_nine_patch),
                        )
                        val fitValues = arrayOf("0", "1", "2", "3")
                        TinyDropdownSettingItem(
                            title = stringResource(R.string.bg_image_fit),
                            selectedValue = bgImageFit.toString(),
                            displayEntries = fitEntries,
                            entryValues = fitValues,
                            onValueChange = {
                                val newFit = it.toIntOrNull() ?: 0
                                bgImageFit = newFit
                                if (newFit == 3) {
                                    showNinePatchEditor = true
                                }
                            },
                        )

                        TinySliderSettingItem(
                            title = stringResource(R.string.highlight_bg_image_scale),
                            value = bgImageScale,
                            valueRange = 0.1f..5f,
                            steps = 48,
                            stepSize = 0.1f,
                            showDecimal = true,
                            valueFormat = { String.format("%.1f", it) },
                            description = String.format("%.1fx", bgImageScale),
                            onValueChange = { bgImageScale = (it * 10).roundToInt() / 10f },
                        )
                        // 只有九宫格才吃这两个偏移（其它 fit 的绘制根本不读它们），
                        // 挂在 fit 外面就是两个拨了没反应的死滑杆。
                        AnimatedVisibility(visible = bgImageFit == 3) {
                            Column {
                                TinySliderSettingItem(
                                    title = stringResource(R.string.highlight_bg_length_offset_left),
                                    value = bgLengthOffsetLeft,
                                    valueRange = -40f..40f,
                                    steps = 159,
                                    stepSize = 0.5f,
                                    showDecimal = true,
                                    valueFormat = { String.format("%.1f", it) },
                                    description = String.format("%.1f dp", bgLengthOffsetLeft),
                                    onValueChange = { bgLengthOffsetLeft = (it * 2).roundToInt() / 2f },
                                )
                                TinySliderSettingItem(
                                    title = stringResource(R.string.highlight_bg_length_offset_right),
                                    value = bgLengthOffsetRight,
                                    valueRange = -40f..40f,
                                    steps = 159,
                                    stepSize = 0.5f,
                                    showDecimal = true,
                                    valueFormat = { String.format("%.1f", it) },
                                    description = String.format("%.1f dp", bgLengthOffsetRight),
                                    onValueChange = { bgLengthOffsetRight = (it * 2).roundToInt() / 2f },
                                )
                            }
                        }
                        if (bgImageFit == 3) {
                            TinySwitchSettingItem(
                                title = stringResource(R.string.manual_nine_slice),
                                checked = manualNineSlice,
                                onCheckedChange = {
                                    manualNineSlice = it
                                    if (it) showNinePatchEditor = true
                                },
                            )
                            if (manualNineSlice) {
                                TinyClickableSettingItem(
                                    title = stringResource(R.string.edit_nine_slice),
                                    onClick = { showNinePatchEditor = true },
                                )
                            }
                        }
                    }
                }

                // === Section 2b: Match typography ===
                SectionTitle(stringResource(R.string.highlight_match_typography))
                AppText(
                    stringResource(R.string.highlight_match_typography_hint),
                    style = LegadoTheme.typography.labelMedium,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                TinySliderSettingItem(
                    title = stringResource(R.string.highlight_spacing_before),
                    value = matchSpacingBefore,
                    valueRange = 0f..40f,
                    steps = 79,
                    stepSize = 0.5f,
                    showDecimal = true,
                    valueFormat = { String.format("%.1f", it) },
                    description = String.format("%.1f dp", matchSpacingBefore),
                    onValueChange = { matchSpacingBefore = (it * 2).roundToInt() / 2f },
                )
                TinySliderSettingItem(
                    title = stringResource(R.string.highlight_spacing_after),
                    value = matchSpacingAfter,
                    valueRange = 0f..40f,
                    steps = 79,
                    stepSize = 0.5f,
                    showDecimal = true,
                    valueFormat = { String.format("%.1f", it) },
                    description = String.format("%.1f dp", matchSpacingAfter),
                    onValueChange = { matchSpacingAfter = (it * 2).roundToInt() / 2f },
                )
                TinySliderSettingItem(
                    title = stringResource(R.string.highlight_line_spacing_above),
                    value = hitLineSpacingTop,
                    valueRange = 0f..40f,
                    steps = 79,
                    stepSize = 0.5f,
                    showDecimal = true,
                    valueFormat = { String.format("%.1f", it) },
                    description = String.format("%.1f dp", hitLineSpacingTop),
                    onValueChange = { hitLineSpacingTop = (it * 2).roundToInt() / 2f },
                )
                TinySliderSettingItem(
                    title = stringResource(R.string.highlight_line_spacing_below),
                    value = hitLineSpacingBottom,
                    valueRange = 0f..40f,
                    steps = 79,
                    stepSize = 0.5f,
                    showDecimal = true,
                    valueFormat = { String.format("%.1f", it) },
                    description = String.format("%.1f dp", hitLineSpacingBottom),
                    onValueChange = { hitLineSpacingBottom = (it * 2).roundToInt() / 2f },
                )

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
                AnimatedVisibility(visible = hasFont) {
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusEvent { typingFocused = it.hasFocus },
                )

                HighlightPreviewCard(
                    rule = previewRule,
                    config = config,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
            // 预览悬浮窗：钉在弹层底边，不跟着内容滚走。还有内容在下面看不着、且焦点不在
            // 输入框上（键盘顶着屏幕）的时候才浮出来。画的是正文同一份函数，不会两套口径。
            androidx.compose.animation.AnimatedVisibility(
                visible = scrollState.value < scrollState.maxValue && !typingFocused,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                HighlightPreviewCard(
                    rule = previewRule,
                    config = config,
                    floating = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
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

    // Nine-patch editor
    NinePatchEditorDialog(
        show = showNinePatchEditor,
        imagePath = bgImage,
        initialLeft = npLeft,
        initialRight = npRight,
        initialTop = npTop,
        initialBottom = npBottom,
        previewRule = previewRule,
        config = config,
        onDismissRequest = { showNinePatchEditor = false },
        onSave = { left, right, top, bottom ->
            npLeft = left
            npRight = right
            npTop = top
            npBottom = bottom
            showNinePatchEditor = false
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

/** 预览卡：正文卡与悬浮卡共用同一份内容，浮起来的那张多一行标题和阴影。 */
/** 角色气泡弹层复用同一张预览卡：它跑的就是正文那条管线，不留第二套口径。 */
@Composable
internal fun HighlightPreviewCard(
    rule: HighlightRule,
    config: ReadSheetConfigUiState,
    modifier: Modifier = Modifier,
    floating: Boolean = false,
) {
    NormalCard(
        modifier = modifier,
        cornerRadius = 16.dp,
        containerColor = if (floating) {
            LegadoTheme.colorScheme.surfaceContainerHigh
        } else {
            LegadoTheme.colorScheme.surfaceContainerLow
        },
        elevation = if (floating) 6.dp else 0.dp,
    ) {
        if (floating) {
            AppText(
                stringResource(R.string.preview_effect),
                style = LegadoTheme.typography.labelMedium,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp),
            )
        }
        HighlightRulePreview(
            rule = rule,
            config = config,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (floating) 128.dp else 168.dp)
                .clipToBounds()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * 规则预览：整块走正文那一条管线，示例句按**正文那一栏的宽度、字号与页边距**排版，再把整页等比缩进卡片。
 *
 * 规则→样式用 [LegacyReaderStyleRangeMapper]（命中字距、命中行行距、背景图切线都在那里换算），
 * 测量用 [ReaderChapterBlockMeasurer]，分页用 [ReaderPaginator]，绘制用正文同一份
 * [drawTextBackground] 与 [ReaderPageDecorationDrawCache]。正文怎么断行、气泡多大、字落在哪，
 * 预览就是那个结果——包括「命中字距只在段外留白，不许把图拉长」这一条。
 *
 * 卡片只有屏宽大分之一，硬画装不下，所以缩放只放在最后一步：字号、字距、行距、段首缩进、页边距、
 * 断行位置、气泡与字的比例全部是正文那一份，整页一起缩。改正文字号时预览跟着一起变。
 */
@Composable
private fun HighlightRulePreview(
    rule: HighlightRule,
    config: ReadSheetConfigUiState,
    modifier: Modifier = Modifier,
) {
    // 正文分页读的就是窗口宽：视口取同一份，断行才会与正文一致。
    val bodyViewportWidthPx = LocalWindowInfo.current.containerSize.width
    val baseTextSizePx = config.textSize.toFloat().spToPx()
    val layout = rememberReaderPreviewLayout(
        rule = rule,
        config = config,
        sample = rule.normalizedSampleText(),
        viewportWidthPx = bodyViewportWidthPx,
        // 视口给高：示例句必须全落在第一页，不然一拉「命中行行距」就把最后一行挤到丢掉的第二页，
        // 预览里就看不到规则生效。超出的部分由外层 clipToBounds 裁掉，跟正文一样不许装饰跑进页边距。
        viewportHeightPx = (baseTextSizePx * 60f).toInt(),
    )
    val decorations = remember(layout) { layout?.let { ReaderPageDecorationDrawCache.create(it.page) } }
    val backgroundPaint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
    val stylePaints = remember { mutableMapOf<ReaderTextStyle, Paint>() }
    Canvas(modifier) {
        val page = layout?.page
        if (page != null && decorations != null && bodyViewportWidthPx > 0) {
            val runs = page.textBackgroundRuns()
            // 正文那一刀：裁剪框跟着背景走，气泡不会被页边距切成两截，字也超不出去。
            val clip = page.contentClipRect(runs)
            val previewScale = size.width / bodyViewportWidthPx
            // 整页原点缩放会把正文那一圈页边距一起缩进卡片里：示例句会落到卡片下方偏右，
            // 视觉上字是歪的。这里只把**内容外接框的左上角**平移到卡片原点（排版一项不动，
            // 段首缩进、命中字距、气泡与字的比例仍是正文那一份），卡片里就是干净的一页内容。
            clipRect(
                0f, 0f,
                (clip.right - clip.left) * previewScale,
                (clip.bottom - clip.top) * previewScale,
            ) {
                scale(previewScale) {
                    translate(-clip.left, -clip.top) {
                        drawReaderPreviewPage(layout, runs, decorations, backgroundPaint, stylePaints)
                    }
                }
            }
        }
    }
}

/** 排版好的一页 + 背景图位图：几何全部已经在 [ReaderPage] 里按正文口径算完。 */
private data class ReaderPreviewLayout(val page: ReaderPage, val background: Bitmap?)

/**
 * 算一页要读一次图片尺寸与字体度量，放后台线程；示例句最长三行，代价可以忽略。
 * 重算期间沿用上一次的页，滑杆拖动时不会闪白。
 */
@Composable
private fun rememberReaderPreviewLayout(
    rule: HighlightRule,
    config: ReadSheetConfigUiState,
    sample: String,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
): ReaderPreviewLayout? {
    var layout by remember { mutableStateOf<ReaderPreviewLayout?>(null) }
    LaunchedEffect(rule, config, sample, viewportWidthPx, viewportHeightPx) {
        if (viewportWidthPx <= 1 || viewportHeightPx <= 1) return@LaunchedEffect
        layout = withContext(Dispatchers.Default) {
            buildReaderPreviewLayout(
                rule = rule,
                config = config,
                sample = sample,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
            )
        }
    }
    return layout
}

private suspend fun buildReaderPreviewLayout(
    rule: HighlightRule,
    config: ReadSheetConfigUiState,
    sample: String,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
): ReaderPreviewLayout? {
    // 基础样式与页边距照抄正文那一份装配（见 LegacyReaderPaginationStyleFactory.create）：
    // 字号、字重、斜体、字色、字距、行距、段首缩进、四边页距，一项都不另算。
    val baseStyle = ReaderTextStyle(
        colorArgb = config.textColor,
        fontSizePx = config.textSize.toFloat().spToPx(),
        fontWeight = LegacyReaderPaginationStyleFactory.resolveWeight(config.textBold),
        italic = config.textItalic,
    )
    val lineSpacingMultiplier = config.lineSpacing / 10f
    val baseShaper = AndroidReaderTextShaper(ReaderAndroidPaintFactory.createTextPaint(baseStyle))
    val lineMetrics = baseShaper.fontLineMetrics
    val source = ReaderChapterSource(
        chapterIndex = 0,
        title = "",
        blocks = listOf(ReaderChapterSourceBlock.Text(value = sample, chapterPosition = 0)),
        characterCount = sample.length,
        semanticContent = sample,
    )
    val measured = ReaderChapterBlockMeasurer(
        bodyShaper = baseShaper,
        titleShaper = baseShaper,
        imageDimensionsResolver = ReaderImageDimensionsResolver { null },
        // 命中段可以改字号：宽度得用放大后的那支笔量，和正文一样按样式各建一支。
        textShaperFactory = ReaderTextShaperFactory {
            AndroidReaderTextShaper(ReaderAndroidPaintFactory.createTextPaint(it))
        },
    ).measure(
        source = source,
        style = ReaderChapterMeasureStyle(
            bodyStyle = baseStyle,
            titleStyle = baseStyle,
            bodyIndentCharacters = config.paragraphIndentCount,
            bodyAlignment = if (config.textFullJustify) {
                ReaderTextAlignment.JUSTIFY
            } else {
                ReaderTextAlignment.START
            },
            titleAlignment = ReaderTextAlignment.START,
            bodyLineHeightPx = lineMetrics.heightPx,
            bodyBaselineOffsetPx = lineMetrics.baselineOffsetPx,
            bodyLineSpacingMultiplier = lineSpacingMultiplier,
            letterSpacingEm = config.letterSpacing,
            // 每一项改动都必须落在「我是李四。」上：区间钉死，不看用户填的正则命没命中——
            // 命不中时整块预览是死的，调字色/背景图/命中字距全都看不出差别。
            // 只有用户把示例句改成不含那一段的内容时，才退回按正则命中。
            // （停用或只作用于标题的规则照样看得见效果：字面区间这两样都不参与。）
            styleRanges = rule.previewHitRange()?.let { (hitStart, hitEnd) ->
                LegacyReaderStyleRangeMapper.rangesForLiteralRange(
                    rule = rule,
                    start = hitStart,
                    endExclusive = hitEnd,
                    target = ReaderStyleTarget.BODY,
                    priority = 0,
                )
            } ?: LegacyReaderStyleRangeMapper.map(
                source = source,
                rules = listOf(rule.copy(enabled = true, targetScope = HighlightRule.TARGET_ALL)),
                processes = emptyList(),
            ),
        ),
    ) as? ReaderChapterMeasureResult.Success ?: return null
    val page = ReaderPaginator.paginateBlocks(
        measured.blocks,
        ReaderPaginationConfig(
            chapterIndex = 0,
            chapterTitle = "",
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
            paddingLeftPx = config.paddingLeft.toFloat().dpToPx(),
            paddingTopPx = config.paddingTop.toFloat().dpToPx(),
            paddingRightPx = config.paddingRight.toFloat().dpToPx(),
            paddingBottomPx = config.paddingBottom.toFloat().dpToPx(),
            lineHeightPx = lineMetrics.heightPx,
            baselineOffsetPx = lineMetrics.baselineOffsetPx,
            lineSpacingMultiplier = lineSpacingMultiplier,
            letterSpacingPx = config.letterSpacing * baseStyle.fontSizePx,
            paragraphSpacingPx = lineMetrics.heightPx * config.paragraphSpacing / 10f,
        ),
    ).firstOrNull() ?: return null
    val background = rule.bgImage?.takeIf { it.isNotBlank() }
        ?.let(ReaderTextBackgroundLoader::load)
    return ReaderPreviewLayout(page, background)
}

/** 与 `ReaderCanvasSurface` 同一顺序：背景色条 → 背景图 → 半截高亮 → 字 → 线。 */
private fun DrawScope.drawReaderPreviewPage(
    layout: ReaderPreviewLayout,
    backgroundRuns: List<ReaderTextBackgroundRun>,
    decorations: ReaderPageDecorationDrawCache,
    backgroundPaint: Paint,
    stylePaints: MutableMap<ReaderTextStyle, Paint>,
) {
    val native = drawContext.canvas.nativeCanvas
    val textElements = layout.page.elements.filterIsInstance<ReaderElement.Text>()
    textElements.mergeBackgroundBounds().forEach { band ->
        drawRect(
            Color(band.colorArgb),
            Offset(band.bounds.left, band.bounds.top),
            Size(band.bounds.width, band.bounds.height),
        )
    }
    layout.background?.let { bitmap ->
        backgroundRuns.forEach { run ->
            drawTextBackground(native, bitmap, run, backgroundPaint)
        }
    }
    decorations.halfHighlights.forEach { it.draw(native) }
    textElements.forEach { element ->
        val paint = stylePaints.getOrPut(element.style) {
            ReaderAndroidPaintFactory.create(element.style)
        }
        native.drawText(element.value, element.bounds.left, element.baselinePx, paint)
    }
    decorations.contentRules.forEach { it.draw(native) }
    decorations.styledUnderlines.forEach { it.draw(native) }
    decorations.overlayRules.forEach { it.draw(native) }
}

/**
 * 九宫格切图：全程只有横向拉伸。左右两条线之间那一格是唯一会被拉长的地方，
 * 上下两条线只决定「字落在图的哪一段里」——整张图的高度按缩放倍数锁死，纵向一条边都不拉。
 */
@Composable
internal fun NinePatchEditorDialog(
    show: Boolean,
    imagePath: String,
    initialLeft: Float,
    initialRight: Float,
    initialTop: Float,
    initialBottom: Float,
    previewRule: HighlightRule,
    config: ReadSheetConfigUiState,
    onDismissRequest: () -> Unit,
    onSave: (left: Float, right: Float, top: Float, bottom: Float) -> Unit,
) {
    var left by remember(show, imagePath) { mutableFloatStateOf(initialLeft) }
    var right by remember(show, imagePath) { mutableFloatStateOf(initialRight) }
    var top by remember(show, imagePath) { mutableFloatStateOf(initialTop) }
    var bottom by remember(show, imagePath) { mutableFloatStateOf(initialBottom) }
    var dragHandle by remember { mutableStateOf<NineSliceHandle?>(null) }

    val bitmap = remember(imagePath) {
        runCatching {
            val file = File(imagePath)
            if (file.exists()) {
                BitmapFactory.decodeFile(imagePath)
            } else null
        }.getOrNull()
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.nine_patch_editor),
        endAction = {
            MediumTonalButton(
                onClick = { onSave(left, right, top, bottom) },
                icon = Icons.Default.Done,
                contentDescription = stringResource(R.string.save),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            AppText(
                stringResource(R.string.nine_slice_split_hint),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            // Image preview with split lines — use single Canvas to avoid coordinate mismatch
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .padding(8.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                if (bitmap != null) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(bitmap) {
                                detectDragGestures(
                                    onDragStart = { point ->
                                        val rect = nineSlicePreviewRect(
                                            size.width.toFloat(), size.height.toFloat(),
                                            bitmap.width.toFloat(), bitmap.height.toFloat(),
                                        )
                                        val candidates = listOf(
                                            NineSliceHandle.LEFT to
                                                    abs(point.x - (rect.left + rect.width * left)),
                                            NineSliceHandle.RIGHT to
                                                    abs(point.x - (rect.right - rect.width * right)),
                                            NineSliceHandle.TOP to
                                                    abs(point.y - (rect.top + rect.height * top)),
                                            NineSliceHandle.BOTTOM to
                                                    abs(point.y - (rect.bottom - rect.height * bottom)),
                                        )
                                        dragHandle = candidates.minByOrNull { it.second }
                                            ?.takeIf { it.second <= 32.dp.toPx() }?.first
                                    },
                                    onDragEnd = { dragHandle = null },
                                    onDragCancel = { dragHandle = null },
                                ) { change, amount ->
                                    val rect = nineSlicePreviewRect(
                                        size.width.toFloat(), size.height.toFloat(),
                                        bitmap.width.toFloat(), bitmap.height.toFloat(),
                                    )
                                    when (dragHandle) {
                                        // 四条线都不许越过 0..1，但**不许**再夹到一半：
                                        // 图案不在正中间的图需要把拉伸带整个推到一侧
                                        NineSliceHandle.LEFT -> left =
                                            (left + amount.x / rect.width).coerceIn(0f, 1f)

                                        NineSliceHandle.RIGHT -> right =
                                            (right - amount.x / rect.width).coerceIn(0f, 1f)

                                        NineSliceHandle.TOP -> top =
                                            (top + amount.y / rect.height).coerceIn(0f, 1f)

                                        NineSliceHandle.BOTTOM -> bottom =
                                            (bottom - amount.y / rect.height).coerceIn(0f, 1f)

                                        null -> Unit
                                    }
                                    if (dragHandle != null) change.consume()
                                }
                            }) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val bw = bitmap.width.toFloat()
                        val bh = bitmap.height.toFloat()
                        val preview = nineSlicePreviewRect(canvasWidth, canvasHeight, bw, bh)
                        val imageW = preview.width
                        val imageH = preview.height
                        val offsetX = preview.left
                        val offsetY = preview.top

                        // Draw bitmap
                        drawImage(
                            image = bitmap.asImageBitmap(),
                            dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()),
                            dstSize = androidx.compose.ui.unit.IntSize(imageW.toInt(), imageH.toInt()),
                        )

                        val lineColor = Color(0xFF16C96A)
                        val lineWidth = 2.dp.toPx()

                        // Left line
                        val lx = offsetX + imageW * left
                        drawLine(lineColor, Offset(lx, offsetY), Offset(lx, offsetY + imageH), lineWidth)
                        // Right line
                        val rx = offsetX + imageW * (1f - right)
                        drawLine(lineColor, Offset(rx, offsetY), Offset(rx, offsetY + imageH), lineWidth)
                        // Top line
                        val ty = offsetY + imageH * top
                        drawLine(lineColor, Offset(offsetX, ty), Offset(offsetX + imageW, ty), lineWidth)
                        // Bottom line
                        val by = offsetY + imageH * (1f - bottom)
                        drawLine(lineColor, Offset(offsetX, by), Offset(offsetX + imageW, by), lineWidth)
                        // 绿框里那一块就是会被横向拉长的中段，也是字待的那一段。
                        drawRect(
                            lineColor.copy(alpha = 0.18f),
                            topLeft = Offset(lx, ty),
                            size = androidx.compose.ui.geometry.Size(rx - lx, by - ty),
                        )
                        val radius = 5.dp.toPx()
                        listOf(
                            Offset(lx, (ty + by) / 2f), Offset(rx, (ty + by) / 2f),
                            Offset((lx + rx) / 2f, ty), Offset((lx + rx) / 2f, by),
                        ).forEach { drawCircle(lineColor, radius, it) }
                    }
                }
            }
            // 拖完四条线马上看正文效果：背景图、缩放、长度偏移用的都是弹层里当前这份设置，
            // 四条切分线则跟着拖动实时变。
            HighlightPreviewCard(
                rule = previewRule.copy(
                    npLeft = left,
                    npRight = right,
                    npTop = top,
                    npBottom = bottom,
                ),
                config = config,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
            AppText(
                stringResource(R.string.nine_slice_horizontal_hint),
                style = LegadoTheme.typography.labelMedium,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            NineSliceSlider(
                title = stringResource(R.string.nine_patch_split_left),
                value = left,
                onValueChange = { left = it },
            )
            NineSliceSlider(
                title = stringResource(R.string.nine_patch_split_right),
                value = right,
                onValueChange = { right = it },
            )
            AppText(
                stringResource(R.string.nine_slice_vertical_hint),
                style = LegadoTheme.typography.labelMedium,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            NineSliceSlider(
                title = stringResource(R.string.nine_patch_split_top),
                value = top,
                onValueChange = { top = it },
            )
            NineSliceSlider(
                title = stringResource(R.string.nine_patch_split_bottom),
                value = bottom,
                onValueChange = { bottom = it },
            )
        }
    }
}

@Composable
private fun NineSliceSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    TinySliderSettingItem(
        title = title,
        value = value,
        // 四条线都能拉到 100%：图案不在正中间的图要把拉伸带整个推到一侧，
        // 夹在 0.5 就会把图案从中间切断（交叉时的画法见 `ReaderNineSliceLayout.cells`）
        valueRange = 0f..1f,
        steps = 99,
        stepSize = 0.01f,
        showDecimal = true,
        valueFormat = { String.format("%.2f", it) },
        description = String.format("%.0f%%", value * 100f),
        onValueChange = { onValueChange((it * 100).roundToInt() / 100f) },
    )
}

private enum class NineSliceHandle { LEFT, RIGHT, TOP, BOTTOM }

private data class NineSlicePreviewRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right get() = left + width
    val bottom get() = top + height
}

private fun nineSlicePreviewRect(
    canvasWidth: Float,
    canvasHeight: Float,
    bitmapWidth: Float,
    bitmapHeight: Float,
): NineSlicePreviewRect {
    val imageAspect = bitmapWidth / bitmapHeight
    return if (imageAspect > canvasWidth / canvasHeight) {
        val height = canvasWidth / imageAspect
        NineSlicePreviewRect(0f, (canvasHeight - height) / 2f, canvasWidth, height)
    } else {
        val width = canvasHeight * imageAspect
        NineSlicePreviewRect((canvasWidth - width) / 2f, 0f, width, canvasHeight)
    }
}
