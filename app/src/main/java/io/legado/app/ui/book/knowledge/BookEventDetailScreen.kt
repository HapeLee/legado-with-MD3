package io.legado.app.ui.book.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookEventDetailScreen(
    state: EventDetailUiState,
    onIntent: (EventDetailIntent) -> Unit,
    effects: Flow<EventDetailEffect>,
    onBack: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val context = LocalContext.current

    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                is EventDetailEffect.ShowToast -> context.toastOnUi(effect.message)
            }
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.event_detail),
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBack)
                },
                actions = {
                    TopBarActionButton(
                        onClick = { onIntent(EventDetailIntent.Save) },
                        imageVector = Icons.Default.Save,
                        contentDescription = stringResource(R.string.save),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                AppCircularProgressIndicator()
            }
        } else {
            EventDetailContent(
                state = state,
                onIntent = onIntent,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .consumeWindowInsets(paddingValues)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun EventDetailContent(
    state: EventDetailUiState,
    onIntent: (EventDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KnowledgeEditFieldCard(
            label = stringResource(R.string.event_character_name),
            value = state.characterName,
            onValueChange = { onIntent(EventDetailIntent.SetCharacterName(it)) },
        )
        KnowledgeEditFieldCard(
            label = stringResource(R.string.event_chapter_title),
            value = state.chapterTitle,
            onValueChange = { onIntent(EventDetailIntent.SetChapterTitle(it)) },
        )
        KnowledgeEditFieldCard(
            label = stringResource(R.string.event_time_text),
            value = state.eventTimeText,
            onValueChange = { onIntent(EventDetailIntent.SetEventTimeText(it)) },
        )
        KnowledgeEditFieldCard(
            label = stringResource(R.string.event_content),
            value = state.content,
            onValueChange = { onIntent(EventDetailIntent.SetContent(it)) },
            multiline = true,
        )
    }
}
