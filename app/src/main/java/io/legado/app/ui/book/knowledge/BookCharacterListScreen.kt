package io.legado.app.ui.book.knowledge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.ui.ai.AiTaskResultSheet
import io.legado.app.ui.ai.chat.ReasoningCard
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.checkBox.CheckboxItem
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.text.AnimatedTextLine
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookCharacterListScreen(
    state: CharacterListUiState,
    onIntent: (CharacterListIntent) -> Unit,
    effects: Flow<CharacterListEffect>,
    onBack: () -> Unit,
    onOpenDetail: (characterId: String?) -> Unit,
    onRefresh: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val roleFilterLabels = remember {
        listOf(
            "" to R.string.knowledge_type_all,
            BookCharacterProfile.ROLE_MALE_LEAD to R.string.role_male_lead,
            BookCharacterProfile.ROLE_FEMALE_LEAD to R.string.role_female_lead,
            BookCharacterProfile.ROLE_MALE_SUPPORTING to R.string.role_male_supporting,
            BookCharacterProfile.ROLE_FEMALE_SUPPORTING to R.string.role_female_supporting,
        )
    }
    val tabTitles = roleFilterLabels.map { stringResource(it.second) }
    val selectedTabIndex =
        roleFilterLabels.indexOfFirst { it.first == state.roleFilter }.coerceAtLeast(0)

    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                is CharacterListEffect.OpenCharacterDetail -> onOpenDetail(effect.characterId)
                is CharacterListEffect.ShowToast -> context.toastOnUi(effect.message)
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
                title = stringResource(R.string.book_characters),
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBack)
                },
                actions = {
                    TopBarActionButton(
                        onClick = { onIntent(CharacterListIntent.OpenAiIdentify) },
                        imageVector = Icons.Default.Person,
                        contentDescription = stringResource(R.string.ai_identify_characters),
                    )
                },
                scrollBehavior = scrollBehavior,
                bottomContent = {
                    AppTabRow(
                        tabTitles = tabTitles,
                        selectedTabIndex = selectedTabIndex,
                        onTabSelected = { index ->
                            onIntent(CharacterListIntent.SetRoleFilter(roleFilterLabels[index].first))
                        }
                    )
                }
            )
        },
        floatingActionButton = {
            AppFloatingActionButton(
                onClick = { onIntent(CharacterListIntent.AddCharacter) },
                icon = Icons.Default.Add,
                tooltipText = stringResource(R.string.add_character),
            )
        },
    ) { paddingValues ->
        if (state.isLoading && state.characters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                AppCircularProgressIndicator()
            }
        } else {
            CharacterListContent(
                state = state,
                onIntent = onIntent,
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                paddingValues = paddingValues
            )
        }
    }
    CharacterIdentifySheet(state.aiSheet, state.isAiSheetVisible, onIntent)
}

@Composable
private fun CharacterIdentifySheet(
    sheet: CharacterIdentifySheet?,
    visible: Boolean,
    onIntent: (CharacterListIntent) -> Unit,
) {
    AiTaskResultSheet(
        show = visible && sheet != null,
        onDismissRequest = { onIntent(CharacterListIntent.DismissAiIdentify) },
        title = stringResource(R.string.ai_identify_characters),
        startAction = if (sheet != null) {
            {
                MediumTonalButton(
                    onClick = { onIntent(CharacterListIntent.RunAiIdentify) },
                    icon = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.retry),
                    enabled = !sheet.loading,
                )
            }
        } else null,
        endAction = if (sheet?.candidates?.isNotEmpty() == true) {
            {
                MediumTonalButton(
                    onClick = { onIntent(CharacterListIntent.SaveAiCandidates) },
                    contentDescription = stringResource(R.string.ai_identify_characters_save),
                    icon = Icons.Default.Save,
                    enabled = !sheet.loading,
                )
            }
        } else null,
    ) {
        when {
            sheet == null -> Unit
            sheet.loading -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) { AppCircularProgressIndicator() }
                sheet.toolNames.forEach { toolName ->
                    TextCard(text = toolName)
                }
                ReasoningCard(
                    text = sheet.reasoning,
                    isStreaming = true,
                    messageCreatedAt = sheet.startedAt,
                )
            }

            sheet.error != null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnimatedTextLine(sheet.error, color = LegadoTheme.colorScheme.error)
            }

            sheet.candidates.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnimatedTextLine(
                    stringResource(R.string.ai_identify_characters_hint),
                    color = LegadoTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sheet.candidates.forEach { candidate ->
                    CharacterIdentifyCandidateRow(candidate, onIntent)
                }
            }
        }
    }
}

@Composable
private fun CharacterIdentifyCandidateRow(
    candidate: CharacterIdentifyCandidateUi,
    onIntent: (CharacterListIntent) -> Unit,
) {
    val expanded = rememberSaveable(candidate.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CheckboxItem(
            title = candidate.name,
            checked = candidate.selected,
            onCheckedChange = { onIntent(CharacterListIntent.ToggleAiCandidate(candidate.id)) },
        )
        if (candidate.summary.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded.value = !expanded.value }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedTextLine(
                    text = candidate.summary.lineSequence().firstOrNull().orEmpty(),
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                AppIcon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = expanded.value,
                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
            ) {
                AnimatedTextLine(
                    candidate.summary,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CharacterListContent(
    state: CharacterListUiState,
    onIntent: (CharacterListIntent) -> Unit,
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = adaptiveContentPadding(
            top = paddingValues.calculateTopPadding(),
            bottom = 120.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.characters.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedTextLine(
                        text = stringResource(R.string.character_empty_hint),
                        style = LegadoTheme.typography.bodyMedium,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(
            items = state.characters,
            key = { it.id },
        ) { character ->
            CharacterListItem(
                character = character,
                onClick = { onIntent(CharacterListIntent.OpenCharacter(character.id)) },
            )
        }
    }
}

@Composable
private fun CharacterListItem(
    character: CharacterListItemUi,
    onClick: () -> Unit,
) {
    val avatarLoadFailed = remember(character.avatarUri) { mutableStateOf(false) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(LegadoTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (!character.avatarUri.isNullOrBlank() && !avatarLoadFailed.value) {
                    AsyncImage(
                        model = character.avatarUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        onError = { avatarLoadFailed.value = true },
                    )
                } else {
                    AppIcon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AnimatedTextLine(
                        text = character.name,
                        style = LegadoTheme.typography.titleSmall,
                    )
                    if (character.role.isNotBlank()) {
                        val roleLabel = when (character.role) {
                            BookCharacterProfile.ROLE_MALE_LEAD -> stringResource(R.string.role_male_lead)
                            BookCharacterProfile.ROLE_FEMALE_LEAD -> stringResource(R.string.role_female_lead)
                            BookCharacterProfile.ROLE_MALE_SUPPORTING -> stringResource(R.string.role_male_supporting)
                            BookCharacterProfile.ROLE_FEMALE_SUPPORTING -> stringResource(R.string.role_female_supporting)
                            else -> character.role
                        }
                        TextCard(text = roleLabel)
                    }
                }
                if (character.summary.isNotBlank()) {
                    AnimatedTextLine(
                        text = character.summary,
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
