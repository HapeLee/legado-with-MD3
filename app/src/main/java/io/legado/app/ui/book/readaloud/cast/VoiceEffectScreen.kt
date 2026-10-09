package io.legado.app.ui.book.readaloud.cast

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.VoiceEffectPreset
import io.legado.app.help.readaloud.cast.VoiceAudition
import io.legado.app.help.readaloud.cast.VoicePoolStore
import io.legado.app.help.readaloud.effect.VoiceEffectAudio
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.CastFieldSpec
import io.legado.app.ui.widget.components.CastFieldStack
import io.legado.app.ui.widget.components.CastOption
import io.legado.app.ui.widget.components.TinySwitch
import io.legado.app.ui.widget.components.VoiceAuditionAction
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.castCardMaxHeight
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.modalBottomSheet.OptionCard
import io.legado.app.ui.widget.components.modalBottomSheet.OptionSheet
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

/**
 * 变声器管理页（朗读规则 → 变声器）。
 *
 * 四个内置预设（魔王/哥布林/机器人/心声混响）是免费方案：音高与语速走 Media3 自带的
 * Sonic 变调，混响与金属感走平台 AudioEffect，没有引入第三方引擎。删干净了可以一键恢复，
 * 也可以把别人分享的一组预设 JSON 导进来（同名覆盖）。
 *
 * 但**混响/金属感只在有我们持有的播放器时挂得上**：系统 TTS 直读路径（[ReadAloud.supportsSessionAudioEffect]
 * 为 false）没有会话号，那一层会被整个跳过，所以列表要把「当前引擎不生效」标出来。
 */
@Composable
fun VoiceEffectRouteScreen(
    onBackClick: () -> Unit,
    viewModel: VoiceEffectViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    VoiceEffectScreen(
        state = state,
        onIntent = viewModel::onIntent,
        effects = viewModel.effects,
        onBackClick = onBackClick,
    )
}

@Composable
fun VoiceEffectScreen(
    state: VoiceEffectUiState,
    onIntent: (VoiceEffectIntent) -> Unit,
    effects: Flow<VoiceEffectEffect>,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    // 试听用的池与音色记在页面级：它不属于任何一份预设，换着预设听同一把声音才比得出差别
    val audition = remember { EffectAuditionState() }
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            readEffectDocument(context, it) { onIntent(VoiceEffectIntent.ImportFrom(it)) }
        }
    }
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let { onIntent(VoiceEffectIntent.ExportTo(it)) }
    }
    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                is VoiceEffectEffect.ShowToast -> context.toastOnUi(effect.message)
                VoiceEffectEffect.OpenImporter -> importer.launch(arrayOf("application/json", "*/*"))
                is VoiceEffectEffect.SaveExporter -> exporter.launch(effect.fileName)
            }
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // Miuix 引擎分支不套 contentColor，隐式取色在深色下会发黑
        contentColor = LegadoTheme.colorScheme.onSurface,
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.voice_effect),
                scrollBehavior = scrollBehavior,
                navigationIcon = { TopBarNavigationButton(onClick = onBackClick) },
                // 顶栏已经用 TopBarActionsRow 包好了，这里只放按钮本身
                actions = {
                    TopBarActionButton(
                        onClick = { onIntent(VoiceEffectIntent.ShowIoSheet) },
                        imageVector = Icons.Default.ImportExport,
                        contentDescription = stringResource(R.string.cast_pool_io),
                    )
                    TopBarActionButton(
                        onClick = { onIntent(VoiceEffectIntent.ShowCreate) },
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.voice_effect_create),
                    )
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = adaptiveContentPadding(top = 0.dp, bottom = 120.dp),
        ) {
            item {
                AppText(
                    text = stringResource(R.string.voice_effect_summary),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.rows.any { it.sessionLayerDropped }) {
                item {
                    AppText(
                        text = stringResource(R.string.voice_effect_session_dropped_hint),
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.tertiary,
                    )
                }
            }
            items(state.rows.size, key = { state.rows[it].preset.name }) { index ->
                val row = state.rows[index]
                // tiny 系列：标题/副标题/开关的排布与朗读设置里那些开关项完全一致；
                // 行点击仍是「打开编辑」，开关与删除各自占尾部一格（小系列按钮）
                TinySettingItem(
                    title = row.preset.name,
                    description = row.summary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .animateItem(),
                    trailingContent = {
                        TinySwitch(
                            checked = row.preset.enabled,
                            onCheckedChange = {
                                onIntent(VoiceEffectIntent.Toggle(row.preset.name, it))
                            },
                        )
                        SmallPlainButton(
                            onClick = { onIntent(VoiceEffectIntent.ShowDelete(row.preset)) },
                            icon = AppIcons.Delete,
                            contentDescription = stringResource(R.string.delete),
                        )
                    },
                    onClick = { onIntent(VoiceEffectIntent.ShowEdit(row.preset)) },
                )
            }
        }
    }

    OptionSheet(
        show = state.showIoSheet,
        onDismissRequest = { onIntent(VoiceEffectIntent.DismissIoSheet) },
        title = stringResource(R.string.cast_pool_io),
    ) {
        OptionCard(
            icon = Icons.Default.CloudDownload,
            text = stringResource(R.string.voice_effect_import),
            onClick = {
                onIntent(VoiceEffectIntent.DismissIoSheet)
                onIntent(VoiceEffectIntent.OpenImporter)
            },
        )
        OptionCard(
            icon = Icons.Default.SaveAlt,
            text = stringResource(R.string.voice_effect_export),
            onClick = {
                onIntent(VoiceEffectIntent.DismissIoSheet)
                onIntent(VoiceEffectIntent.RequestExporter)
            },
        )
        OptionCard(
            icon = Icons.Default.Restore,
            text = stringResource(R.string.voice_effect_restore),
            onClick = {
                onIntent(VoiceEffectIntent.DismissIoSheet)
                onIntent(VoiceEffectIntent.RestoreBuiltins)
            },
        )
    }

    // 常驻组合 + show/data：miuix 的窗口靠 show 驱动退场动画，条件组合会直接丢动画
    VoiceEffectEditDialog(
        initial = state.editTarget,
        isNew = state.isNew,
        audition = audition,
        onSave = { onIntent(VoiceEffectIntent.Save(it)) },
        onDismiss = { onIntent(VoiceEffectIntent.DismissEdit) },
    )

    AppAlertDialog(
        show = state.deleteTarget != null,
        onDismissRequest = { onIntent(VoiceEffectIntent.DismissDelete) },
        title = stringResource(R.string.voice_effect_delete),
        text = stringResource(R.string.voice_effect_delete_tip),
        confirmText = stringResource(R.string.ok),
        onConfirm = { onIntent(VoiceEffectIntent.Delete(state.deleteTarget?.name.orEmpty())) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(VoiceEffectIntent.DismissDelete) },
    )
}

/**
 * 编辑一个预设：音高/语速是倍率，混响是平台预设号，金属感是中频带通开关。
 *
 * 音高与语速各自有上下限（见 [VoiceEffectAudio]），滑过头只会到边界，不会把声音推成噪音。
 *
 * 名字 / 声音池 / 音色三行走 [CastFieldStack]：同屏只有一个真输入框，切行不闪键盘，
 * 与配音角色编辑页同一套交互（候选列表带圆角容器与淡入淡出）。试听按钮直接吃
 * **还没保存的草稿**，改一下滑杆当场听得见。
 * 池与音色只服务试听，不写进预设——音色归角色所有，预设只描述「怎么变形」。
 */
@Composable
private fun VoiceEffectEditDialog(
    initial: VoiceEffectPreset?,
    isNew: Boolean,
    audition: EffectAuditionState,
    onSave: (VoiceEffectPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // 常驻组合（调用方只把 initial 置 null）：miuix 的窗口靠 show 驱动退场动画，
    // 条件组合会让整棵子树在关闭那刻被摘掉，动画与草稿一起丢
    var cached by remember { mutableStateOf(initial) }
    if (initial != null) cached = initial
    val current = cached ?: return
    var draft by remember(current) { mutableStateOf(current) }
    var data by remember { mutableStateOf<EffectVoiceData?>(null) }
    LaunchedEffect(Unit) {
        data = withContext(Dispatchers.IO) {
            val poolOf = VoicePoolStore.voicePoolMap()
            EffectVoiceData(
                pools = VoicePoolStore.enabledPoolNames(),
                // 只给进了声音池的音色：朗读侧能用的就是这些，池外的选了也听不到效果
                voices = VoicePoolStore.allVoicePairs()
                    .filter { poolOf.containsKey(it.first) }
                    .map { EffectVoiceOption(it.first, it.second, poolOf.getValue(it.first)) },
            )
        }
    }
    var expandedRow by remember { mutableStateOf<String?>(null) }
    val voices = data?.voices.orEmpty()
    val voiceOptions = remember(voices, audition.pool) {
        voices.filter { audition.pool.isBlank() || it.pools.contains(audition.pool) }
            .map { CastOption(it.id, it.label) }
    }
    LaunchedEffect(data) {
        // 第一次进来先随手挑一把，试听不必先翻下拉
        if (audition.voiceId.isBlank()) {
            voices.firstOrNull()?.let {
                audition.voiceId = it.id
                audition.voiceQuery = it.label
            }
        }
    }
    val reverbNames = listOf(
        stringResource(R.string.voice_effect_reverb_none),
        stringResource(R.string.voice_effect_reverb_small_room),
        stringResource(R.string.voice_effect_reverb_medium_room),
        stringResource(R.string.voice_effect_reverb_large_room),
        stringResource(R.string.voice_effect_reverb_medium_hall),
        stringResource(R.string.voice_effect_reverb_large_hall),
        stringResource(R.string.voice_effect_reverb_plate),
    )
    AppModalBottomSheet(
        show = initial != null,
        onDismissRequest = onDismiss,
        title = stringResource(
            if (isNew) R.string.voice_effect_create else R.string.voice_effect_edit,
        ),
        // 试听与保存都作用于下面这份草稿，钉在头部：参数区可以滚，不必滚回顶部才听得见/存得了
        startAction = {
            VoiceAuditionAction(
                voiceId = audition.voiceId,
                text = remember(context) { VoiceAudition.defaultPreviewText(context) },
                draft = draft,
            )
        },
        // 不给取消按钮：草稿全在本弹层里，点保存才落库，关掉就是丢弃，没有需要反悔的中间态
        endAction = {
            MediumTonalButton(
                onClick = { onSave(draft) },
                enabled = draft.name.isNotBlank(),
                icon = AppIcons.Check,
                contentDescription = stringResource(R.string.save),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CastFieldStack(
                specs = listOf(
                    CastFieldSpec(
                        id = "name",
                        label = stringResource(R.string.voice_effect_name),
                        value = draft.name,
                        onValueChange = { draft = draft.copy(name = it) },
                    ),
                    CastFieldSpec(
                        id = "pool",
                        label = stringResource(R.string.cast_voice_pool),
                        value = audition.pool,
                        options = data?.pools?.map { CastOption(it, it) }.orEmpty(),
                        expanded = expandedRow == "pool",
                        onValueChange = { audition.pool = it },
                        onSelected = { option ->
                            audition.pool = option.key
                            if (audition.voiceId.isNotBlank() &&
                                voices.firstOrNull { it.id == audition.voiceId }
                                    ?.pools?.contains(option.key) != true
                            ) {
                                audition.voiceId = ""
                                audition.voiceQuery = ""
                            }
                        },
                        onExpand = { open -> expandedRow = if (open) "pool" else null },
                    ),
                    CastFieldSpec(
                        id = "voice",
                        label = stringResource(R.string.cast_voice),
                        value = audition.voiceQuery,
                        options = voiceOptions,
                        expanded = expandedRow == "voice",
                        onValueChange = { audition.voiceQuery = it },
                        onSelected = { option ->
                            audition.voiceId = option.key
                            audition.voiceQuery = option.label
                        },
                        onExpand = { open -> expandedRow = if (open) "voice" else null },
                    ),
                ),
            )
            // 参数区单独一块可滚：任一候选列表一展开就把它整块撤掉——弹层高度是硬约束，
            // 两组同时铺开必然顶到键盘。这里不套 expandVertically/shrinkVertically：
            // 撤参数与开候选两条高度动画同时在跑，净高度先涨后落，整块会上下抽动，
            // 而共享输入框靠外壳的实时坐标对齐，慢一帧就是看得见的错位。
            if (expandedRow == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = castCardMaxHeight(0.4f))
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TinySliderSettingItem(
                        title = stringResource(R.string.voice_effect_pitch),
                        value = draft.pitch,
                        valueRange = VoiceEffectAudio.MIN_PITCH..VoiceEffectAudio.MAX_PITCH,
                        steps = 20,
                        showDecimal = true,
                        stepSize = 0.01f,
                        valueFormat = { "×" + String.format(Locale.ENGLISH, "%.2f", it) },
                        onValueChange = { draft = draft.copy(pitch = it) },
                    )
                    TinySliderSettingItem(
                        title = stringResource(R.string.voice_effect_speed),
                        value = draft.speed,
                        valueRange = VoiceEffectAudio.MIN_SPEED..VoiceEffectAudio.MAX_SPEED,
                        steps = 15,
                        showDecimal = true,
                        stepSize = 0.01f,
                        valueFormat = { "×" + String.format(Locale.ENGLISH, "%.2f", it) },
                        onValueChange = { draft = draft.copy(speed = it) },
                    )
                    // 混响是 7 个平台预设号，不是连续量：给下拉而不是滑杆，
                    // 免得拖出一个「介于小房间和大房间之间」的无效值
                    TinyDropdownSettingItem(
                        title = stringResource(R.string.voice_effect_reverb),
                        selectedValue = draft.reverbPreset.toString(),
                        displayEntries = reverbNames.toTypedArray(),
                        entryValues = reverbNames.indices.map { it.toString() }.toTypedArray(),
                        onValueChange = {
                            draft = draft.copy(reverbPreset = it.toIntOrNull() ?: 0)
                        },
                    )
                    TinySwitchSettingItem(
                        title = stringResource(R.string.voice_effect_metal),
                        checked = draft.metal,
                        onCheckedChange = { draft = draft.copy(metal = it) },
                    )
                }
            }
        }
    }
}

private fun readEffectDocument(context: Context, uri: Uri, onText: (String) -> Unit) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
    }.getOrNull()?.let(onText)
}

/**
 * 试听工作台选中的池与音色。记在页面级、不进数据库：它只是「用哪把声音来听这份参数」，
 * 真正拥有音色的是配音角色那一侧。
 */
@Stable
internal class EffectAuditionState {
    var pool by mutableStateOf("")
    var voiceId by mutableStateOf("")
    var voiceQuery by mutableStateOf("")
}

internal class EffectVoiceData(val pools: List<String>, val voices: List<EffectVoiceOption>)

internal class EffectVoiceOption(val id: String, val label: String, val pools: Set<String>)
