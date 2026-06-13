package io.legado.app.ui.dict

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.help.GlideImageGetter
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.utils.setHtml
import kotlinx.coroutines.flow.Flow
import org.koin.androidx.compose.koinViewModel

@Composable
fun DictSheet(
    show: Boolean,
    word: String,
    onDismissRequest: () -> Unit,
    viewModel: DictViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(show, word) {
        if (show) {
            viewModel.onIntent(DictIntent.Load(word))
        }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = word,
    ) {
        DictSheetContent(
            state = state,
            onIntent = viewModel::onIntent,
            effects = viewModel.effects,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun DictSheetContent(
    state: DictUiState,
    onIntent: (DictIntent) -> Unit,
    effects: Flow<DictEffect>,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is DictEffect.ShowToast -> Unit
            }
        }
    }

    DictContent(
        state = state,
        onIntent = onIntent,
        modifier = modifier,
    )
}

@Composable
private fun DictContent(
    state: DictUiState,
    onIntent: (DictIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val blankWordText = stringResource(R.string.cannot_empty)
    val emptyText = stringResource(R.string.empty)
    val searchEmptyText = stringResource(R.string.search_empty)
    val tabTitles = remember(state.rules) { state.rules.map { it.name } }
    val emptyMessage = when (state.emptyReason) {
        DictEmptyReason.BlankWord -> blankWordText
        DictEmptyReason.NoRules -> emptyText
        DictEmptyReason.NoResult -> searchEmptyText
        null -> null
    }

    Column(modifier = modifier) {
        if (state.rules.size > 1) {
            AppTabRow(
                tabTitles = tabTitles,
                selectedTabIndex = state.selectedIndex.coerceIn(0, state.rules.lastIndex),
                onTabSelected = { index ->
                    onIntent(DictIntent.SelectRule(index))
                },
                modifier = Modifier.fillMaxWidth(),
                isScrollable = true,
            )
        }

        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppCircularProgressIndicator()
                }
            }

            emptyMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyMessage(message = emptyMessage)
                }
            }

            state.htmlContent.isNotBlank() -> {
                DictHtmlContent(
                    htmlContent = state.htmlContent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DictHtmlContent(
    htmlContent: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var glideImageGetter by remember { mutableStateOf<GlideImageGetter?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            glideImageGetter?.clear()
            glideImageGetter = null
        }
    }

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod()
                setPadding(32, 16, 32, 16)
            }
        },
        update = { textView ->
            glideImageGetter?.clear()
            glideImageGetter = GlideImageGetter.create(context, textView, htmlContent)
            textView.setHtml(htmlContent, glideImageGetter)
        },
        modifier = modifier,
    )
}
