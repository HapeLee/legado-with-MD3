package io.legado.app.data.repository

import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.ReadStyleGateway
import io.legado.app.domain.gateway.ReadStyleBooleanKey
import io.legado.app.domain.gateway.ReadStyleColorKey
import io.legado.app.domain.gateway.ReadStyleFloatKey
import io.legado.app.domain.gateway.ReadStyleIntKey
import io.legado.app.domain.gateway.ReadStyleMutation
import io.legado.app.domain.gateway.ReadStyleStringKey
import io.legado.app.domain.model.settings.ReadStyleItem
import io.legado.app.domain.model.settings.ReadStyleState
import io.legado.app.help.DefaultData
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.AppConfigStore
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
                ReadBookConfig.durConfig.setCurBg(mutation.type, mutation.value)
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
        val index = configStore.addConfig(ReadBookConfig.Config())
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

    override fun importCurrentStyle(bytes: ByteArray) {
        ReadBookConfig.durConfig = readStyleRepository.import(bytes)
        save()
    }

    override fun importOrReplaceStyle(bytes: ByteArray): String {
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

    override fun saveBackgroundImage(inputStream: InputStream, displayName: String?): String =
        readStyleRepository.saveBackgroundImage(inputStream, displayName)

    override fun setCurrentBackgroundImage(path: String) {
        ReadBookConfig.durConfig.setCurBg(2, path)
        save()
    }

    override fun setCurrentBackgroundImageForMode(path: String, isNight: Boolean) {
        if (isNight) {
            ReadBookConfig.durConfig.bgTypeNight = 2
            ReadBookConfig.durConfig.bgStrNight = path
        } else {
            ReadBookConfig.durConfig.bgType = 2
            ReadBookConfig.durConfig.bgStr = path
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

    private fun updateInt(key: ReadStyleIntKey, value: Int) {
        val config = ReadBookConfig.config
        when (key) {
            ReadStyleIntKey.TextSize -> config.textSize = value
            ReadStyleIntKey.LineSpacing -> config.lineSpacingExtra = value
            ReadStyleIntKey.ParagraphSpacing -> config.paragraphSpacing = value
            ReadStyleIntKey.TextBold -> config.textBold = value
            ReadStyleIntKey.TitleMode -> config.titleMode = value
            ReadStyleIntKey.TitleBold -> config.titleBold = value
            ReadStyleIntKey.TitleLineSpacingExtra -> config.titleLineSpacingExtra = value
            ReadStyleIntKey.TitleLineSpacingSub -> config.titleLineSpacingSub = value
            ReadStyleIntKey.TitleSize -> config.titleSize = value
            ReadStyleIntKey.TitleTopSpacing -> config.titleTopSpacing = value
            ReadStyleIntKey.TitleBottomSpacing -> config.titleBottomSpacing = value
            ReadStyleIntKey.TitleSegType -> config.titleSegType = value
            ReadStyleIntKey.TitleSegDistance -> config.titleSegDistance = value
            ReadStyleIntKey.HeaderMode -> config.headerMode = value
            ReadStyleIntKey.FooterMode -> config.footerMode = value
            ReadStyleIntKey.TipHeaderLeft -> config.tipHeaderLeft = value
            ReadStyleIntKey.TipHeaderMiddle -> config.tipHeaderMiddle = value
            ReadStyleIntKey.TipHeaderRight -> config.tipHeaderRight = value
            ReadStyleIntKey.TipFooterLeft -> config.tipFooterLeft = value
            ReadStyleIntKey.TipFooterMiddle -> config.tipFooterMiddle = value
            ReadStyleIntKey.TipFooterRight -> config.tipFooterRight = value
            ReadStyleIntKey.HeaderFontSize -> config.headerFontSize = value
            ReadStyleIntKey.FooterFontSize -> config.footerFontSize = value
            ReadStyleIntKey.PageAnim -> config.setCurPageAnim(value)
            ReadStyleIntKey.UnderlineHeight -> config.underlineHeight = value
            ReadStyleIntKey.UnderlinePadding -> config.underlinePadding = value
            ReadStyleIntKey.PaddingTop -> config.paddingTop = value
            ReadStyleIntKey.PaddingBottom -> config.paddingBottom = value
            ReadStyleIntKey.PaddingLeft -> config.paddingLeft = value
            ReadStyleIntKey.PaddingRight -> config.paddingRight = value
            ReadStyleIntKey.HeaderPaddingTop -> config.headerPaddingTop = value
            ReadStyleIntKey.HeaderPaddingBottom -> config.headerPaddingBottom = value
            ReadStyleIntKey.HeaderPaddingLeft -> config.headerPaddingLeft = value
            ReadStyleIntKey.HeaderPaddingRight -> config.headerPaddingRight = value
            ReadStyleIntKey.FooterPaddingTop -> config.footerPaddingTop = value
            ReadStyleIntKey.FooterPaddingBottom -> config.footerPaddingBottom = value
            ReadStyleIntKey.FooterPaddingLeft -> config.footerPaddingLeft = value
            ReadStyleIntKey.FooterPaddingRight -> config.footerPaddingRight = value
            ReadStyleIntKey.BgType -> ReadBookConfig.durConfig.bgType = value
            ReadStyleIntKey.BgTypeNight -> ReadBookConfig.durConfig.bgTypeNight = value
            ReadStyleIntKey.BgTypeEInk -> ReadBookConfig.durConfig.bgTypeEInk = value
            ReadStyleIntKey.BgAlpha -> config.bgAlpha = value
        }
    }

    private fun updateFloat(key: ReadStyleFloatKey, value: Float) {
        val config = ReadBookConfig.config
        when (key) {
            ReadStyleFloatKey.LetterSpacing -> config.letterSpacing = value
            ReadStyleFloatKey.TitleSegScaling -> config.titleSegScaling = value
            ReadStyleFloatKey.ShadowRadius -> config.shadowRadius = value
            ReadStyleFloatKey.ShadowDx -> config.shadowDx = value
            ReadStyleFloatKey.ShadowDy -> config.shadowDy = value
            ReadStyleFloatKey.DottedBase -> ReadBookConfig.durConfig.dottedBase = value
            ReadStyleFloatKey.DottedRatio -> ReadBookConfig.durConfig.dottedRatio = value
        }
    }

    private fun updateBoolean(key: ReadStyleBooleanKey, value: Boolean) {
        val config = ReadBookConfig.config
        when (key) {
            ReadStyleBooleanKey.TextItalic -> config.textItalic = value
            ReadStyleBooleanKey.TextShadow -> config.textShadow = value
            ReadStyleBooleanKey.Underline -> config.underline = value
            ReadStyleBooleanKey.DottedLine -> config.dottedLine = value
            ReadStyleBooleanKey.UnderlineExtend -> config.underlineExtend = value
            ReadStyleBooleanKey.ShowHeaderLine -> config.showHeaderLine = value
            ReadStyleBooleanKey.ShowFooterLine -> config.showFooterLine = value
            ReadStyleBooleanKey.ApplyHeaderStyle -> config.applyHeaderStyle = value
            ReadStyleBooleanKey.StatusIconDark -> ReadBookConfig.durConfig.setCurStatusIconDark(value)
        }
    }

    private fun updateString(key: ReadStyleStringKey, value: String) {
        val config = ReadBookConfig.config
        when (key) {
            ReadStyleStringKey.TextFont -> config.textFont = value
            ReadStyleStringKey.ParagraphIndent -> config.paragraphIndent = value
            ReadStyleStringKey.TitleFont -> config.titleFont = value
            ReadStyleStringKey.TitleSegFlag -> config.titleSegFlag = value
            ReadStyleStringKey.HeaderFont -> config.headerFont = value
            ReadStyleStringKey.FooterFont -> config.footerFont = value
            ReadStyleStringKey.CustomTipHeaderLeft -> config.customTipHeaderLeft = value
            ReadStyleStringKey.CustomTipHeaderMiddle -> config.customTipHeaderMiddle = value
            ReadStyleStringKey.CustomTipHeaderRight -> config.customTipHeaderRight = value
            ReadStyleStringKey.CustomTipFooterLeft -> config.customTipFooterLeft = value
            ReadStyleStringKey.CustomTipFooterMiddle -> config.customTipFooterMiddle = value
            ReadStyleStringKey.CustomTipFooterRight -> config.customTipFooterRight = value
            ReadStyleStringKey.BgStr -> ReadBookConfig.durConfig.bgStr = value
            ReadStyleStringKey.BgStrNight -> ReadBookConfig.durConfig.bgStrNight = value
            ReadStyleStringKey.BgStrEInk -> ReadBookConfig.durConfig.bgStrEInk = value
            ReadStyleStringKey.StyleName -> ReadBookConfig.durConfig.name = value
        }
    }

    private fun updateColor(key: ReadStyleColorKey, value: Int) {
        val config = ReadBookConfig.config
        when (key) {
            ReadStyleColorKey.Text -> ReadBookConfig.durConfig.setCurTextColor(value)
            ReadStyleColorKey.TextAccent -> ReadBookConfig.durConfig.setCurTextAccentColor(value)
            ReadStyleColorKey.Title -> config.titleColor = value
            ReadStyleColorKey.TitleNight -> config.titleColorNight = value
            ReadStyleColorKey.TipHeader -> config.tipHeaderColor = value
            ReadStyleColorKey.TipHeaderNight -> config.tipHeaderColorNight = value
            ReadStyleColorKey.TipFooter -> config.tipFooterColor = value
            ReadStyleColorKey.TipFooterNight -> config.tipFooterColorNight = value
            ReadStyleColorKey.TipDivider -> config.tipDividerColor = value
            ReadStyleColorKey.Shadow -> ReadBookConfig.durConfig.setCurShadColor(value)
            ReadStyleColorKey.Underline -> ReadBookConfig.durConfig.setUnderlineColor(value)
        }
    }

    private fun buildState(): ReadStyleState = ReadStyleState(
        revision = stateRevision.incrementAndGet(),
        items = configStore.configsSnapshot().map { config ->
            ReadStyleItem(
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
