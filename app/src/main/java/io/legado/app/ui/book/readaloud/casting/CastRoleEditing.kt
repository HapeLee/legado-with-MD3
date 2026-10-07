package io.legado.app.ui.book.readaloud.casting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.feature.reader.core.cast.CastMarkers
import io.legado.app.help.readaloud.cast.BookCastStore
import io.legado.app.help.readaloud.cast.VoiceAudition
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.CastFieldSpec
import io.legado.app.ui.widget.components.CastFieldStack
import io.legado.app.ui.widget.components.CastOption
import io.legado.app.ui.widget.components.VoiceAuditionButton
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.PrimaryButton
import io.legado.app.ui.widget.components.button.SecondaryButton
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menuItem.MenuItemIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 官方人物卡片行右侧的动作菜单：展开行内编辑、设这个角色自己的气泡、换头像、删除这个角色。
 *
 * 卡片本身点下去是官方的人物详情页。这里只补官方没有的几件事——改名字/声音池、
 * 不设进详情页就能换头像，以及把角色连同它的分配句一起删掉，样式沿用官方书源列表
 * 那套「更多」溢出菜单（`BookSourceItemMenu`），不在卡片上摆两个裸图标。
 */
@Composable
fun CastRoleRowActions(
    editing: Boolean,
    onToggleEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetAvatar: () -> Unit = {},
    onSetBubble: () -> Unit = {},
    hasBubble: Boolean = false,
    onClearBubble: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        SmallPlainButton(
            icon = AppIcons.MoreVert,
            contentDescription = stringResource(R.string.menu),
            selected = editing,
            onClick = { expanded = true },
        )
        RoundDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) { dismiss ->
            RoundDropdownMenuItem(
                text = stringResource(R.string.edit),
                leadingIcon = {
                    MenuItemIcon(
                        imageVector = AppIcons.Edit,
                        contentDescription = null,
                    )
                },
                onClick = {
                    dismiss()
                    onToggleEdit()
                },
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.character_avatar_source),
                leadingIcon = {
                    MenuItemIcon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                    )
                },
                onClick = {
                    dismiss()
                    onSetAvatar()
                },
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.cast_bubble_menu),
                leadingIcon = {
                    MenuItemIcon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                    )
                },
                onClick = {
                    dismiss()
                    onSetBubble()
                },
            )
            // 弹层里已经没有「启用」那一栏了，撤掉气泡只能在菜单里做一次
            if (hasBubble) {
                RoundDropdownMenuItem(
                    text = stringResource(R.string.cast_bubble_clear),
                    leadingIcon = {
                        MenuItemIcon(
                            imageVector = AppIcons.Close,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        dismiss()
                        onClearBubble()
                    },
                )
            }
            RoundDropdownMenuItem(
                text = stringResource(R.string.cast_delete_character),
                color = LegadoTheme.colorScheme.error,
                leadingIcon = {
                    MenuItemIcon(
                        imageVector = AppIcons.Delete,
                        contentDescription = null,
                        tint = LegadoTheme.colorScheme.error,
                    )
                },
                onClick = {
                    dismiss()
                    onDelete()
                },
            )
        }
    }
}

/**
 * 角色行内编辑：名字 / 声音池 / 音色 / 变声器四行下拉 + 试听，与分配角色弹层同款组件。
 *
 * 保存走 [BookCastStore.updateCharacter]：它会把名字与池同步进官方人物档案、
 * 改写所有分配句，并把选中的音色锁进 book_voice_bindings（发音链路读它）。
 * 变声器那一栏存的是**角色级**的 cast_characters.voiceEffect（全局，这个角色的所有句子都用它）。
 */
@Composable
fun CastRoleEditorPanel(
    bookUrl: String,
    item: VoiceCastingItemUi,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bundle by produceState<BookCastStore.EditBundle?>(initialValue = null, item.subjectId) {
        value = withContext(Dispatchers.IO) { BookCastStore.editBundle(bookUrl) }
    }
    val data = bundle ?: return
    var name by remember(item.subjectId) { mutableStateOf(item.name) }
    var pool by remember(item.subjectId) { mutableStateOf(item.poolLabel) }
    var voiceId by remember(item.subjectId) { mutableStateOf(item.voiceId) }
    var voiceEffect by remember(item.subjectId) { mutableStateOf(item.voiceEffect) }
    var voiceQuery by remember(item.subjectId) {
        mutableStateOf(data.voices.firstOrNull { it.id == item.voiceId }?.displayName.orEmpty())
    }
    var expandedRow by remember(item.subjectId) { mutableStateOf<String?>(null) }
    val voiceOptions = remember(data.voices, pool) {
        data.voices
            .filter { pool.isBlank() || it.poolNames.contains(pool.trim()) }
            .map { CastOption(it.id, it.displayName) }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CastFieldStack(
            specs = listOf(
                CastFieldSpec(
                    id = "name",
                    label = stringResource(R.string.cast_character_name),
                    value = name,
                    options = data.characters
                        .filter { it.id != item.subjectId }
                        .map { CastOption(it.id, CastMarkers.labelOf(it.name, it.poolLabel)) },
                    expanded = expandedRow == "name",
                    onValueChange = { name = it },
                    onSelected = { option ->
                        data.characters.firstOrNull { it.id == option.key }?.let { c ->
                            name = c.name
                            pool = c.poolLabel
                            voiceId = c.voiceId
                            voiceEffect = c.voiceEffect
                            voiceQuery = data.voices
                                .firstOrNull { it.id == c.voiceId }?.displayName.orEmpty()
                        }
                    },
                    onExpand = { open -> expandedRow = if (open) "name" else null },
                ),
                CastFieldSpec(
                    id = "pool",
                    label = stringResource(R.string.cast_voice_pool),
                    value = pool,
                    options = data.pools.map { CastOption(it, it) },
                    expanded = expandedRow == "pool",
                    onValueChange = { pool = it },
                    onSelected = {
                        pool = it.key
                        if (voiceId.isNotBlank() &&
                            data.voices.firstOrNull { it.id == voiceId }
                                ?.poolNames?.contains(it.key) != true
                        ) {
                            voiceId = ""
                            voiceQuery = ""
                        }
                    },
                    onExpand = { open -> expandedRow = if (open) "pool" else null },
                ),
                CastFieldSpec(
                    id = "voice",
                    label = stringResource(R.string.cast_voice),
                    value = voiceQuery,
                    options = voiceOptions,
                    expanded = expandedRow == "voice",
                    onValueChange = { voiceQuery = it },
                    onSelected = {
                        voiceId = it.key
                        voiceQuery = it.label
                    },
                    onExpand = { open -> expandedRow = if (open) "voice" else null },
                ),
                CastFieldSpec(
                    id = "effect",
                    label = stringResource(R.string.cast_voice_effect_global),
                    value = voiceEffect,
                    options = data.effects.map { CastOption(it, it) },
                    expanded = expandedRow == "effect",
                    // 角色的**全局**变声器：这一份存在 cast_characters.voiceEffect，
                    // 朗读侧每个角色句都按 characterId 读它，所以改一次全书生效。
                    // 空 = 不变声，除非正文胶囊给那一句单独指定了预设（段级优先）。
                    onValueChange = { voiceEffect = it },
                    onSelected = { voiceEffect = it.key },
                    onExpand = { open -> expandedRow = if (open) "effect" else null },
                ),
            ),
        )
        // 试听：用角色声音池那里可自定义的默认试听文本
        VoiceAuditionButton(
            voiceId = voiceId,
            text = remember(context) { VoiceAudition.defaultPreviewText(context) },
            effect = voiceEffect,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            SecondaryButton(onClick = onCancel, text = stringResource(R.string.cancel))
            PrimaryButton(
                enabled = CastMarkers.isValidName(name),
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        val ok = BookCastStore.updateCharacter(
                            bookUrl = bookUrl,
                            characterId = item.subjectId,
                            name = name,
                            poolLabel = pool,
                            voiceId = voiceId,
                            voiceEffect = voiceEffect,
                        )
                        if (ok) {
                            // 正文重排在 updateCharacter 里按「改的是不是正文看得见的那几栏」决定
                            withContext(Dispatchers.Main) { onSaved() }
                        }
                    }
                },
                text = stringResource(R.string.save),
            )
        }
    }
}

/**
 * 旁白行内编辑：只有一行「音色」。
 *
 * 旁白不是角色——没有名字、不进声音池、也没有角色级变声器那一列可写
 * （那些都存在 cast_characters 上，旁白在表里没有行）。所以这里只摆真正生效的
 * 那一件事：book_voice_bindings 里 (narrator, narrator) 的音色绑定。
 * 选「跟随朗读引擎」等于删掉这条绑定，朗读侧回落到引擎自己的默认发音人。
 */
@Composable
fun CastNarratorEditorPanel(
    item: VoiceCastingItemUi,
    voices: List<VoiceOptionUi>,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var voiceId by remember(item.subjectId) { mutableStateOf(item.voiceId) }
    var voiceQuery by remember(item.subjectId) { mutableStateOf(item.voiceName) }
    var expandedRow by remember(item.subjectId) { mutableStateOf<String?>(null) }
    val defaultLabel = stringResource(R.string.cast_narrator_voice_default)
    val options = remember(voices, defaultLabel) {
        listOf(CastOption("", defaultLabel)) +
            voices.filter { it.selectable }.map { voice ->
                CastOption(
                    voice.id,
                    listOf(voice.name, voice.engineName)
                        .filter(String::isNotBlank).joinToString(" · "),
                )
            }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CastFieldStack(
            specs = listOf(
                CastFieldSpec(
                    id = "voice",
                    label = stringResource(R.string.cast_voice),
                    value = voiceQuery,
                    options = options,
                    expanded = expandedRow == "voice",
                    onValueChange = { voiceQuery = it },
                    onSelected = {
                        voiceId = it.key
                        voiceQuery = it.label
                    },
                    onExpand = { open -> expandedRow = if (open) "voice" else null },
                ),
            ),
        )
        VoiceAuditionButton(
            voiceId = voiceId,
            text = remember(context) { VoiceAudition.defaultPreviewText(context) },
            effect = "",
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            SecondaryButton(onClick = onCancel, text = stringResource(R.string.cancel))
            PrimaryButton(onClick = { onSave(voiceId) }, text = stringResource(R.string.save))
        }
    }
}

/** 删除确认：连分配句、音色绑定、人物档案与本书记忆里的那一行一起清掉。 */
@Composable
fun CastRoleDeleteDialog(
    bookUrl: String,
    target: VoiceCastingItemUi?,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    AppAlertDialog(
        show = target != null,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.cast_delete_character),
        text = target?.let { stringResource(R.string.cast_delete_character_confirm, it.name) },
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onDismiss = onDismiss,
        onConfirm = {
            val doomed = target ?: return@AppAlertDialog
            onDismiss()
            scope.launch(Dispatchers.IO) {
                BookCastStore.deleteCharacter(bookUrl, doomed.subjectId, doomed.name)
                BookCastStore.reloadReaderChapter(bookUrl)
                withContext(Dispatchers.Main) { onDeleted() }
            }
        },
    )
}
