package io.legado.app.ui.config.themeManage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.feature.settings.thememanage.SavedThemeSummary
import io.legado.app.feature.settings.thememanage.ThemeManageEffect
import io.legado.app.feature.settings.thememanage.ThemeManageIntent
import io.legado.app.feature.settings.thememanage.ThemeManageScreen
import io.legado.app.feature.settings.thememanage.ThemeManageText
import io.legado.app.feature.settings.thememanage.ThemeManageViewModel
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
    var exportTarget by remember { mutableStateOf<SavedThemeSummary?>(null) }

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
                    savedThemeName = target?.name,
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
                        append(context.getString(effect.text.toStringRes()))
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

/**
 * M5-16a：VM 搬进 `:feature:settings` 后，结果文案不再传 `@StringRes Int`
 * （共享层拿不到 `R`），而是传语义枚举 [ThemeManageText]；**由宿主把它映射回自己的文案**。
 *
 * 为什么这 7 条**没有**搬进 `:feature:settings` 的 composeResources：这条 effect 由本文件
 * 消费（它要 `context.toastOnUi`），文案属宿主这次 toast 的渲染 ⇒ 搬过去只会得到两份
 * 要手动同步的副本。与 `theme_manage_migrate_success/_partial` 留在宿主拼是同一判据。
 */
@StringRes
private fun ThemeManageText.toStringRes(): Int = when (this) {
    ThemeManageText.SaveFailed -> R.string.theme_manage_save_failed
    ThemeManageText.ApplyFailed -> R.string.theme_manage_apply_failed
    ThemeManageText.DeleteFailed -> R.string.theme_manage_delete_failed
    ThemeManageText.ExportSuccess -> R.string.theme_manage_export_success
    ThemeManageText.ExportFailed -> R.string.theme_manage_export_failed
    ThemeManageText.ImportSuccess -> R.string.theme_manage_import_success
    ThemeManageText.ImportFailed -> R.string.theme_manage_import_failed
}
