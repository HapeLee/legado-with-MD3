package io.legado.app.data.repository

import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.ReadStyleBooleanKey
import io.legado.app.domain.gateway.ReadStyleColorKey
import io.legado.app.domain.gateway.ReadStyleFloatKey
import io.legado.app.domain.gateway.ReadStyleGateway
import io.legado.app.domain.gateway.ReadStyleIntKey
import io.legado.app.domain.gateway.ReadStyleMutation
import io.legado.app.domain.gateway.ReadStyleStringKey
import io.legado.app.domain.model.settings.READ_STYLE_PRESET_COUNT
import io.legado.app.domain.model.settings.ReadStyleItem
import io.legado.app.domain.model.settings.ReadStyleState
import io.legado.app.help.DefaultData
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import splitties.init.appCtx
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong

class ReadBookStyleConfigRepository(
    private val readStyleRepository: ReadStyleRepository,
    private val highlightRuleRepository: HighlightRuleRepository,
    private val configStore: ReadStyleConfigStore,
) : ReadStyleGateway {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveQueue = ReadStyleSaveQueue(
        scope = scope,
        persist = { snapshot ->
            readStyleRepository.save(snapshot.configs, snapshot.shareConfig)
        },
        onFailure = { error ->
            AppLog.put("保存排版配置文件出错", error)
        },
    )
    private val stateRevision = AtomicLong(0L)
    private val _state = MutableStateFlow(buildState())
    override val state: StateFlow<ReadStyleState> = _state.asStateFlow()
    override val currentState: ReadStyleState get() = _state.value

    override fun refresh() {
        configStore.initConfigs()
        configStore.initShareConfig()
        publishState()
    }

    override fun notifyModeChanged() {
        publishState()
    }

    override fun save() {
        publishState()
        saveQueue.submit(
            ReadStyleSaveSnapshot(
                configs = configStore.configsSnapshot(),
                shareConfig = configStore.shareConfigSnapshot(),
            )
        )
    }

    override fun updateCurrentStyle(mutation: ReadStyleMutation) {
        when (mutation) {
            is ReadStyleMutation.IntValue -> updateInt(mutation.key, mutation.value)
            is ReadStyleMutation.FloatValue -> updateFloat(mutation.key, mutation.value)
            is ReadStyleMutation.BooleanValue -> updateBoolean(mutation.key, mutation.value)
            is ReadStyleMutation.StringValue -> updateString(mutation.key, mutation.value)
            is ReadStyleMutation.ColorValue -> updateColor(mutation.key, mutation.value)
            is ReadStyleMutation.Background ->
                mutateCurrentStyle { it.withCurBg(mutation.type, mutation.value) }
        }
        publishState()
    }

    override fun clearMissingTextFont() {
        updateCurrentStyle(ReadStyleMutation.StringValue(ReadStyleStringKey.TextFont, ""))
        save()
    }

    override fun applyPreset(index: Int): Boolean {
        val preset = DefaultData.readConfigs.getOrNull(index) ?: return false
        val copy = GSON.fromJsonObject<ReadBookConfig.Config>(GSON.toJson(preset)).getOrNull()
            ?: return false
        ReadBookConfig.durConfig = copy
        save()
        return true
    }

    override fun addStyle(): Int {
        // 新主题 = 复刻"正在使用"那份的**排版**（共享排版开着时取共享那份的排版 —— 与"导出当前样式"
        // 同一个口径，见 ReadBookConfig.getExportConfig），但**不继承背景图**：背景整组回默认纯色。
        val index = configStore.addConfig(ReadBookConfig.getExportConfig().asNewStyle())
        save()
        return index
    }

    override fun deleteCurrentStyle(): Boolean {
        val deletedConfigName = ReadBookConfig.durConfig.name
        val removedIndex = ReadBookConfig.styleSelect
            .takeIf { configStore.deleteConfigAt(it) }
        if (removedIndex != null) {
            val readIndex = AppConfigStore.getInt(PreferKey.readStyleSelect) ?: 0
            val comicIndex = AppConfigStore.getInt(PreferKey.comicStyleSelect) ?: readIndex
            AppConfigStore.putAll(
                mapOf(
                    PreferKey.readStyleSelect to if (removedIndex <= readIndex) {
                        (readIndex - 1).coerceAtLeast(0)
                    } else readIndex,
                    PreferKey.comicStyleSelect to if (removedIndex <= comicIndex) {
                        (comicIndex - 1).coerceAtLeast(0)
                    } else comicIndex,
                )
            )
            highlightRuleRepository.removeConfigBinding(deletedConfigName)
            save()
        }
        return removedIndex != null
    }

    override fun moveStyle(from: Int, to: Int): Boolean {
        if (!configStore.moveStyle(from, to)) return false
        // 选中下标跟着移动，否则重排后"当前样式"会跳到别人身上
        val readIndex = AppConfigStore.getInt(PreferKey.readStyleSelect) ?: 0
        val comicIndex = AppConfigStore.getInt(PreferKey.comicStyleSelect) ?: readIndex
        AppConfigStore.putAll(
            mapOf(
                PreferKey.readStyleSelect to shiftIndexAfterMove(readIndex, from, to),
                PreferKey.comicStyleSelect to shiftIndexAfterMove(comicIndex, from, to),
            )
        )
        save()
        return true
    }

    override fun deleteStylesAt(indices: List<Int>): Boolean {
        val snapshot = configStore.configsSnapshot()
        val targets = indices
            .filter { it >= READ_STYLE_PRESET_COUNT && it in snapshot.indices }
            .distinct()
            .sorted()
        if (targets.isEmpty()) return false
        // 名字要在删之前取——删完就查不到了，而高亮规则按名字解绑
        val removedNames = targets.map { snapshot[it].name }
        if (!configStore.deleteStylesAt(targets)) return false

        // 每删掉一份、且它不晚于当前选中项，选中下标就左移一位
        fun shift(index: Int) = (index - targets.count { it <= index }).coerceAtLeast(0)
        val readIndex = AppConfigStore.getInt(PreferKey.readStyleSelect) ?: 0
        val comicIndex = AppConfigStore.getInt(PreferKey.comicStyleSelect) ?: readIndex
        AppConfigStore.putAll(
            mapOf(
                PreferKey.readStyleSelect to shift(readIndex),
                PreferKey.comicStyleSelect to shift(comicIndex),
            )
        )
        removedNames.forEach { highlightRuleRepository.removeConfigBinding(it) }
        save()
        return true
    }

    override fun moveStylesToEdge(indices: List<Int>, toTop: Boolean): Boolean {
        val before = configStore.configsSnapshot()
        val readIndex = AppConfigStore.getInt(PreferKey.readStyleSelect) ?: 0
        val comicIndex = AppConfigStore.getInt(PreferKey.comicStyleSelect) ?: readIndex
        // 记下"选中那份"的实例：搬完按实例找新位置，同名样式也不会认错
        val selectedRead = before.getOrNull(readIndex)
        val selectedComic = before.getOrNull(comicIndex)
        if (!configStore.moveStylesToEdge(indices, toTop)) return false
        val after = configStore.configsSnapshot()
        AppConfigStore.putAll(
            mapOf(
                PreferKey.readStyleSelect to after.newIndexOf(selectedRead, readIndex),
                PreferKey.comicStyleSelect to after.newIndexOf(selectedComic, comicIndex),
            )
        )
        save()
        return true
    }

    /** 按实例找新位置；实例为空或已不在列表里就保持原下标。 */
    private fun List<ReadBookConfig.Config>.newIndexOf(
        instance: ReadBookConfig.Config?,
        fallback: Int,
    ): Int {
        if (instance == null) return fallback
        val index = indexOfFirst { it === instance }
        return if (index >= 0) index else fallback
    }

    /**
     * 重排后的新下标（[io.legado.app.utils.move] 语义：先移除、再插入到 [to]）：
     * 被移动的项自己落到 [to]，被跨过的那些项各让一位。
     */
    private fun shiftIndexAfterMove(index: Int, from: Int, to: Int): Int = when {
        index == from -> to
        from < index && index <= to -> index - 1
        to <= index && index < from -> index + 1
        else -> index
    }

    override fun importCurrentStyle(bytes: ByteArray) {
        val bundle = readStyleRepository.importStylesIfBundle(bytes)
        if (bundle != null) {
            // 批量包：逐份进列表（同名覆盖），不动当前选中
            bundle.forEach { configStore.importOrReplaceConfig(it) }
        } else {
            ReadBookConfig.durConfig = readStyleRepository.import(bytes)
        }
        save()
    }

    override fun importStylesAsNew(bytes: ByteArray): Int? {
        val usedNames = configStore.configsSnapshot().mapTo(hashSetOf()) { it.name }
        // import() 同时保存按名称绑定的高亮规则，必须在它写库前分配新名称。
        // 批量导入共用这一组名称，避免包内同名主题互相覆盖规则。
        fun prepareNewConfig(config: ReadBookConfig.Config): ReadBookConfig.Config {
            val baseName = config.name.ifBlank { appCtx.getString(R.string.text_bg_style) }
            var name = baseName
            var suffix = 2
            while (!usedNames.add(name)) {
                name = "$baseName (${suffix++})"
            }
            return config.copy(name = name)
        }
        val bundle = readStyleRepository.importStylesIfBundle(bytes, ::prepareNewConfig)
        if (bundle != null) {
            // 批量包：逐份追加到末尾（一律新增，不再同名覆盖），不动任何已有主题
            bundle.forEach { configStore.addConfig(it) }
            save()
            return null
        }
        // 单份也先分配新名称，再追加到列表最后。
        val index = configStore.addConfig(readStyleRepository.import(bytes, ::prepareNewConfig))
        save()
        return index
    }

    override fun importOrReplaceStyle(bytes: ByteArray): String {
        val bundle = readStyleRepository.importStylesIfBundle(bytes)
        if (bundle != null) {
            val names = bundle.map { configStore.importOrReplaceConfig(it) }
            save()
            return names.joinToString("、")
        }
        val name = configStore.importOrReplaceConfig(readStyleRepository.import(bytes))
        save()
        return name
    }

    override fun exportCurrentStyle(): ByteArray {
        val config = ReadBookConfig.getExportConfig().copy(
            highlightRules = ArrayList(highlightRuleRepository.load(ReadBookConfig.durConfig.name))
        )
        return readStyleRepository.export(config)
    }

    override fun exportStyles(indices: List<Int>): ByteArray {
        val snapshot = configStore.configsSnapshot()
        return readStyleRepository.exportStyles(
            indices.filter { it in snapshot.indices }.distinct().map { snapshot[it].withExportRules() }
        )
    }

    override fun exportStyleAt(index: Int): ByteArray? {
        // 与"批量包"里那一个小包走同一条 export(config) ⇒ 产物格式完全一致
        val config = configStore.configsSnapshot().getOrNull(index) ?: return null
        return readStyleRepository.export(config.withExportRules())
    }

    private fun ReadBookConfig.Config.withExportRules(): ReadBookConfig.Config = copy(
        highlightRules = ArrayList(highlightRuleRepository.load(name)),
    )

    override fun saveBackgroundImage(inputStream: InputStream, displayName: String?): String =
        readStyleRepository.saveBackgroundImage(inputStream, displayName)

    override fun setCurrentBackgroundImage(path: String) {
        mutateCurrentStyle { it.withCurBg(2, path) }
        save()
    }

    override fun setCurrentBackgroundImageForMode(path: String, isNight: Boolean) {
        mutateCurrentStyle {
            if (isNight) {
                it.copy(bgTypeNight = 2, bgStrNight = path)
            } else {
                it.copy(bgType = 2, bgStr = path)
            }
        }
        save()
    }

    override fun exportConfigsJson(): String = GSON.toJson(configStore.configsSnapshot())

    override fun exportShareConfigJson(): String = GSON.toJson(configStore.shareConfigSnapshot())

    override fun allBackgroundImagePaths(): List<String> = configStore.allPicBgStr()

    override fun clearUnusedBackgrounds() {
        configStore.clearBgAndCache()
    }

    private fun publishState() {
        _state.value = buildState()
    }

    /** 改当前**生效**的那一份（共享排版开着时是共享那份）。 */
    private inline fun mutateEffective(
        crossinline transform: (ReadBookConfig.Config) -> ReadBookConfig.Config,
    ) = configStore.updateEffective(
        index = ReadBookConfig.styleSelect,
        useShare = ReadBookConfig.shareLayout,
        transform = { transform(it) },
    )

    /**
     * 改当前**样式**那一份，共享排版开着时也不动共享那份。
     * 背景、虚线、状态栏图标、样式名这些按样式独立的项走这里——与 R4.7 之前
     * 写 `ReadBookConfig.durConfig.x` 的那些分支一一对应。
     */
    private inline fun mutateCurrentStyle(
        crossinline transform: (ReadBookConfig.Config) -> ReadBookConfig.Config,
    ) = configStore.updateStyleAt(
        index = ReadBookConfig.styleSelect,
        transform = { transform(it) },
    )

    private fun updateInt(key: ReadStyleIntKey, value: Int) {
        when (key) {
            ReadStyleIntKey.TextSize -> mutateEffective { it.copy(textSize = value) }
            ReadStyleIntKey.LineSpacing -> mutateEffective { it.copy(lineSpacingExtra = value) }
            ReadStyleIntKey.ParagraphSpacing -> mutateEffective { it.copy(paragraphSpacing = value) }
            ReadStyleIntKey.TextBold -> mutateEffective { it.copy(textBold = value) }
            ReadStyleIntKey.TitleMode -> mutateEffective { it.copy(titleMode = value) }
            ReadStyleIntKey.TitleBold -> mutateEffective { it.copy(titleBold = value) }
            ReadStyleIntKey.TitleLineSpacingExtra ->
                mutateEffective { it.copy(titleLineSpacingExtra = value) }
            ReadStyleIntKey.TitleLineSpacingSub ->
                mutateEffective { it.copy(titleLineSpacingSub = value) }
            ReadStyleIntKey.TitleSize -> mutateEffective {
                // 顺带把当前的相对/绝对状态固化为显式值：否则旧的「值 < 8 即相对」推断会在
                // 相对模式偏移调到 ≥8 时把模式悄悄翻成绝对。
                it.copy(titleSize = value, titleSizeRelative = it.isTitleSizeRelative())
            }
            ReadStyleIntKey.TitleTopSpacing -> mutateEffective { it.copy(titleTopSpacing = value) }
            ReadStyleIntKey.TitleBottomSpacing ->
                mutateEffective { it.copy(titleBottomSpacing = value) }
            ReadStyleIntKey.TitleSegType -> mutateEffective { it.copy(titleSegType = value) }
            ReadStyleIntKey.TitleSegDistance -> mutateEffective { it.copy(titleSegDistance = value) }
            ReadStyleIntKey.HeaderMode -> mutateEffective { it.copy(headerMode = value) }
            ReadStyleIntKey.FooterMode -> mutateEffective { it.copy(footerMode = value) }
            ReadStyleIntKey.TipHeaderLeft -> mutateEffective { it.copy(tipHeaderLeft = value) }
            ReadStyleIntKey.TipHeaderMiddle -> mutateEffective { it.copy(tipHeaderMiddle = value) }
            ReadStyleIntKey.TipHeaderRight -> mutateEffective { it.copy(tipHeaderRight = value) }
            ReadStyleIntKey.TipFooterLeft -> mutateEffective { it.copy(tipFooterLeft = value) }
            ReadStyleIntKey.TipFooterMiddle -> mutateEffective { it.copy(tipFooterMiddle = value) }
            ReadStyleIntKey.TipFooterRight -> mutateEffective { it.copy(tipFooterRight = value) }
            ReadStyleIntKey.HeaderFontSize -> mutateEffective { it.copy(headerFontSize = value) }
            ReadStyleIntKey.FooterFontSize -> mutateEffective { it.copy(footerFontSize = value) }
            ReadStyleIntKey.PageAnim -> mutateEffective { it.withCurPageAnim(value) }
            ReadStyleIntKey.PageAnimSpeed -> mutateEffective { it.withPageAnimSpeed(value) }
            ReadStyleIntKey.UnderlineHeight -> mutateEffective { it.copy(underlineHeight = value) }
            ReadStyleIntKey.UnderlinePadding -> mutateEffective { it.copy(underlinePadding = value) }
            ReadStyleIntKey.PaddingTop -> mutateEffective { it.copy(paddingTop = value) }
            ReadStyleIntKey.PaddingBottom -> mutateEffective { it.copy(paddingBottom = value) }
            ReadStyleIntKey.PaddingLeft -> mutateEffective { it.copy(paddingLeft = value) }
            ReadStyleIntKey.PaddingRight -> mutateEffective { it.copy(paddingRight = value) }
            ReadStyleIntKey.HeaderPaddingTop -> mutateEffective { it.copy(headerPaddingTop = value) }
            ReadStyleIntKey.HeaderPaddingBottom ->
                mutateEffective { it.copy(headerPaddingBottom = value) }
            ReadStyleIntKey.HeaderPaddingLeft ->
                mutateEffective { it.copy(headerPaddingLeft = value) }
            ReadStyleIntKey.HeaderPaddingRight ->
                mutateEffective { it.copy(headerPaddingRight = value) }
            ReadStyleIntKey.FooterPaddingTop -> mutateEffective { it.copy(footerPaddingTop = value) }
            ReadStyleIntKey.FooterPaddingBottom ->
                mutateEffective { it.copy(footerPaddingBottom = value) }
            ReadStyleIntKey.FooterPaddingLeft ->
                mutateEffective { it.copy(footerPaddingLeft = value) }
            ReadStyleIntKey.FooterPaddingRight ->
                mutateEffective { it.copy(footerPaddingRight = value) }
            ReadStyleIntKey.BgAlpha -> mutateEffective { it.copy(bgAlpha = value) }
            ReadStyleIntKey.BgType -> mutateCurrentStyle { it.copy(bgType = value) }
            ReadStyleIntKey.BgTypeNight -> mutateCurrentStyle { it.copy(bgTypeNight = value) }
            ReadStyleIntKey.BgTypeEInk -> mutateCurrentStyle { it.copy(bgTypeEInk = value) }
        }
    }

    private fun updateFloat(key: ReadStyleFloatKey, value: Float) {
        when (key) {
            ReadStyleFloatKey.LetterSpacing -> mutateEffective { it.copy(letterSpacing = value) }
            ReadStyleFloatKey.TitleSegScaling -> mutateEffective { it.copy(titleSegScaling = value) }
            ReadStyleFloatKey.ShadowRadius -> mutateEffective { it.copy(shadowRadius = value) }
            ReadStyleFloatKey.ShadowDx -> mutateEffective { it.copy(shadowDx = value) }
            ReadStyleFloatKey.ShadowDy -> mutateEffective { it.copy(shadowDy = value) }
            ReadStyleFloatKey.DottedBase -> mutateCurrentStyle { it.copy(dottedBase = value) }
            ReadStyleFloatKey.DottedRatio -> mutateCurrentStyle { it.copy(dottedRatio = value) }
        }
    }

    private fun updateBoolean(key: ReadStyleBooleanKey, value: Boolean) {
        when (key) {
            ReadStyleBooleanKey.TextItalic -> mutateEffective { it.copy(textItalic = value) }
            ReadStyleBooleanKey.TextShadow -> mutateEffective { it.copy(textShadow = value) }
            // 只改解释方式，不动数值：同一数值在相对/绝对下含义不同，切换后标题立即随之变化。
            ReadStyleBooleanKey.TitleSizeRelative ->
                mutateEffective { it.copy(titleSizeRelative = value) }
            ReadStyleBooleanKey.Underline -> mutateEffective { it.copy(underline = value) }
            ReadStyleBooleanKey.DottedLine -> mutateEffective { it.copy(dottedLine = value) }
            ReadStyleBooleanKey.UnderlineExtend -> mutateEffective { it.copy(underlineExtend = value) }
            ReadStyleBooleanKey.ShowHeaderLine -> mutateEffective { it.copy(showHeaderLine = value) }
            ReadStyleBooleanKey.ShowFooterLine -> mutateEffective { it.copy(showFooterLine = value) }
            ReadStyleBooleanKey.ApplyHeaderStyle ->
                mutateEffective { it.copy(applyHeaderStyle = value) }
            ReadStyleBooleanKey.StatusIconDark ->
                mutateCurrentStyle { it.withCurStatusIconDark(value) }
        }
    }

    private fun updateString(key: ReadStyleStringKey, value: String) {
        when (key) {
            ReadStyleStringKey.TextFont -> mutateEffective { it.copy(textFont = value) }
            ReadStyleStringKey.ParagraphIndent -> mutateEffective { it.copy(paragraphIndent = value) }
            ReadStyleStringKey.TitleFont -> mutateEffective { it.copy(titleFont = value) }
            ReadStyleStringKey.TitleSegFlag -> mutateEffective { it.copy(titleSegFlag = value) }
            ReadStyleStringKey.HeaderFont -> mutateEffective { it.copy(headerFont = value) }
            ReadStyleStringKey.FooterFont -> mutateEffective { it.copy(footerFont = value) }
            ReadStyleStringKey.CustomTipHeaderLeft ->
                mutateEffective { it.copy(customTipHeaderLeft = value) }
            ReadStyleStringKey.CustomTipHeaderMiddle ->
                mutateEffective { it.copy(customTipHeaderMiddle = value) }
            ReadStyleStringKey.CustomTipHeaderRight ->
                mutateEffective { it.copy(customTipHeaderRight = value) }
            ReadStyleStringKey.CustomTipFooterLeft ->
                mutateEffective { it.copy(customTipFooterLeft = value) }
            ReadStyleStringKey.CustomTipFooterMiddle ->
                mutateEffective { it.copy(customTipFooterMiddle = value) }
            ReadStyleStringKey.CustomTipFooterRight ->
                mutateEffective { it.copy(customTipFooterRight = value) }
            ReadStyleStringKey.BgStr -> mutateCurrentStyle { it.copy(bgStr = value) }
            ReadStyleStringKey.BgStrNight -> mutateCurrentStyle { it.copy(bgStrNight = value) }
            ReadStyleStringKey.BgStrEInk -> mutateCurrentStyle { it.copy(bgStrEInk = value) }
            ReadStyleStringKey.StyleName -> mutateCurrentStyle { it.copy(name = value) }
        }
    }

    private fun updateColor(key: ReadStyleColorKey, value: Int) {
        when (key) {
            ReadStyleColorKey.Title -> mutateEffective { it.copy(titleColor = value) }
            ReadStyleColorKey.TitleNight -> mutateEffective { it.copy(titleColorNight = value) }
            ReadStyleColorKey.TipHeader -> mutateEffective { it.copy(tipHeaderColor = value) }
            ReadStyleColorKey.TipHeaderNight ->
                mutateEffective { it.copy(tipHeaderColorNight = value) }
            ReadStyleColorKey.TipFooter -> mutateEffective { it.copy(tipFooterColor = value) }
            ReadStyleColorKey.TipFooterNight ->
                mutateEffective { it.copy(tipFooterColorNight = value) }
            ReadStyleColorKey.TipDivider -> mutateEffective { it.copy(tipDividerColor = value) }
            ReadStyleColorKey.Text -> mutateCurrentStyle { it.withCurTextColor(value) }
            ReadStyleColorKey.TextAccent -> mutateCurrentStyle { it.withCurTextAccentColor(value) }
            ReadStyleColorKey.Shadow -> mutateCurrentStyle { it.withCurShadowColor(value) }
            ReadStyleColorKey.Underline -> mutateCurrentStyle { it.withCurUnderlineColor(value) }
        }
    }

    private fun buildState(): ReadStyleState = ReadStyleState(
        revision = stateRevision.incrementAndGet(),
        items = configStore.configsSnapshot().map { config ->
            ReadStyleItem(
                id = config.id,
                name = config.name,
                bgType = config.bgType,
                bgValue = config.bgStr,
                bgTypeNight = config.bgTypeNight,
                bgValueNight = config.bgStrNight,
                bgTypeEInk = config.bgTypeEInk,
                bgValueEInk = config.bgStrEInk,
                textColor = config.getTextColor().toColorIntOrDefault(),
                textColorNight = config.getTextColorNight().toColorIntOrDefault(),
                textColorEInk = config.getTextColorEInk().toColorIntOrDefault(),
            )
        },
        selectedIndex = ReadBookConfig.styleSelect,
        shareLayout = ReadBookConfig.shareLayout,
    )

    private fun String.toColorIntOrDefault(): Int =
        runCatching { android.graphics.Color.parseColor(this) }.getOrDefault(0)
}

internal data class ReadStyleSaveSnapshot(
    val configs: List<ReadBookConfig.Config>,
    val shareConfig: ReadBookConfig.Config,
)

/**
 * 排版配置是完整快照，队列中只需保留最新一份待保存值。
 * 单次文件异常只丢弃该次快照，不得终止后续保存消费。
 */
internal class ReadStyleSaveQueue(
    scope: CoroutineScope,
    private val persist: (ReadStyleSaveSnapshot) -> Unit,
    private val onFailure: (Throwable) -> Unit,
) {
    private val snapshots = Channel<ReadStyleSaveSnapshot>(Channel.CONFLATED)

    init {
        scope.launch {
            for (snapshot in snapshots) {
                try {
                    persist(snapshot)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    onFailure(error)
                }
            }
        }
    }

    fun submit(snapshot: ReadStyleSaveSnapshot) {
        snapshots.trySend(snapshot).getOrThrow()
    }
}
