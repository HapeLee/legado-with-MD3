package io.legado.app.feature.settings.themeconfig

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.change_icon
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import org.jetbrains.compose.resources.stringResource

/**
 * 一个「启动图标」选项（M5-19e）。
 *
 * ⚠️ **图像由宿主渲染**：原实现直接在 sheet 里用 `R.mipmap.*` + `getCompatDrawable` +
 * `AndroidView(ImageView)` 画图标，而这三样都是平台 API ⇒ 共享层只接一个渲染槽。
 * 形状沿用同模块既有先例 `BackgroundImageExtraOption`（宿主把数据与回调摊平后作为参数传入），
 * 不用 CompositionLocal —— 这是**必需**数据（不是可选装饰），没有「未安装就降级」的余地。
 *
 * `value` 既是对外存储的值，也是网格的 key（同迁移前 `key = { it.value }`）。
 */
class LauncherIconOption(
    val value: String,
    val image: @Composable (modifier: Modifier) -> Unit,
)

/**
 * M5-19e：从 `:app` 的 `ui/config/themeConfig/LauncherIconPickerSheet.kt` 迁入。
 *
 * **正文逐字保留**（脚本化等价改写），改写类别：
 * 1. 包名 → `io.legado.app.feature.settings.themeconfig`；
 * 2. `stringResource` → CMP 版；`R.string.*`（1 条）→ `Res.string.*` + 逐 key import；
 * 3. ⚠️ **数据与渲染改由宿主注入**：原来 `val icons = LauncherIcons.list` 与
 *    `context.getCompatDrawable(item.resId)` + `AndroidView(ImageView)` 都在本文件里，
 *    而 `R.mipmap` / `getCompatDrawable` / `ImageView` 都是平台 API ⇒ 换成入参
 *    `icons: List<LauncherIconOption>`，每格调 `item.image(Modifier.size(48.dp))`。
 *    宿主实现在 `:app` 的 `LauncherIconOptions.kt`（渲染与迁移前**逐字等价**）；
 *    原先内联在同文件尾部的 `LauncherIconItem` / `LauncherIcons` 两个声明也随之留在宿主。
 * 4. 网格布局、选中态（`secondaryContainer` + `primary` 2dp 边框）、`onValueChange` 后
 *    立即 `onDismissRequest()`、`key = { it.value }`、`48.dp` 尺寸、`aspectRatio(1f)` 与
 *    圆角 **逐字保留**。
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherIconPickerSheet(
    show: Boolean,
    selectedValue: String,
    icons: List<LauncherIconOption>,
    onDismissRequest: () -> Unit,
    onValueChange: (String) -> Unit
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(Res.string.change_icon)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(icons, key = { it.value }) { item ->

                    val isSelected = item.value == selectedValue

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.large)
                            .background(
                                if (isSelected)
                                    LegadoTheme.colorScheme.secondaryContainer
                                else
                                    LegadoTheme.colorScheme.surfaceContainer
                            )
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = LegadoTheme.colorScheme.primary,
                                        shape = MaterialTheme.shapes.large
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable {
                                onValueChange(item.value)
                                onDismissRequest()
                            }
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        item.image(Modifier.size(48.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
