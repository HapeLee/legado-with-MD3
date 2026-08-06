package io.legado.app.ui.config.themeConfig

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.constant.EventBus
import io.legado.app.help.LauncherIconHelp
import io.legado.app.help.config.ThemeConfigStore
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.postEvent
import io.legado.app.utils.takePersistablePermissionSafely
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import java.io.File

@Composable
fun ThemeConfigRouteScreen(
    onBackClick: () -> Unit,
    onNavigateToCustomTheme: () -> Unit,
    onNavigateToThemeManage: () -> Unit,
    viewModel: ThemeConfigViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingNavigationDestination by remember { mutableStateOf<String?>(null) }
    var pendingBackgroundDark by remember { mutableStateOf(false) }
    var pendingContainerBackground by remember { mutableStateOf<ContainerBackgroundTarget?>(null) }

    val fontFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            uri.takePersistablePermissionSafely(context, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            viewModel.onIntent(ThemeConfigIntent.SetFontFolder(uri.toString()))
        }
    }
    val navigationIconLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val destination = pendingNavigationDestination
        pendingNavigationDestination = null
        if (uri != null && destination != null) {
            runCatching {
                val iconDir = File(context.filesDir, "nav_icons").apply { mkdirs() }
                val input = context.contentResolver.openInputStream(uri)
                val destinationFile = if (input != null) {
                    // 以内容摘要命名：同一张图重复选择命中同一路径，不同图片路径不同，
                    // 避免覆盖固定文件名导致 Coil 缓存到旧图、修改后无法即时生效。
                    val digest = input.use(MD5Utils::md5Encode)
                    val file = File(iconDir, "$digest.png")
                    if (!file.exists()) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            file.outputStream().use(stream::copyTo)
                        }
                    }
                    file
                } else {
                    return@runCatching
                }
                viewModel.onIntent(
                    ThemeConfigIntent.SelectNavigationIcon(
                        destination = destination,
                        path = destinationFile.absolutePath,
                    )
                )
            }
        }
    }
    val backgroundImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pendingContainerBackground?.let { target ->
                viewModel.onIntent(ThemeConfigIntent.SelectContainerBackground(target, pendingBackgroundDark, uri.toString()))
                pendingContainerBackground = null
            } ?: viewModel.onIntent(ThemeConfigIntent.SelectBackground(uri.toString(), pendingBackgroundDark))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                ThemeConfigEffect.ApplyDayNight -> ThemeConfigStore.applyDayNightLive()
                ThemeConfigEffect.NotifyMain -> postEvent(EventBus.NOTIFY_MAIN, true)
                is ThemeConfigEffect.ChangeLauncherIcon ->
                    LauncherIconHelp.changeIcon(effect.value)
                ThemeConfigEffect.OpenFontFolder -> fontFolderLauncher.launch(null)
                is ThemeConfigEffect.OpenNavigationIcon -> {
                    pendingNavigationDestination = effect.destination
                    navigationIconLauncher.launch("image/png")
                }
                is ThemeConfigEffect.OpenBackgroundImage -> {
                    pendingContainerBackground = null
                    pendingBackgroundDark = effect.dark
                    backgroundImageLauncher.launch("image/*")
                }
                is ThemeConfigEffect.OpenContainerBackgroundImage -> {
                    pendingContainerBackground = effect.target
                    pendingBackgroundDark = effect.dark
                    backgroundImageLauncher.launch("image/*")
                }
                is ThemeConfigEffect.ShowToast -> context.toastOnUi(effect.stringRes)
            }
        }
    }

    ThemeConfigScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
        onNavigateToCustomTheme = onNavigateToCustomTheme,
        onNavigateToThemeManage = onNavigateToThemeManage,
    )
}
