package io.legado.app.ui.book.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.text.AnimatedTextLine
import io.legado.app.ui.widget.components.text.AppText
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        EditFieldCard(
            label = stringResource(R.string.event_character_name),
            value = state.characterName,
            onValueChange = { onIntent(EventDetailIntent.SetCharacterName(it)) },
        )
        EditFieldCard(
            label = stringResource(R.string.event_chapter_title),
            value = state.chapterTitle,
            onValueChange = { onIntent(EventDetailIntent.SetChapterTitle(it)) },
        )
        EditFieldCard(
            label = stringResource(R.string.event_time_text),
            value = state.eventTimeText,
            onValueChange = { onIntent(EventDetailIntent.SetEventTimeText(it)) },
        )
        EditFieldCard(
            label = stringResource(R.string.event_content),
            value = state.content,
            onValueChange = { onIntent(EventDetailIntent.SetContent(it)) },
            multiline = true,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditFieldCard(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    multiline: Boolean = false,
) {
    var showDialog by remember { mutableStateOf(false) }
    var dialogValue by remember(value) { mutableStateOf(value) }

    GlassCard(
        onClick = {
            dialogValue = value
            showDialog = true
        },
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AppText(
                text = label,
                style = LegadoTheme.typography.labelMedium,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedTextLine(
                text = value.ifBlank { "—" },
                style = LegadoTheme.typography.bodyMedium,
                color = if (value.isBlank()) LegadoTheme.colorScheme.onSurfaceVariant else LegadoTheme.colorScheme.onSurface,
            )
        }
    }

    if (showDialog) {
        AppAlertDialog(
            show = showDialog,
            onDismissRequest = { showDialog = false },
            title = label,
            confirmText = stringResource(R.string.apply),
            onConfirm = {
                onValueChange(dialogValue)
                showDialog = false
            },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { showDialog = false },
            content = {
                AppTextField(
                    value = dialogValue,
                    onValueChange = { dialogValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = !multiline,
                    minLines = if (multiline) 4 else 1,
                    maxLines = if (multiline) 12 else 1,
                )
            },
        )
    }
}
