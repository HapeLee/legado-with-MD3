package io.legado.app.feature.settings.themeconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.add
import io.legado.app.feature.settings.res.close
import io.legado.app.feature.settings.res.day
import io.legado.app.feature.settings.res.night
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.text.AppText
import org.jetbrains.compose.resources.stringResource

/** 背景图片对应的额外选项：应用背景图为模糊，大容器/项目背景图为透明度。 */
sealed interface BackgroundImageExtraOption {
    data class Blur(
        val lightTitle: String,
        val darkTitle: String,
        val lightValue: Int,
        val darkValue: Int,
        val onLightChange: (Int) -> Unit,
        val onDarkChange: (Int) -> Unit,
    ) : BackgroundImageExtraOption

    data class Opacity(
        val title: String,
        val value: Int,
        val onValueChange: (Int) -> Unit,
    ) : BackgroundImageExtraOption
}

/**
 * 通用亮/暗背景图管理面板：左右两个图片容器（日间/夜间，空时显示添加卡片，
 * 有图时显示图片与关闭按钮），下方是对应的额外选项（背景虚化或背景图透明度）。
 */
@OptIn(ExperimentalMaterial3Api::class)
/**
 * M5-19a：从 `:app` 的 `ui/config/themeConfig/BackgroundImageManageSheet.kt` 迁入。
 *
 * **正文逐字保留**（脚本化等价改写，非手抄），改写只有三类：包名 →
 * `io.legado.app.feature.settings.themeconfig`；`stringResource` / `stringArrayResource`
 * 换成 CMP 版；`R.string.*`（4 条）/ `R.array.*`（0 个）→ `Res.string.*` / `Res.array.*`
 * 并逐 key import。
 *
 * 该文件经 `tools/audit-slice-deps.py` 勘察为**零阻塞**（无 `:app` 私有依赖、无平台 import），
 * 且无 JVM/Android 专用写法 ⇒ 只需上述三类改写。
 */

@Composable
fun BackgroundImageManageSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    lightPath: String?,
    darkPath: String?,
    extraOption: BackgroundImageExtraOption? = null,
    onSelectLight: () -> Unit,
    onSelectDark: () -> Unit,
    onRemoveLight: () -> Unit,
    onRemoveDark: () -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                BackgroundImageTile(
                    label = stringResource(Res.string.day),
                    path = lightPath,
                    modifier = Modifier.weight(1f),
                    onSelect = onSelectLight,
                    onRemove = onRemoveLight,
                )
                BackgroundImageTile(
                    label = stringResource(Res.string.night),
                    path = darkPath,
                    modifier = Modifier.weight(1f),
                    onSelect = onSelectDark,
                    onRemove = onRemoveDark,
                )
            }

            when (val option = extraOption) {
                is BackgroundImageExtraOption.Blur -> {
                    if (!lightPath.isNullOrBlank()) {
                        SliderSettingItem(
                            title = option.lightTitle,
                            value = option.lightValue.toFloat(),
                            defaultValue = 0f,
                            valueRange = 0f..100f,
                            onValueChange = { option.onLightChange(it.toInt()) },
                        )
                    }
                    if (!darkPath.isNullOrBlank()) {
                        SliderSettingItem(
                            title = option.darkTitle,
                            value = option.darkValue.toFloat(),
                            defaultValue = 0f,
                            valueRange = 0f..100f,
                            onValueChange = { option.onDarkChange(it.toInt()) },
                        )
                    }
                }

                is BackgroundImageExtraOption.Opacity -> {
                    SliderSettingItem(
                        title = option.title,
                        description = "${option.value}%",
                        value = option.value.toFloat(),
                        defaultValue = 100f,
                        valueRange = 0f..100f,
                        onValueChange = { option.onValueChange(it.toInt()) },
                    )
                }

                null -> Unit
            }
        }
    }
}

@Composable
private fun BackgroundImageTile(
    label: String,
    path: String?,
    modifier: Modifier,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = modifier) {
        if (path.isNullOrBlank()) {
            NormalCard(
                onClick = onSelect,
                cornerRadius = 12.dp,
                containerColor = LegadoTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(Res.string.add),
                        modifier = Modifier.size(48.dp),
                        tint = LegadoTheme.colorScheme.primary
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth()) {
                NormalCard(
                    cornerRadius = 12.dp,
                ) {
                    AsyncImage(
                        model = path,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        contentScale = ContentScale.Crop
                    )
                }
                SmallTonalButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(32.dp),
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.close)
                )
            }
        }
        AppText(
            text = label,
            style = LegadoTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
        )
    }
}
