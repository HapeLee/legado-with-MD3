package io.legado.app.ui.book.read.sheet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.feature.reader.core.cast.CastMarkers
import io.legado.app.help.readaloud.cast.CastAssignmentStore
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadMenuConfig
import io.legado.app.ui.theme.LegadoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.legado.app.ui.widget.components.CastFieldSpec
import io.legado.app.ui.widget.components.CastFieldStack
import io.legado.app.ui.widget.components.CastImeScope
import io.legado.app.ui.widget.components.CastOption
import io.legado.app.ui.widget.components.VoiceAuditionButton
import io.legado.app.ui.widget.components.castCardMaxHeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


/**
 * 多角色分配悬浮窗：阅读页主窗口内的居中卡片（不用 Dialog——独立窗口会让
 * HyperOS 在输入框间转移焦点时反复 hide/show 键盘）。
 *
 * 键盘行为对齐 MultiTTS（用户半透明键盘，IME 永远在最上层）：
 * - `safeDrawingPadding` 让整张卡片留在状态栏以下、并随键盘弹出整体上移；
 * - 候选列表**内嵌在卡片里**（输入框正下方展开），不用 Popup 窗口，
 *   彻底消除浮层窗口与键盘的层级/焦点互扰；
 * - 输入即自动展开筛选列表（MultiTTS 逻辑）；三角只切换「全部 ↔ 收起」；
 * - 点列表项只回写值，不清焦点 → 键盘保持。
 *
 * 按钮：取消分配 / 取消 / 创建（新角色）/ 确认（分配+更新已有角色，不创建）。
 */
@Composable
fun ReadAloudCastSheet(
    show: Boolean,
    ordinal: Int,
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
    menuConfig: ReadMenuConfig? = null,
) {
    // 关掉后宿主立刻把 ordinal 清成 -1：先把它记住，再让整棵树多活 180ms，
    // 否则退场动画一帧都播不出来（官方那侧的 ChangeChapterSourceSheet 也是这个套路）。
    val opened = show && ordinal >= 0
    val ordinalAt = rememberSheetArg(opened, ordinal)
    if (!rememberSheetAlive(opened) || ordinalAt < 0) return
    val book = ReadBook.book ?: return
    val bookUrl = book.bookUrl
    val chapterIndex = ReadBook.durChapterIndex
    val loadKey = "$bookUrl#$chapterIndex#$ordinalAt"
    val sheetData by produceState<CastAssignmentStore.SheetData?>(initialValue = null, loadKey) {
        value = withContext(Dispatchers.IO) {
            CastAssignmentStore.sheetData(bookUrl, chapterIndex, ordinalAt)
        }
    }
    val data = sheetData ?: return
    // 试听念的就是这一句正文（锚点计数与胶囊同源）
    val quoteText by produceState<String?>(initialValue = null, loadKey) {
        value = withContext(Dispatchers.IO) {
            CastAssignmentStore.quoteText(book, chapterIndex, ordinalAt)
        }
    }

    var selectedCharacterId by remember(loadKey) { mutableStateOf(data.initialCharacterId) }
    var name by remember(loadKey) { mutableStateOf(data.initialName) }
    var pool by remember(loadKey) { mutableStateOf(data.initialPool) }
    var voiceId by remember(loadKey) { mutableStateOf(data.initialVoiceId) }
    var voiceEffect by remember(loadKey) { mutableStateOf(data.initialVoiceEffect) }
    var voiceQuery by remember(loadKey) {
        mutableStateOf(
            data.voices.firstOrNull { it.id == data.initialVoiceId }?.displayName
                ?: data.initialVoiceId
        )
    }
    // 单开约束：任意时刻最多一个列表展开
    var expandedRow by remember(loadKey) { mutableStateOf<String?>(null) }

    BackHandler(enabled = show) {
        if (expandedRow != null) expandedRow = null else onDismissRequest()
    }

    // 音色按已选声音池筛选：选了池只看该池成员（且池内启用），没选池显示全部
    val voiceOptions = remember(data.voices, pool) {
        data.voices
            .filter { pool.isBlank() || it.poolNames.contains(pool.trim()) }
            .map { CastOption(it.id, it.displayName) }
    }

    // 窗口内置空 Compose 键盘控制器：切换输入框不再先收再弹
    val scrimAlpha = rememberSheetScrimAlpha(opened)
    CastImeScope {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 遮罩跟着卡片一起淡入淡出：只让卡片动、黑底硬蹦的话，看起来还是「突然一响」
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f * scrimAlpha))
                // 键盘弹出时整个 overlay 可用区缩小 → 居中的卡片自然上移
                .safeDrawingPadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (expandedRow != null) expandedRow = null else onDismissRequest()
                },
            contentAlignment = Alignment.Center,
        ) {
            CastSheetCard(
                menuConfig = menuConfig,
                visible = opened,
                modifier = Modifier
                    .fillMaxWidth()
                    // 只吞掉点击、不抢焦点：用 clickable 会让正在输入的框失焦→键盘先收再弹
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                    .padding(horizontal = 24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 卡片不超过可用高度（键盘弹起时可用高度更小），超出部分内部滚动
                        .heightIn(max = castCardMaxHeight(0.72f))
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismissRequest) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                        Text(
                            text = stringResource(R.string.cast_sheet_title),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 48.dp),
                            style = LegadoTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }

                    CastFieldStack(
                        specs = listOf(
                            CastFieldSpec(
                                id = "name",
                                label = stringResource(R.string.cast_character_name),
                                value = name,
                                options = data.characters.map {
                                    CastOption(it.id, CastMarkers.labelOf(it.name, it.poolLabel))
                                },
                                expanded = expandedRow == "name",
                                onValueChange = {
                                    // 手动改名后不再指向「下拉选中的那个角色」，确认时按名字/池重新解析
                                    name = it
                                    selectedCharacterId = ""
                                },
                                onSelected = { option ->
                                    data.characters.firstOrNull { it.id == option.key }?.let { c ->
                                        selectedCharacterId = c.id
                                        name = c.name
                                        pool = c.poolLabel
                                        voiceId = c.voiceId
                                        // 角色的全局变声器不抄进这一句：段级栏保持段级，空着就是跟随角色
                                        voiceQuery = data.voices
                                            .firstOrNull { it.id == c.voiceId }?.displayName ?: c.voiceId
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
                                    // 换池后当前音色若不在新池里，清掉音色选择避免假匹配
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
                                // 音色行输入只用于筛选列表，不直接改写已选音色
                                onValueChange = { voiceQuery = it },
                                onSelected = {
                                    voiceId = it.key
                                    voiceQuery = it.label
                                },
                                onExpand = { open -> expandedRow = if (open) "voice" else null },
                            ),
                            CastFieldSpec(
                                id = "effect",
                                label = stringResource(R.string.cast_voice_effect_sentence),
                                value = voiceEffect,
                                options = data.effects.map { CastOption(it, it) },
                                expanded = expandedRow == "effect",
                                // 只改这一句（chapter_role_assignments.voiceEffect）；
                                // 角色的全局变声器在「人物与角色配音」页编辑，这里清空就是跟随角色。
                                onValueChange = { voiceEffect = it },
                                onSelected = { voiceEffect = it.key },
                                onExpand = { open -> expandedRow = if (open) "effect" else null },
                            ),
                        ),
                    )

                    // 试听：念正文这一句，用的就是当前选中的音色
                    VoiceAuditionButton(
                        voiceId = voiceId,
                        text = quoteText.orEmpty(),
                        // 段级留空时听的是角色的全局那份，与朗读侧的取值规则一致
                        effect = voiceEffect.ifBlank {
                            data.characters.firstOrNull { it.id == selectedCharacterId }
                                ?.voiceEffect.orEmpty()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (data.assigned) {
                            TextButton(onClick = {
                                onIntent(ReadBookIntent.UnassignRoleCast(ordinalAt))
                            }) {
                                Text(stringResource(R.string.cast_unassign))
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        TextButton(onClick = onDismissRequest) {
                            Text(stringResource(R.string.cancel))
                        }
                        Spacer(Modifier.width(4.dp))
                        TextButton(
                            enabled = CastMarkers.isValidName(name),
                            onClick = {
                                onIntent(
                                    ReadBookIntent.CreateRoleCast(
                                        ordinal = ordinalAt,
                                        characterName = name,
                                        voicePoolLabel = pool,
                                        voiceId = voiceId,
                                        voiceEffect = voiceEffect,
                                    ),
                                )
                            },
                        ) {
                            Text(stringResource(R.string.cast_create))
                        }
                        Spacer(Modifier.width(4.dp))
                        TextButton(
                            enabled = CastMarkers.isValidName(name),
                            onClick = {
                                onIntent(
                                    ReadBookIntent.ConfirmRoleCast(
                                        ordinal = ordinalAt,
                                        selectedCharacterId = selectedCharacterId,
                                        characterName = name,
                                        voicePoolLabel = pool,
                                        voiceId = voiceId,
                                        voiceEffect = voiceEffect,
                                    ),
                                )
                            },
                        ) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

