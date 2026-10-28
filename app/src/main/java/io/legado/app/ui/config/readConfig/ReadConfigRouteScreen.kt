package io.legado.app.ui.config.readConfig

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun ReadConfigRouteScreen(
    onBackClick: () -> Unit,
    onNavigateToBubblePackages: () -> Unit,
    viewModel: ReadConfigViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val reviewBubbleSvgPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onIntent(ReadConfigIntent.ReviewBubbleSvgSelected(it)) }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ReadConfigEffect.SettingsUpdateFailed -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    ReadConfigScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
        onPickReviewBubbleSvg = { reviewBubbleSvgPicker.launch("*/*") },
        onNavigateToBubblePackages = onNavigateToBubblePackages,
    )
}
