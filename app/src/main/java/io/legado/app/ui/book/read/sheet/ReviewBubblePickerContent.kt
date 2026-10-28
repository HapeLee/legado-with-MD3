package io.legado.app.ui.book.read.sheet

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.feature.reader.platform.ReaderReviewBubbleRenderer
import io.legado.app.feature.reader.platform.ReviewBubbleAppearances
import io.legado.app.help.config.BubblePackageManager
import io.legado.app.help.config.ReadStyleResolver
import io.legado.app.model.ReadBook
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * 阅读器内的段评气泡快速切换：只改当前气泡包，不动主题与排版设置。
 *
 * 换包后立即重新分页，返回阅读页即可看到新气泡；`source` 表示继续用书源自己的段评图。
 */
@Composable
fun ReviewBubblePickerContent() {
    val settingsGateway = koinInject<ReadSettingsGateway>()
    val scope = rememberCoroutineScope()
    var packages by remember { mutableStateOf<List<BubblePackageManager.Entry>>(emptyList()) }
    var followSource by remember {
        mutableStateOf(settingsGateway.currentSettings.reviewBubbleStyle == "source")
    }
    var activeDirName by remember { mutableStateOf(BubblePackageManager.activeDirName()) }

    LaunchedEffect(Unit) {
        packages = withContext(Dispatchers.IO) {
            runCatching { BubblePackageManager.loadEntries() }.getOrDefault(emptyList())
        }
    }

    fun apply(followSourceValue: Boolean, dirName: String) {
        scope.launch {
            runCatching {
                settingsGateway.update {
                    if (followSourceValue) {
                        it.copy(reviewBubbleStyle = "source")
                    } else {
                        it.copy(reviewBubbleStyle = "package", reviewBubblePackage = dirName)
                    }
                }
                BubblePackageManager.invalidateCurrentEntry()
            }.onSuccess {
                followSource = followSourceValue
                activeDirName = dirName
                ReadBook.loadContent(false)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ReviewBubbleOptionRow(
            label = stringResource(R.string.review_bubble_follow_source),
            selected = followSource,
            onClick = { apply(true, activeDirName) },
        )
        packages.forEach { entry ->
            ReviewBubbleOptionRow(
                label = if (entry.source == BubblePackageManager.Source.BUILTIN) {
                    "${entry.config.name} · ${stringResource(R.string.review_bubble_package_builtin_tag)}"
                } else {
                    entry.config.name
                },
                selected = !followSource && activeDirName == entry.dirName,
                preview = {
                    BubblePreview(appearanceEntry = entry)
                },
                onClick = { apply(false, entry.dirName) },
            )
        }
    }
}

@Composable
private fun ReviewBubbleOptionRow(
    label: String,
    selected: Boolean,
    preview: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        AppText(
            text = label,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        preview?.invoke()
    }
}

@Composable
private fun BubblePreview(appearanceEntry: BubblePackageManager.Entry) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val appearance = remember(appearanceEntry.dirName, appearanceEntry.config.updatedAt) {
        ReviewBubbleAppearances.of(appearanceEntry)
    }
    Canvas(
        modifier = Modifier
            .height(28.dp)
            .fillMaxWidth(0.28f),
    ) {
        val height = size.height
        val width = height * appearance.aspectRatio
        drawIntoCanvas { canvas ->
            ReaderReviewBubbleRenderer.drawAppearance(
                canvas = canvas.nativeCanvas,
                bounds = RectF(0f, 0f, width, height),
                appearance = appearance,
                text = "12",
                status = "normal",
                colorOverride = null,
                isNight = ReadStyleResolver.isNightTheme(),
                fallbackTextColorArgb = textColor.toArgb(),
                typeface = null,
            )
        }
    }
}
