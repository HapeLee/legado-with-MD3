package io.legado.app.ui.book.knowledge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.text.AnimatedTextLine
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookCharacterNetworkScreen(
    state: CharacterNetworkUiState,
    onIntent: (CharacterNetworkIntent) -> Unit,
    effects: Flow<CharacterNetworkEffect>,
    onBack: () -> Unit,
    onOpenCharacterDetail: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                is CharacterNetworkEffect.OpenCharacterDetail -> onOpenCharacterDetail(effect.characterId)
                is CharacterNetworkEffect.ShowToast -> context.toastOnUi(effect.message)
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
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.character_network),
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBack)
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        if (state.isLoading && state.characters.isEmpty() && state.relations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                AppCircularProgressIndicator()
            }
        } else {
            CharacterNetworkContent(
                state = state,
                onIntent = onIntent,
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                paddingValues = paddingValues,
            )
        }
    }
}

@Composable
private fun CharacterNetworkContent(
    state: CharacterNetworkUiState,
    onIntent: (CharacterNetworkIntent) -> Unit,
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = adaptiveContentPadding(
            top = paddingValues.calculateTopPadding(),
            bottom = 120.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            CharacterNodeStrip(
                characters = state.characters,
                onOpenCharacter = {
                    onIntent(CharacterNetworkIntent.OpenCharacter(it))
                },
            )
        }
        item {
            AddRelationCard(
                state = state,
                onIntent = onIntent,
            )
        }
        item {
            AppText(
                text = stringResource(R.string.character_relations),
                style = LegadoTheme.typography.titleMedium,
            )
        }
        if (state.relations.isEmpty()) {
            item {
                AnimatedTextLine(
                    text = stringResource(R.string.character_relations_empty),
                    style = LegadoTheme.typography.bodyMedium,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(
                items = state.relations,
                key = { it.id },
            ) { relation ->
                RelationEditorCard(
                    relation = relation,
                    isSaving = state.isSaving,
                    onIntent = onIntent,
                )
            }
        }
    }
}

@Composable
private fun CharacterNodeStrip(
    characters: List<CharacterNodeUi>,
    onOpenCharacter: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppText(
            text = stringResource(R.string.book_characters),
            style = LegadoTheme.typography.titleMedium,
        )
        if (characters.isEmpty()) {
            AnimatedTextLine(
                text = stringResource(R.string.character_empty_hint),
                style = LegadoTheme.typography.bodyMedium,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = characters,
                    key = { it.id },
                ) { character ->
                    CharacterNodeChip(
                        character = character,
                        onClick = { onOpenCharacter(character.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterNodeChip(
    character: CharacterNodeUi,
    onClick: () -> Unit,
) {
    GlassCard(
        onClick = onClick,
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CharacterAvatar(
                avatarUri = character.avatarUri,
                modifier = Modifier.size(32.dp),
            )
            AnimatedTextLine(
                text = character.name,
                style = LegadoTheme.typography.bodyMedium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AddRelationCard(
    state: CharacterNetworkUiState,
    onIntent: (CharacterNetworkIntent) -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppText(
                text = stringResource(R.string.add_character_relation),
                style = LegadoTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(
                    value = state.newFromName,
                    onValueChange = { onIntent(CharacterNetworkIntent.SetNewFromName(it)) },
                    label = stringResource(R.string.relation_from),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                AppTextField(
                    value = state.newToName,
                    onValueChange = { onIntent(CharacterNetworkIntent.SetNewToName(it)) },
                    label = stringResource(R.string.relation_to),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            AppTextField(
                value = state.newRelationType,
                onValueChange = { onIntent(CharacterNetworkIntent.SetNewRelationType(it)) },
                label = stringResource(R.string.relation_type),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            AppTextField(
                value = state.newAttitude,
                onValueChange = { onIntent(CharacterNetworkIntent.SetNewAttitude(it)) },
                label = stringResource(R.string.relation_attitude),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            AppTextField(
                value = state.newSummary,
                onValueChange = { onIntent(CharacterNetworkIntent.SetNewSummary(it)) },
                label = stringResource(R.string.relation_summary),
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
            )
            MediumTonalButton(
                onClick = { onIntent(CharacterNetworkIntent.AddRelation) },
                enabled = !state.isSaving,
                icon = Icons.Default.Add,
                text = stringResource(R.string.add_character_relation),
            )
        }
    }
}

@Composable
private fun RelationEditorCard(
    relation: CharacterRelationEditorUi,
    isSaving: Boolean,
    onIntent: (CharacterNetworkIntent) -> Unit,
) {
    val showDeleteDialog = remember { mutableStateOf(false) }
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedTextLine(
                    text = "${relation.fromName} - ${relation.toName}",
                    style = LegadoTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                SmallTonalButton(
                    onClick = { onIntent(CharacterNetworkIntent.SaveRelation(relation.id)) },
                    enabled = !isSaving,
                    icon = Icons.Default.Save,
                    contentDescription = stringResource(R.string.save),
                )
                SmallTonalButton(
                    onClick = { showDeleteDialog.value = true },
                    enabled = !isSaving,
                    icon = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                )
            }
            AppTextField(
                value = relation.relationType,
                onValueChange = {
                    onIntent(CharacterNetworkIntent.SetRelationType(relation.id, it))
                },
                label = stringResource(R.string.relation_type),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            AppTextField(
                value = relation.attitude,
                onValueChange = {
                    onIntent(CharacterNetworkIntent.SetRelationAttitude(relation.id, it))
                },
                label = stringResource(R.string.relation_attitude),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            AppTextField(
                value = relation.summary,
                onValueChange = {
                    onIntent(CharacterNetworkIntent.SetRelationSummary(relation.id, it))
                },
                label = stringResource(R.string.relation_summary),
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
            )
        }
    }
    AppAlertDialog(
        show = showDeleteDialog.value,
        onDismissRequest = { showDeleteDialog.value = false },
        title = stringResource(R.string.delete),
        text = stringResource(R.string.sure_delete),
        confirmText = stringResource(R.string.delete),
        onConfirm = {
            onIntent(CharacterNetworkIntent.DeleteRelation(relation.id))
            showDeleteDialog.value = false
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { showDeleteDialog.value = false },
    )
}

@Composable
private fun CharacterAvatar(
    avatarUri: String?,
    modifier: Modifier = Modifier,
) {
    val avatarLoadFailed = remember(avatarUri) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(LegadoTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUri.isNullOrBlank() && !avatarLoadFailed.value) {
            AsyncImage(
                model = avatarUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { avatarLoadFailed.value = true },
            )
        } else {
            AppIcon(Icons.Default.Person, null)
        }
    }
}
