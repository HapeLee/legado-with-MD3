package io.legado.app.ui.book.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.text.AnimatedTextLine
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookEventListScreen(
    state: EventListUiState,
    onIntent: (EventListIntent) -> Unit,
    effects: Flow<EventListEffect>,
    onBack: () -> Unit,
    onOpenDetail: (eventId: String?) -> Unit,
    onRefresh: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                is EventListEffect.OpenEventDetail -> onOpenDetail(effect.eventId)
                is EventListEffect.ShowToast -> context.toastOnUi(effect.message)
            }
        }
    }

    DisposableEffect(lifecycleOwner, onRefresh) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.event_list),
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBack)
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            AppFloatingActionButton(
                onClick = { onIntent(EventListIntent.AddEvent) },
                icon = Icons.Default.Add,
                tooltipText = stringResource(R.string.add_event),
            )
        },
    ) { paddingValues ->
        if (state.isLoading && state.entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                AppCircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = adaptiveContentPadding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = 120.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.entries.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 64.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedTextLine(
                                text = stringResource(R.string.event_empty_hint),
                                style = LegadoTheme.typography.bodyMedium,
                                color = LegadoTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(
                    items = state.entries,
                    key = { it.id },
                ) { entry ->
                    EventEntryCard(
                        entry = entry,
                        onClick = { onIntent(EventListIntent.OpenEvent(entry.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EventEntryCard(
    entry: EventListItemUi,
    onClick: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val header = listOfNotNull(
                entry.characterName.takeIf { it.isNotBlank() },
                entry.chapterTitle.takeIf { it.isNotBlank() },
                entry.eventTimeText.takeIf { it.isNotBlank() },
            ).joinToString(" · ").ifBlank { stringResource(R.string.event_detail) }
            AnimatedTextLine(
                text = header,
                style = LegadoTheme.typography.titleSmall,
            )
            AnimatedTextLine(
                text = entry.content.ifBlank { stringResource(R.string.event_content) },
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}
