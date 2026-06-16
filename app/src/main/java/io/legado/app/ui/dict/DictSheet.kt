package io.legado.app.ui.dict

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.legado.app.R
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.tabRow.AppTabRow
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

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
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

@OptIn(ExperimentalTextApi::class)
@Composable
private fun DictHtmlContent(
    htmlContent: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(htmlContent) { parseHtmlBlocks(htmlContent) }
    Column(modifier = modifier.padding(horizontal = 32.dp, vertical = 16.dp)) {
        blocks.forEach { block ->
            when (block) {
                is HtmlBlock.Text -> {
                    AppText(
                        text = remember(block.content) {
                            AnnotatedString.fromHtml(block.content)
                        },
                    )
                }
                is HtmlBlock.Image -> {
                    AsyncImage(
                        model = block.url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth,
                    )
                }
            }
        }
    }
}

private sealed interface HtmlBlock {
    data class Text(val content: String) : HtmlBlock
    data class Image(val url: String) : HtmlBlock
}

private val IMG_REGEX = Regex("""<img\s+[^>]*src\s*=\s*["']([^"']+)["'][^>]*/?>""")

private fun parseHtmlBlocks(html: String): List<HtmlBlock> {
    val blocks = mutableListOf<HtmlBlock>()
    var lastIndex = 0
    IMG_REGEX.findAll(html).forEach { match ->
        if (match.range.first > lastIndex) {
            val textPart = html.substring(lastIndex, match.range.first).trim()
            if (textPart.isNotBlank()) blocks.add(HtmlBlock.Text(textPart))
        }
        blocks.add(HtmlBlock.Image(match.groupValues[1]))
        lastIndex = match.range.last + 1
    }
    if (lastIndex < html.length) {
        val remaining = html.substring(lastIndex).trim()
        if (remaining.isNotBlank()) blocks.add(HtmlBlock.Text(remaining))
    }
    return blocks
}
