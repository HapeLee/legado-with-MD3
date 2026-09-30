package io.legado.app.ui.book.readaloud.casting

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.HighlightRule
import io.legado.app.ui.book.read.sheet.HighlightPreviewCard
import io.legado.app.ui.book.read.sheet.NinePatchEditorDialog
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.SectionTitle
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyColorSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import kotlin.math.roundToInt

/**
 * 角色气泡设置：参数、切分线编辑器与预览卡全部沿用高亮规则那一节，不留第二套口径。
 *
 * 存进库的是「一条只填了气泡那几栏的 [HighlightRule]」——样式换算走正文那一份
 * `LegacyReaderStyleRangeMapper.styleOf`。命中的句子由分配表给定，所以这里没有正则、
 * 也没有「作用于标题/正文」那一栏。
 *
 * 优先级：同一句台词上高亮规则也匹配时，用这个角色的气泡；但只换气泡本身，
 * 字色/下划线/字号仍归那句里赢着的规则（见 `LegacyReaderStyleRangeMapper.withBubbleOf`）。
 */
@Composable
fun CastBubbleSheet(
    show: Boolean,
    characterName: String,
    /** [CastCharacter.bubbleRuleJson] 原样传进来；空串 = 没设过气泡。 */
    initialJson: String,
    onDismissRequest: () -> Unit,
    /** 回传要写进库的那段 JSON；空串 = 清掉气泡。 */
    onSave: (String) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val initial = remember(show, initialJson) {
        initialJson.takeIf { it.isNotBlank() }
            ?.let { GSON.fromJsonObject<HighlightRule>(it).getOrNull() }
    }
    var enabled by remember(show, initial) { mutableStateOf(initial != null) }
    var bgImage by remember(show, initial) { mutableStateOf(initial?.bgImage.orEmpty()) }
    var bgImageFit by remember(show, initial) { mutableIntStateOf(initial?.bgImageFit ?: 3) }
    var bgImageScale by remember(show, initial) { mutableFloatStateOf(initial?.bgImageScale ?: 1f) }
    var offsetLeft by remember(show, initial) {
        mutableFloatStateOf(initial?.bgLengthOffsetLeft ?: 0f)
    }
    var offsetRight by remember(show, initial) {
        mutableFloatStateOf(initial?.bgLengthOffsetRight ?: 0f)
    }
    var manualNineSlice by remember(show, initial) {
        mutableStateOf(initial?.manualNineSlice ?: true)
    }
    var npLeft by remember(show, initial) { mutableFloatStateOf(initial?.npLeft ?: 0.1f) }
    var npRight by remember(show, initial) { mutableFloatStateOf(initial?.npRight ?: 0.1f) }
    var npTop by remember(show, initial) { mutableFloatStateOf(initial?.npTop ?: 0.1f) }
    var npBottom by remember(show, initial) { mutableFloatStateOf(initial?.npBottom ?: 0.1f) }
    var bgColor by remember(show, initial) { mutableIntStateOf(initial?.bgColor ?: 0x20FFEB3B) }
    var hasBgColor by remember(show, initial) { mutableStateOf(initial?.bgColor != null) }
    var showNinePatch by remember(show) { mutableStateOf(false) }
    var showColorPicker by remember(show) { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val resolver = context.contentResolver
                        copyIntoBackgroundDir(
                            resolver,
                            resolver.queryColumn(uri, OpenableColumns.DISPLAY_NAME),
                            uri,
                        )
                    }
                }.onSuccess { bgImage = it }
                    .onFailure { throwable ->
                        context.toastOnUi(R.string.error)
                        AppLog.put("选择角色气泡背景图失败", throwable)
                    }
            }
        }
    }

    val previewRule = HighlightRule(
        name = "bubble:$characterName",
        enabled = true,
        bgColor = bgColor.takeIf { hasBgColor },
        bgImage = bgImage.takeIf { enabled && it.isNotBlank() },
        bgImageFit = bgImageFit,
        bgImageScale = bgImageScale,
        npLeft = npLeft,
        npRight = npRight,
        npTop = npTop,
        npBottom = npBottom,
        manualNineSlice = manualNineSlice,
        bgLengthOffsetLeft = offsetLeft,
        bgLengthOffsetRight = offsetRight,
    )

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.cast_bubble_menu),
        endAction = {
            MediumTonalButton(
                onClick = {
                    onSave(
                        if (!enabled || (bgImage.isBlank() && !hasBgColor)) {
                            ""
                        } else {
                            GSON.toJson(previewRule)
                        },
                    )
                },
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
                stringResource(R.string.cast_bubble_summary),
                style = LegadoTheme.typography.labelMedium,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            TinySwitchSettingItem(
                title = stringResource(R.string.cast_bubble),
                checked = enabled,
                onCheckedChange = { enabled = it },
            )
            AnimatedVisibility(visible = enabled) {
                Column {
                    SectionTitle(stringResource(R.string.cast_bubble))
                    TinyClickableSettingItem(
                        title = stringResource(R.string.highlight_bg_image),
                        description = bgImage.ifBlank { null }?.let { File(it).name },
                        onClick = { imagePicker.launch(arrayOf("image/*")) },
                    )
                    AnimatedVisibility(visible = bgImage.isNotBlank()) {
                        Column {
                            val fitEntries = arrayOf(
                                stringResource(R.string.bg_fit_tile),
                                stringResource(R.string.bg_fit_stretch),
                                stringResource(R.string.bg_fit_crop),
                                stringResource(R.string.bg_fit_nine_patch),
                            )
                            TinyDropdownSettingItem(
                                title = stringResource(R.string.bg_image_fit),
                                selectedValue = bgImageFit.toString(),
                                displayEntries = fitEntries,
                                entryValues = arrayOf("0", "1", "2", "3"),
                                onValueChange = {
                                    val newFit = it.toIntOrNull() ?: 0
                                    bgImageFit = newFit
                                    if (newFit == 3) showNinePatch = true
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
                            // 只有九宫格读这两个偏移，挂在外层就是两个拨了没反应的死滑杆
                            AnimatedVisibility(visible = bgImageFit == 3) {
                                Column {
                                    TinySliderSettingItem(
                                        title = stringResource(R.string.highlight_bg_length_offset_left),
                                        value = offsetLeft,
                                        valueRange = -40f..40f,
                                        steps = 159,
                                        stepSize = 0.5f,
                                        showDecimal = true,
                                        valueFormat = { String.format("%.1f", it) },
                                        description = String.format("%.1f dp", offsetLeft),
                                        onValueChange = { offsetLeft = (it * 2).roundToInt() / 2f },
                                    )
                                    TinySliderSettingItem(
                                        title = stringResource(R.string.highlight_bg_length_offset_right),
                                        value = offsetRight,
                                        valueRange = -40f..40f,
                                        steps = 159,
                                        stepSize = 0.5f,
                                        showDecimal = true,
                                        valueFormat = { String.format("%.1f", it) },
                                        description = String.format("%.1f dp", offsetRight),
                                        onValueChange = { offsetRight = (it * 2).roundToInt() / 2f },
                                    )
                                    TinySwitchSettingItem(
                                        title = stringResource(R.string.manual_nine_slice),
                                        checked = manualNineSlice,
                                        onCheckedChange = {
                                            manualNineSlice = it
                                            if (it) showNinePatch = true
                                        },
                                    )
                                    if (manualNineSlice) {
                                        TinyClickableSettingItem(
                                            title = stringResource(R.string.edit_nine_slice),
                                            onClick = { showNinePatch = true },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    TinySwitchSettingItem(
                        title = stringResource(R.string.bg_color),
                        checked = hasBgColor,
                        onCheckedChange = { hasBgColor = it },
                    )
                    AnimatedVisibility(visible = hasBgColor) {
                        TinyColorSettingItem(
                            title = stringResource(R.string.select_color),
                            colorValue = bgColor,
                            onClick = { showColorPicker = true },
                        )
                    }
                    if (bgImage.isNotBlank()) {
                        TinyClickableSettingItem(
                            title = stringResource(R.string.cast_bubble_clear),
                            onClick = { bgImage = "" },
                        )
                    }
                    HighlightPreviewCard(
                        rule = previewRule,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }

    NinePatchEditorDialog(
        show = showNinePatch && bgImage.isNotBlank(),
        imagePath = bgImage,
        initialLeft = npLeft,
        initialRight = npRight,
        initialTop = npTop,
        initialBottom = npBottom,
        previewRule = previewRule,
        onDismissRequest = { showNinePatch = false },
        onSave = { left, right, top, bottom ->
            npLeft = left
            npRight = right
            npTop = top
            npBottom = bottom
            showNinePatch = false
        },
    )
    ColorPickerSheet(
        show = showColorPicker,
        initialColor = bgColor,
        onDismissRequest = { showColorPicker = false },
        onColorSelected = { color ->
            bgColor = color
            showColorPicker = false
        },
    )
}

/**
 * 与高亮规则那一份完全一致：落到同一个 `filesDir/bg_images`，`.9.png` 的后缀必须保住
 * （自动读切线靠它），文件名带时间戳所以不会互相覆盖。
 */
private fun copyIntoBackgroundDir(
    resolver: android.content.ContentResolver,
    displayName: String?,
    uri: android.net.Uri,
): String {
    val dir = File(appCtx.filesDir, "bg_images")
    if (!dir.exists()) dir.mkdirs()
    val suffix = when {
        displayName?.endsWith(".9.png", ignoreCase = true) == true -> ".9.png"
        displayName?.substringAfterLast('.', "").isNullOrBlank() -> ".img"
        else -> ".${displayName!!.substringAfterLast('.')}"
    }
    val target = File(dir, "bg_${System.currentTimeMillis()}$suffix")
    resolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    } ?: throw java.io.FileNotFoundException("Open input stream failed")
    return target.absolutePath
}

private fun android.content.ContentResolver.queryColumn(
    uri: android.net.Uri,
    column: String,
): String? = runCatching {
    query(uri, arrayOf(column), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getColumnIndex(column).takeIf { it >= 0 }?.let(cursor::getString)
        } else null
    }
}.getOrNull()
