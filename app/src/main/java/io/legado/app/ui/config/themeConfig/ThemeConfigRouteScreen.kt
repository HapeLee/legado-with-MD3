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
                val destinationFile = File(iconDir, "$destination.png")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destinationFile.outputStream().use(input::copyTo)
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
            viewModel.onIntent(
                ThemeConfigIntent.SelectBackground(uri.toString(), pendingBackgroundDark)
            )
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
                    pendingBackgroundDark = effect.dark
                    backgroundImageLauncher.launch("image/*")
                }
                is ThemeConfigEffect.ShowToast -> context.toastOnUi(effect.stringRes)
                is ThemeConfigEffect.OpenTimePicker -> {
                    // Time picker intent handled by ThemeConfigScreen internally
                }
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
