package io.legado.app.ui.config.readConfig

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.feature.reader.platform.ReaderReviewBubbleRenderer
import io.legado.app.help.config.ReadStyleResolver
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText

@Composable
internal fun reviewBubbleStyleLabel(style: String, packageName: String): String = when (style) {
    "custom" -> stringResource(R.string.review_bubble_custom)
    "package" -> packageName.ifBlank { stringResource(R.string.review_bubble_builtin_bubble) }
    else -> stringResource(R.string.review_bubble_follow_source)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewBubbleSheet(
    show: Boolean,
    style: String,
    svgPath: String,
    packages: List<ReviewBubblePackageUi>,
    activePackage: String,
    onDismissRequest: () -> Unit,
    onStyleChange: (String) -> Unit,
    onSelectPackage: (String) -> Unit,
    onPickSvg: () -> Unit,
    onClearSvg: () -> Unit,
    onManagePackages: () -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.review_bubble),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BubblePreview()
            ReviewBubbleOptionRow(
                label = stringResource(R.string.review_bubble_follow_source),
                selected = style == "source",
                onClick = { onStyleChange("source") },
            )
            ReviewBubbleOptionRow(
                label = stringResource(R.string.review_bubble_custom),
                selected = style == "custom",
                onClick = onPickSvg,
            )
            if (svgPath.isNotBlank()) {
                TextButton(
                    onClick = onClearSvg,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    AppText(text = stringResource(R.string.review_bubble_clear))
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AppText(
                    text = stringResource(R.string.review_bubble_package_section),
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(onClick = onManagePackages) {
                    AppText(text = stringResource(R.string.bubble_package_manage_action))
                }
            }
            packages.forEach { item ->
                ReviewBubbleOptionRow(
                    label = if (item.builtin) {
                        "${item.name} · ${stringResource(R.string.review_bubble_package_builtin_tag)}"
                    } else {
                        item.name
                    },
                    selected = style == "package" && activePackage == item.dirName,
                    onClick = { onSelectPackage(item.dirName) },
                )
            }
        }
    }
}

@Composable
private fun BubblePreview() {
    val textColor = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
    ) {
        val height = size.height * 0.62f
        val width = height * 1.5f
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f
        drawIntoCanvas { canvas ->
            ReaderReviewBubbleRenderer.drawPreview(
                canvas = canvas.nativeCanvas,
                bounds = RectF(left, top, left + width, top + height),
                text = "12",
                textColorArgb = textColor.toArgb(),
                isNight = ReadStyleResolver.isNightTheme(),
                typeface = null,
            )
        }
    }
}

@Composable
private fun ReviewBubbleOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        AppText(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
