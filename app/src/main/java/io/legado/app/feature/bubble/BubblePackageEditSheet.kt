package io.legado.app.feature.bubble

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.feature.reader.platform.ReaderReviewBubbleRenderer
import io.legado.app.feature.reader.platform.ReviewBubbleAppearance
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.text.AppText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BubblePackageEditSheet(
    draft: BubblePackageDraft,
    onIntent: (BubblePackageManageIntent) -> Unit,
) {
    var colorSlot by remember { mutableStateOf<BubbleColorSlot?>(null) }

    AppModalBottomSheet(
        show = true,
        onDismissRequest = { onIntent(BubblePackageManageIntent.DismissDraft) },
        title = stringResource(
            if (draft.isNew) R.string.bubble_package_new else R.string.bubble_package_edit
        ),
        endAction = {
            TextButton(onClick = { onIntent(BubblePackageManageIntent.SaveDraft) }) {
                AppText(text = stringResource(R.string.bubble_package_save))
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BubbleDraftPreview(draft)
            AppTextField(
                value = draft.name,
                onValueChange = {
                    onIntent(BubblePackageManageIntent.DraftNameChanged(it))
                },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.bubble_package_name),
                singleLine = true,
            )
            AppTextField(
                value = draft.svgTemplate,
                onValueChange = {
                    onIntent(BubblePackageManageIntent.DraftTemplateChanged(it))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                label = stringResource(R.string.bubble_package_template),
                supportingText = {
                    AppText(text = stringResource(R.string.bubble_package_template_hint))
                },
            )
            AppText(
                text = stringResource(
                    R.string.bubble_package_size_scale,
                    draft.sizeScale,
                ),
                style = MaterialTheme.typography.titleSmall,
            )
            Slider(
                value = draft.sizeScale,
                onValueChange = {
                    onIntent(BubblePackageManageIntent.DraftScaleChanged(it))
                },
                valueRange = 0.5f..1.5f,
                steps = 3,
            )
            ColorRow(
                title = stringResource(R.string.bubble_package_day_normal),
                color = draft.dayNormalColor,
                onClick = { colorSlot = BubbleColorSlot.DAY_NORMAL },
            )
            ColorRow(
                title = stringResource(R.string.bubble_package_day_emphasis),
                color = draft.dayEmphasisColor,
                onClick = { colorSlot = BubbleColorSlot.DAY_EMPHASIS },
            )
            ColorRow(
                title = stringResource(R.string.bubble_package_night_normal),
                color = draft.nightNormalColor,
                onClick = { colorSlot = BubbleColorSlot.NIGHT_NORMAL },
            )
            ColorRow(
                title = stringResource(R.string.bubble_package_night_emphasis),
                color = draft.nightEmphasisColor,
                onClick = { colorSlot = BubbleColorSlot.NIGHT_EMPHASIS },
            )
        }
    }

    colorSlot?.let { slot ->
        ColorPickerSheet(
            show = true,
            initialColor = parseColor(colorOf(draft, slot)),
            onDismissRequest = { colorSlot = null },
            onColorSelected = { color ->
                colorSlot = null
                onIntent(
                    BubblePackageManageIntent.DraftColorChanged(slot, colorToHex(color))
                )
            },
        )
    }
}

@Composable
private fun ColorRow(title: String, color: String, onClick: () -> Unit) {
    ClickableSettingItem(
        title = title,
        option = color.ifBlank { stringResource(R.string.bubble_package_color_unset) },
        onClick = onClick,
    )
}

@Composable
private fun BubbleDraftPreview(draft: BubblePackageDraft) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val appearance = remember(
        draft.svgTemplate,
        draft.sizeScale,
        draft.dayNormalColor,
        draft.dayEmphasisColor,
        draft.nightNormalColor,
        draft.nightEmphasisColor,
        draft.resources,
        draft.resourceRoot,
    ) {
        ReviewBubbleAppearance(
            template = draft.svgTemplate,
            sizeScale = draft.sizeScale,
            dayNormalColor = draft.dayNormalColor.ifBlank { null },
            dayEmphasisColor = draft.dayEmphasisColor.ifBlank { null },
            nightNormalColor = draft.nightNormalColor.ifBlank { null },
            nightEmphasisColor = draft.nightEmphasisColor.ifBlank { null },
            resources = draft.resources,
            resourceRoot = draft.resourceRoot,
            aspectRatio = ReaderReviewBubbleRenderer.aspectRatio(draft.svgTemplate),
            revision = 0,
            cacheKey = "draft:${draft.svgTemplate.hashCode()}:${draft.dayNormalColor}",
        )
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        val height = size.height * 0.6f * draft.sizeScale.coerceIn(0.5f, 1.5f)
        val width = height * appearance.aspectRatio
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f
        drawIntoCanvas { canvas ->
            ReaderReviewBubbleRenderer.drawAppearance(
                canvas = canvas.nativeCanvas,
                bounds = RectF(left, top, left + width, top + height),
                appearance = appearance,
                text = "12",
                status = "normal",
                colorOverride = null,
                isNight = false,
                fallbackTextColorArgb = textColor.toArgb(),
                typeface = null,
            )
        }
    }
}

private fun colorOf(draft: BubblePackageDraft, slot: BubbleColorSlot): String = when (slot) {
    BubbleColorSlot.DAY_NORMAL -> draft.dayNormalColor
    BubbleColorSlot.DAY_EMPHASIS -> draft.dayEmphasisColor
    BubbleColorSlot.NIGHT_NORMAL -> draft.nightNormalColor
    BubbleColorSlot.NIGHT_EMPHASIS -> draft.nightEmphasisColor
}

private fun parseColor(value: String): Int = runCatching {
    android.graphics.Color.parseColor(
        if (value.startsWith("#")) value else "#$value"
    )
}.getOrDefault(android.graphics.Color.GRAY)

private fun colorToHex(color: Int): String {
    val alpha = color ushr 24 and 0xff
    return if (alpha == 0xff) {
        String.format("#%06X", color and 0xffffff)
    } else {
        String.format("#%08X", color)
    }
}
