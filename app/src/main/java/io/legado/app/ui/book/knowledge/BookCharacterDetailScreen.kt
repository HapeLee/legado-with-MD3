package io.legado.app.ui.book.knowledge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.legado.app.R
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.icon.AppIcon
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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookCharacterDetailScreen(
    state: CharacterDetailUiState,
    onIntent: (CharacterDetailIntent) -> Unit,
    effects: Flow<CharacterDetailEffect>,
    onBack: () -> Unit,
    onPickAvatar: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val context = LocalContext.current

    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                is CharacterDetailEffect.ShowToast -> context.toastOnUi(effect.message)
            }
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.character_detail),
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBack)
                },
                actions = {
                    TopBarActionButton(
                        onClick = { onIntent(CharacterDetailIntent.Save) },
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
            CharacterDetailContent(
                state = state,
                onIntent = onIntent,
                onPickAvatar = onPickAvatar,
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
private fun CharacterDetailContent(
    state: CharacterDetailUiState,
    onIntent: (CharacterDetailIntent) -> Unit,
    onPickAvatar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showProfileDialog by remember { mutableStateOf(false) }
    var dialogName by remember(state.name) { mutableStateOf(state.name) }
    var dialogAliases by remember(state.aliasesText) { mutableStateOf(state.aliasesText) }
    var dialogRole by remember(state.role) { mutableStateOf(state.role) }

    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CharacterHeader(
            name = state.name,
            aliasesText = state.aliasesText,
            avatarUri = state.avatarUri,
            roleDisplayName = state.role,
            tags = state.tags,
            onPickAvatar = onPickAvatar,
            onInfoClick = {
                dialogName = state.name
                dialogAliases = state.aliasesText
                dialogRole = state.role
                showProfileDialog = true
            },
        )
        KnowledgeFlowTagEditor(
            title = stringResource(R.string.character_tags),
            tags = state.tags,
            onAddTag = { onIntent(CharacterDetailIntent.AddTag(it)) },
            onRemoveTag = { onIntent(CharacterDetailIntent.RemoveTag(it)) },
            showCloseIcon = true,
        )
        KnowledgeEditFieldCard(
            label = stringResource(R.string.character_personality),
            value = state.personality,
            onValueChange = { onIntent(CharacterDetailIntent.SetPersonality(it)) },
            multiline = true,
        )
        KnowledgeEditFieldCard(
            label = stringResource(R.string.character_summary),
            value = state.summary,
            onValueChange = { onIntent(CharacterDetailIntent.SetSummary(it)) },
            multiline = true,
        )
        CharacterReadonlySection(
            title = stringResource(R.string.character_events),
            emptyText = stringResource(R.string.character_events_empty),
            isEmpty = state.events.isEmpty(),
        ) {
            state.events.forEach { event ->
                InfoCard(
                    title = event.title,
                    body = event.content,
                )
            }
        }
        CharacterReadonlySection(
            title = stringResource(R.string.character_relations),
            emptyText = stringResource(R.string.character_relations_empty),
            isEmpty = state.relations.isEmpty(),
        ) {
            state.relations.forEach { relation ->
                InfoCard(
                    title = "${relation.fromName} - ${relation.toName}".trim(' ', '-'),
                    body = listOf(relation.relationType, relation.attitude, relation.summary)
                        .filter { it.isNotBlank() }
                        .joinToString("\n"),
                )
            }
        }
    }

    AppAlertDialog(
        show = showProfileDialog,
        onDismissRequest = { showProfileDialog = false },
        title = stringResource(R.string.character_detail),
        confirmText = stringResource(R.string.apply),
        onConfirm = {
            onIntent(CharacterDetailIntent.SetName(dialogName))
            onIntent(CharacterDetailIntent.SetAliasesText(dialogAliases))
            onIntent(CharacterDetailIntent.SetRole(dialogRole))
            showProfileDialog = false
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { showProfileDialog = false },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(
                    value = dialogName,
                    onValueChange = { dialogName = it },
                    label = stringResource(R.string.character_name),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                AppTextField(
                    value = dialogAliases,
                    onValueChange = { dialogAliases = it },
                    label = stringResource(R.string.character_aliases),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                ProfileRoleDropdown(
                    selectedRole = dialogRole,
                    onRoleSelected = { dialogRole = it },
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileRoleDropdown(
    selectedRole: String,
    onRoleSelected: (String) -> Unit,
) {
    var showDropdown by remember { mutableStateOf(false) }
    val roleDisplayNames = mapOf(
        BookCharacterProfile.ROLE_MALE_LEAD to stringResource(R.string.role_male_lead),
        BookCharacterProfile.ROLE_FEMALE_LEAD to stringResource(R.string.role_female_lead),
        BookCharacterProfile.ROLE_MALE_SUPPORTING to stringResource(R.string.role_male_supporting),
        BookCharacterProfile.ROLE_FEMALE_SUPPORTING to stringResource(R.string.role_female_supporting),
    )
    val currentDisplay = roleDisplayNames[selectedRole] ?: "—"

    AppText(
        text = stringResource(R.string.character_role),
        style = LegadoTheme.typography.labelMedium,
        color = LegadoTheme.colorScheme.onSurfaceVariant,
    )
    Box {
        GlassCard(
            onClick = { showDropdown = true },
            containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
        ) {
            AnimatedTextLine(
                text = currentDisplay,
                style = LegadoTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
            )
        }
        RoundDropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { showDropdown = false },
        ) {
            BookCharacterProfile.ALL_ROLES.forEach { role ->
                RoundDropdownMenuItem(
                    text = roleDisplayNames[role] ?: role,
                    onClick = {
                        onRoleSelected(if (selectedRole == role) "" else role)
                        showDropdown = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CharacterHeader(
    name: String,
    aliasesText: String,
    avatarUri: String,
    roleDisplayName: String,
    tags: ImmutableList<String>,
    onPickAvatar: () -> Unit,
    onInfoClick: () -> Unit,
) {
    val avatarLoadFailed = remember(avatarUri) { mutableStateOf(false) }
    val resolvedRole = when (roleDisplayName) {
        BookCharacterProfile.ROLE_MALE_LEAD -> stringResource(R.string.role_male_lead)
        BookCharacterProfile.ROLE_FEMALE_LEAD -> stringResource(R.string.role_female_lead)
        BookCharacterProfile.ROLE_MALE_SUPPORTING -> stringResource(R.string.role_male_supporting)
        BookCharacterProfile.ROLE_FEMALE_SUPPORTING -> stringResource(R.string.role_female_supporting)
        else -> ""
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassCard(
            onClick = onPickAvatar,
            cornerRadius = 64.dp,
            containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarUri.isNotBlank() && !avatarLoadFailed.value) {
                    AsyncImage(
                        model = avatarUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onError = { avatarLoadFailed.value = true },
                    )
                } else {
                    AppIcon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onInfoClick)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnimatedTextLine(
                    text = name.ifBlank { "—" },
                    style = LegadoTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (resolvedRole.isNotBlank()) {
                    TextCard(text = resolvedRole)
                }
            }
            if (aliasesText.isNotBlank()) {
                AnimatedTextLine(
                    text = aliasesText,
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            } else {
                AnimatedTextLine(
                    text = stringResource(R.string.character_no_aliases),
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun CharacterReadonlySection(
    title: String,
    emptyText: String,
    isEmpty: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppText(
            text = title,
            style = LegadoTheme.typography.titleMedium,
        )
        if (isEmpty) {
            GlassCard {
                Box(
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyMessage(
                        message = emptyText
                    )
                }
            }
        } else {
            content()
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    body: String,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AnimatedTextLine(
                text = title.ifBlank { stringResource(R.string.character_unknown) },
                style = LegadoTheme.typography.bodyMedium,
                overflow = TextOverflow.Ellipsis,
            )
            AnimatedTextLine(
                text = body.ifBlank { stringResource(R.string.character_no_summary) },
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                maxLines = 4,
            )
        }
    }
}
