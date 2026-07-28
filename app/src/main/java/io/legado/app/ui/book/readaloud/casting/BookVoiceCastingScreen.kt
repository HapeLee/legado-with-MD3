package io.legado.app.ui.book.readaloud.casting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinySettingItem
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
fun BookVoiceCastingScreen(
    state: BookVoiceCastingUiState,
    onIntent: (BookVoiceCastingIntent) -> Unit,
    effects: Flow<BookVoiceCastingEffect>,
    onBack: () -> Unit,
    onManageCloudTts: () -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                is BookVoiceCastingEffect.ShowToast -> context.toastOnUi(effect.message)
            }
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.book_voice_casting),
                navigationIcon = { TopBarNavigationButton(onClick = onBack) },
                actions = {
                    TopBarActionButton(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = stringResource(R.string.read_aloud_engines_and_voices),
                        onClick = onManageCloudTts,
                    )
                    TopBarActionButton(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        onClick = { onIntent(BookVoiceCastingIntent.Refresh) },
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        when {
            state.isLoading && state.items.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            else -> VoiceCastingList(
                state = state,
                onIntent = onIntent,
                contentPadding = paddingValues,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    VoicePickerSheet(
        picker = state.picker,
        voices = state.voices,
        onIntent = onIntent,
    )
}

@Composable
private fun VoiceCastingList(
    state: BookVoiceCastingUiState,
    onIntent: (BookVoiceCastingIntent) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val specialItems = state.items.filter { it.kind != CastingSubjectKind.Character }
    val characters = state.items.filter { it.kind == CastingSubjectKind.Character }
    LazyColumn(
        modifier = modifier,
        contentPadding = adaptiveContentPadding(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(contentType = "intro") {
            AppText(
                text = stringResource(R.string.book_voice_casting_summary),
                style = LegadoTheme.typography.bodyMedium,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item(contentType = "section") {
            AppText(
                text = stringResource(R.string.voice_fallback_roles),
                style = LegadoTheme.typography.titleSmall,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        items(
            items = specialItems,
            key = { "${it.subjectType}:${it.subjectId}" },
            contentType = { "casting" },
        ) { item ->
            VoiceCastingCard(item = item, onIntent = onIntent)
        }
        item(contentType = "section") {
            AppText(
                text = stringResource(R.string.book_characters),
                style = LegadoTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
        }
        if (characters.isEmpty()) {
            item(contentType = "empty") {
                AppText(
                    text = stringResource(R.string.character_empty_hint),
                    style = LegadoTheme.typography.bodyMedium,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            items(
                items = characters,
                key = { "${it.subjectType}:${it.subjectId}" },
                contentType = { "casting" },
            ) { item ->
                VoiceCastingCard(item = item, onIntent = onIntent)
            }
        }
    }
}

@Composable
private fun VoiceCastingCard(
    item: VoiceCastingItemUi,
    onIntent: (BookVoiceCastingIntent) -> Unit,
) {
    val title = subjectTitle(item.kind, item.name)
    val voiceText = when {
        !item.hasBinding -> stringResource(R.string.voice_not_assigned)
        item.voiceAvailable -> item.voiceName
        item.voiceName.isNotBlank() -> stringResource(R.string.voice_unavailable_named, item.voiceName)
        else -> stringResource(R.string.voice_unavailable)
    }
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            onIntent(
                BookVoiceCastingIntent.OpenVoicePicker(
                    subjectType = item.subjectType,
                    subjectId = item.subjectId,
                )
            )
        },
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(LegadoTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(
                        imageVector = when (item.kind) {
                            CastingSubjectKind.Narrator -> Icons.AutoMirrored.Filled.MenuBook
                            CastingSubjectKind.Character -> Icons.Default.Person
                            else -> Icons.Default.RecordVoiceOver
                        },
                        contentDescription = null,
                    )
                }
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (item.description.isNotBlank()) {
                        AnimatedTextLine(
                            text = item.description,
                            style = LegadoTheme.typography.bodySmall,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AnimatedTextLine(
                        text = voiceText,
                        style = LegadoTheme.typography.labelMedium,
                        color = if (item.hasBinding && !item.voiceAvailable) {
                            LegadoTheme.colorScheme.error
                        } else {
                            LegadoTheme.colorScheme.primary
                        },
                    )
                }
            },
            trailingContent = if (item.hasBinding && !item.voiceAvailable) {
                {
                    AppIcon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = stringResource(R.string.voice_unavailable),
                        tint = LegadoTheme.colorScheme.error,
                    )
                }
            } else null,
        ) {
            AnimatedTextLine(text = title)
        }
    }
}

@Composable
private fun VoicePickerSheet(
    picker: VoicePickerUi?,
    voices: ImmutableList<VoiceOptionUi>,
    onIntent: (BookVoiceCastingIntent) -> Unit,
) {
    AppModalBottomSheet(
        show = picker != null,
        onDismissRequest = { onIntent(BookVoiceCastingIntent.DismissVoicePicker) },
        title = picker?.let { subjectTitle(it.kind, it.name) }.orEmpty(),
    ) {
        if (picker == null) return@AppModalBottomSheet
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (voices.none { it.selectable }) {
                AppText(
                    text = stringResource(R.string.no_available_voices),
                    style = LegadoTheme.typography.bodyMedium,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(
                        items = voices,
                        key = VoiceOptionUi::id,
                    ) { voice ->
                        TinySettingItem(
                            title = voice.name,
                            description = voiceDescription(voice),
                            enabled = voice.selectable,
                            trailingContent = if (picker.selectedVoiceId == voice.id) {
                                {
                                    AppIcon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = LegadoTheme.colorScheme.primary,
                                    )
                                }
                            } else null,
                            onClick = { onIntent(BookVoiceCastingIntent.AssignVoice(voice.id)) },
                        )
                    }
                }
            }
            if (picker.selectedVoiceId != null) {
                TinySettingItem(
                    title = stringResource(R.string.clear_voice_binding),
                    color = LegadoTheme.colorScheme.errorContainer,
                    onClick = { onIntent(BookVoiceCastingIntent.ClearBinding) },
                )
            }
        }
    }
}

@Composable
private fun subjectTitle(kind: CastingSubjectKind, name: String): String = when (kind) {
    CastingSubjectKind.Narrator -> stringResource(R.string.voice_role_narrator)
    CastingSubjectKind.UnknownMale -> stringResource(R.string.voice_role_unknown_male)
    CastingSubjectKind.UnknownFemale -> stringResource(R.string.voice_role_unknown_female)
    CastingSubjectKind.Unknown -> stringResource(R.string.voice_role_unknown)
    CastingSubjectKind.Character -> name
}

@Composable
private fun voiceDescription(voice: VoiceOptionUi): String {
    val engineType = when (voice.engineType) {
        ReadAloudVoice.ENGINE_SYSTEM -> stringResource(R.string.system_tts)
        ReadAloudVoice.ENGINE_HTTP -> stringResource(R.string.http_tts)
        else -> voice.engineType
    }
    return if (voice.selectable) {
        listOf(engineType, voice.engineName).filter(String::isNotBlank).joinToString(" · ")
    } else {
        stringResource(R.string.voice_unavailable_named, engineType)
    }
}
