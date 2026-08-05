package io.legado.app.ui.book.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
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

private val KNOWLEDGE_TYPE_LABELS = mapOf(
    "world_rule" to R.string.knowledge_type_world_rule,
    "location" to R.string.knowledge_type_location,
    "faction" to R.string.knowledge_type_faction,
    "object" to R.string.knowledge_type_object,
    "terminology" to R.string.knowledge_type_terminology,
    "timeline" to R.string.knowledge_type_timeline,
    "style" to R.string.knowledge_type_style,
    "theme" to R.string.knowledge_type_theme,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookKnowledgeDetailScreen(
    state: KnowledgeDetailUiState,
    onIntent: (KnowledgeDetailIntent) -> Unit,
    effects: Flow<KnowledgeDetailEffect>,
    onBack: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                is KnowledgeDetailEffect.ShowToast -> context.toastOnUi(effect.message)
                KnowledgeDetailEffect.NavigateBack -> onBack()
            }
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.knowledge_detail),
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBack)
                },
                actions = {
                    if (state.entryId != null) {
                        TopBarActionButton(
                            onClick = { showDeleteDialog = true },
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                        )
                    }
                    TopBarActionButton(
                        onClick = { onIntent(KnowledgeDetailIntent.Save) },
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
            KnowledgeDetailContent(
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

    AppAlertDialog(
        show = showDeleteDialog,
        onDismissRequest = { showDeleteDialog = false },
        title = stringResource(R.string.delete),
        text = stringResource(R.string.sure_delete),
        confirmText = stringResource(R.string.delete),
        onConfirm = { onIntent(KnowledgeDetailIntent.Delete); showDeleteDialog = false },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { showDeleteDialog = false },
    )
}

@Composable
private fun KnowledgeDetailContent(
    state: KnowledgeDetailUiState,
    onIntent: (KnowledgeDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KnowledgeTypeSelector(
            selectedType = state.type,
            onTypeSelected = { onIntent(KnowledgeDetailIntent.SetType(it)) },
        )
        KnowledgeEditFieldCard(
            label = stringResource(R.string.knowledge_title),
            value = state.title,
            onValueChange = { onIntent(KnowledgeDetailIntent.SetTitle(it)) },
        )
        KnowledgeFlowTagEditor(
            title = stringResource(R.string.knowledge_keywords),
            tags = state.keywords,
            onAddTag = { onIntent(KnowledgeDetailIntent.AddKeyword(it)) },
            onRemoveTag = { onIntent(KnowledgeDetailIntent.RemoveKeyword(it)) },
        )
        KnowledgeEditFieldCard(
            label = stringResource(R.string.knowledge_content),
            value = state.content,
            onValueChange = { onIntent(KnowledgeDetailIntent.SetContent(it)) },
            multiline = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KnowledgeEditFieldCard(
                label = stringResource(R.string.knowledge_scope_start),
                value = state.scopeStartChapter,
                onValueChange = { onIntent(KnowledgeDetailIntent.SetScopeStart(it)) },
                modifier = Modifier.weight(1f),
            )
            KnowledgeEditFieldCard(
                label = stringResource(R.string.knowledge_scope_end),
                value = state.scopeEndChapter,
                onValueChange = { onIntent(KnowledgeDetailIntent.SetScopeEnd(it)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun KnowledgeTypeSelector(
    selectedType: String,
    onTypeSelected: (String) -> Unit,
) {
    var showDropdown by remember { mutableStateOf(false) }
    val currentLabel = KNOWLEDGE_TYPE_LABELS[selectedType]?.let { stringResource(it) } ?: "—"

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppText(
            text = stringResource(R.string.knowledge_type),
            style = LegadoTheme.typography.titleMedium,
        )
        Box {
            GlassCard(
                onClick = { showDropdown = true },
                containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
            ) {
                AnimatedTextLine(
                    text = currentLabel,
                    style = LegadoTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            RoundDropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false },
            ) {
                KNOWLEDGE_TYPE_LABELS.forEach { (type, labelRes) ->
                    RoundDropdownMenuItem(
                        text = stringResource(labelRes),
                        onClick = {
                            onTypeSelected(type)
                            showDropdown = false
                        },
                    )
                }
            }
        }
    }
}
