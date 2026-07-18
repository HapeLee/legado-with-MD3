package io.legado.app.ui.config.customTheme

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.constant.EventBus
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.postEvent
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun CustomThemeRouteScreen(
    onBackClick: () -> Unit,
    viewModel: CustomThemeViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    LaunchedEffect(viewModel, context) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is CustomThemeEffect.ApplyLegacyPrimarySeed -> {
                    ThemeStore.editTheme(context).primaryColor(effect.color).apply()
                    postEvent(EventBus.RECREATE, "")
                }
                is CustomThemeEffect.SettingsUpdateFailed -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    CustomThemeScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
    )
}
