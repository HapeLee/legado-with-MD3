package io.legado.app.ui.config.themeConfig

import android.content.ComponentName
import android.widget.ImageView
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.ui.main.Launcher0
import io.legado.app.ui.main.Launcher1
import io.legado.app.ui.main.Launcher2
import io.legado.app.ui.main.Launcher3
import io.legado.app.ui.main.Launcher4
import io.legado.app.ui.main.Launcher5
import io.legado.app.ui.main.Launcher6
import io.legado.app.ui.main.LauncherW
import io.legado.app.ui.widget.components.modalBottomSheet.GlassModalBottomSheet
import io.legado.app.utils.getCompatDrawable
import splitties.init.appCtx

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherIconPickerSheet(
    selectedValue: String,
    onDismissRequest: () -> Unit,
    onValueChange: (String) -> Unit
) {
    val context = LocalContext.current
    val icons = LauncherIcons.list

    GlassModalBottomSheet(
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.change_icon),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(icons, key = { it.value }) { item ->

                    val isSelected = item.value == selectedValue
                    val drawable = remember(item.resId) {
                        context.getCompatDrawable(item.resId)
                    }

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.large)
                            .background(
                                if (isSelected)
                                    MaterialTheme.colorScheme.secondaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceContainer
                            )
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
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
                        AndroidView(
                            factory = { ctx ->
                                ImageView(ctx).apply {
                                    scaleType = ImageView.ScaleType.FIT_CENTER
                                }
                            },
                            update = { imageView ->
                                imageView.setImageDrawable(drawable)
                            },
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

data class LauncherIconItem(
    val value: String,
    val label: String,
    val resId: Int,
    val component: ComponentName
)

object LauncherIcons {

    val list = listOf(
        LauncherIconItem(
            value = "ic_launcher",
            label = "iconMain",
            resId = R.mipmap.ic_launcher,
            component = ComponentName(appCtx, LauncherW::class.java)
        ),
        LauncherIconItem(
            value = "launcherw",
            label = "iconWhite",
            resId = R.mipmap.launcherw,
            component = ComponentName(appCtx, LauncherW::class.java)
        ),
        LauncherIconItem(
            value = "launcher0",
            label = "icon0",
            resId = R.mipmap.launcher0,
            component = ComponentName(appCtx, Launcher0::class.java)
        ),
        LauncherIconItem(
            value = "launcher1",
            label = "icon1",
            resId = R.mipmap.launcher1,
            component = ComponentName(appCtx, Launcher1::class.java)
        ),
        LauncherIconItem(
            value = "launcher2",
            label = "icon2",
            resId = R.mipmap.launcher2,
            component = ComponentName(appCtx, Launcher2::class.java)
        ),
        LauncherIconItem(
            value = "launcher3",
            label = "icon3",
            resId = R.mipmap.launcher3,
            component = ComponentName(appCtx, Launcher3::class.java)
        ),
        LauncherIconItem(
            value = "launcher4",
            label = "icon4",
            resId = R.mipmap.launcher4,
            component = ComponentName(appCtx, Launcher4::class.java)
        ),
        LauncherIconItem(
            value = "launcher5",
            label = "icon5",
            resId = R.mipmap.launcher5,
            component = ComponentName(appCtx, Launcher5::class.java)
        ),
        LauncherIconItem(
            value = "launcher6",
            label = "icon6",
            resId = R.mipmap.launcher6,
            component = ComponentName(appCtx, Launcher6::class.java)
        ),
    )

    fun find(value: String?): LauncherIconItem? {
        return list.find { it.value == value }
    }
}