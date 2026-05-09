package io.legado.app.ui.config.themeConfig

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.legado.app.ui.config.mainConfig.MainConfig
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.SmallTonalIconButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import java.io.File

private data class NavIconDestination(
    val key: String,
    val label: String,
    val path: String,
    val onSetPath: (String) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavIconManageSheet(
    show: Boolean,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var activeDest by remember { mutableStateOf<String?>(null) }

    val destinations = listOf(
        NavIconDestination("bookshelf", "书架", MainConfig.navIconBookshelf) { MainConfig.navIconBookshelf = it },
        NavIconDestination("explore", "发现", MainConfig.navIconExplore) { MainConfig.navIconExplore = it },
        NavIconDestination("rss", "订阅", MainConfig.navIconRss) { MainConfig.navIconRss = it },
        NavIconDestination("my", "我的", MainConfig.navIconMy) { MainConfig.navIconMy = it },
    )

    val selectImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val dest = activeDest ?: return@rememberLauncherForActivityResult
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            val iconDir = File(context.filesDir, "nav_icons")
            iconDir.mkdirs()
            val destFile = File(iconDir, "$dest.png")
            inputStream?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destinations.firstOrNull { it.key == dest }?.onSetPath?.invoke(destFile.absolutePath)
        }
        activeDest = null
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = "导航栏图标",
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                destinations.forEach { dest ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        NormalCard(
                            onClick = {
                                activeDest = dest.key
                                selectImage.launch("image/png")
                            },
                            cornerRadius = 12.dp,
                            containerColor = LegadoTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                        ) {
                            if (dest.path.isNotEmpty()) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = dest.path,
                                        contentDescription = dest.label,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                    SmallTonalIconButton(
                                        onClick = { dest.onSetPath("") },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(24.dp),
                                        imageVector = Icons.Default.Close
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppIcon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "添加${dest.label}图标",
                                        modifier = Modifier.size(32.dp),
                                        tint = LegadoTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        AppText(
                            text = dest.label,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
