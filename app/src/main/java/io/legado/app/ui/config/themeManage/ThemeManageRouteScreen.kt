package io.legado.app.ui.config.themeManage

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
import io.legado.app.R
import io.legado.app.help.config.SavedTheme
import io.legado.app.help.config.ThemePackageManager
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun ThemeManageRouteScreen(
    onBackClick: () -> Unit,
    viewModel: ThemeManageViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var exportTarget by remember { mutableStateOf<SavedTheme?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            val target = exportTarget
            exportTarget = null
            viewModel.onIntent(
                ThemeManageIntent.ExportPackage(
                    uri = uri.toString(),
                    themeName = target?.name,
                    themeData = target?.data,
                    savedTheme = target,
                )
            )
        }
    }
    val importPackageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.onIntent(ThemeManageIntent.ImportPackage(uri.toString()))
    }
    val importLegacyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.onIntent(ThemeManageIntent.ImportLegacyJson(uri.toString()))
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ThemeManageEffect.OpenExportDocument -> {
                    exportTarget = effect.theme
                    val name = effect.theme?.name ?: "materado_theme_${System.currentTimeMillis()}"
                    exportLauncher.launch("$name.${ThemePackageManager.FILE_EXTENSION}")
                }
                ThemeManageEffect.OpenImportPackage -> importPackageLauncher.launch(
                    arrayOf("application/zip", "application/octet-stream")
                )
                ThemeManageEffect.OpenImportLegacyJson -> importLegacyLauncher.launch(
                    arrayOf("application/json", "text/json")
                )
                is ThemeManageEffect.LegacyMigrationFinished -> {
                    val message = if (effect.failedCount == 0) {
                        context.getString(R.string.theme_manage_migrate_success, effect.migratedCount)
                    } else {
                        context.getString(
                            R.string.theme_manage_migrate_partial,
                            effect.migratedCount,
                            effect.failedCount,
                        )
                    }
                    context.toastOnUi(message)
                }
                is ThemeManageEffect.ShowResult -> {
                    val message = buildString {
                        append(context.getString(effect.messageRes))
                        effect.detail?.takeIf(String::isNotBlank)?.let {
                            append('\n')
                            append(it)
                        }
                    }
                    context.toastOnUi(message)
                }
            }
        }
    }

    ThemeManageScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
    )
}
