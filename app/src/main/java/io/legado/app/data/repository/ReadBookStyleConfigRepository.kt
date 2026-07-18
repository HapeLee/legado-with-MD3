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

class ReadBookStyleConfigRepository(
    private val readStyleRepository: ReadStyleRepository,
    private val highlightRuleRepository: HighlightRuleRepository,
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
    private val _state = MutableStateFlow(buildState())
    override val state: StateFlow<ReadStyleState> = _state.asStateFlow()
    override val currentState: ReadStyleState get() = _state.value

    override fun refresh() {
        ReadBookConfig.initConfigs()
        ReadBookConfig.initShareConfig()
        publishState()
    }

    override fun save() {
        publishState()
        saveQueue.submit(
            ReadStyleSaveSnapshot(
                configs = ReadBookConfig.configsSnapshot(),
                shareConfig = ReadBookConfig.shareConfigSnapshot(),
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

    override fun applyPreset(index: Int): Boolean {
        val preset = DefaultData.readConfigs.getOrNull(index) ?: return false
        val copy = GSON.fromJsonObject<ReadBookConfig.Config>(GSON.toJson(preset)).getOrNull()
            ?: return false
        ReadBookConfig.durConfig = copy
        save()
        return true
    }

    override fun addStyle(): Int {
        val index = ReadBookConfig.addConfig(ReadBookConfig.Config())
        save()
        return index
    }

    override fun deleteCurrentStyle(): Boolean {
        val deletedConfigName = ReadBookConfig.durConfig.name
        val removedIndex = ReadBookConfig.deleteDur()
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
        val name = ReadBookConfig.importOrReplaceConfig(readStyleRepository.import(bytes))
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

    override fun exportConfigsJson(): String = GSON.toJson(ReadBookConfig.configsSnapshot())

    override fun exportShareConfigJson(): String = GSON.toJson(ReadBookConfig.shareConfigSnapshot())

    private fun publishState() {
        _state.value = buildState()
    }

    private fun updateInt(key: ReadStyleIntKey, value: Int) {
        when (key) {
            ReadStyleIntKey.TextSize -> ReadBookConfig.textSize = value
            ReadStyleIntKey.LineSpacing -> ReadBookConfig.lineSpacingExtra = value
            ReadStyleIntKey.ParagraphSpacing -> ReadBookConfig.paragraphSpacing = value
            ReadStyleIntKey.TextBold -> ReadBookConfig.textBold = value
            ReadStyleIntKey.TitleMode -> ReadBookConfig.titleMode = value
            ReadStyleIntKey.TitleBold -> ReadBookConfig.titleBold = value
            ReadStyleIntKey.TitleLineSpacingExtra -> ReadBookConfig.titleLineSpacingExtra = value
            ReadStyleIntKey.TitleLineSpacingSub -> ReadBookConfig.titleLineSpacingSub = value
            ReadStyleIntKey.TitleSize -> ReadBookConfig.titleSize = value
            ReadStyleIntKey.TitleTopSpacing -> ReadBookConfig.titleTopSpacing = value
            ReadStyleIntKey.TitleBottomSpacing -> ReadBookConfig.titleBottomSpacing = value
            ReadStyleIntKey.TitleSegType -> ReadBookConfig.titleSegType = value
            ReadStyleIntKey.TitleSegDistance -> ReadBookConfig.titleSegDistance = value
            ReadStyleIntKey.HeaderMode -> ReadBookConfig.headerMode = value
            ReadStyleIntKey.FooterMode -> ReadBookConfig.footerMode = value
            ReadStyleIntKey.TipHeaderLeft -> ReadBookConfig.tipHeaderLeft = value
            ReadStyleIntKey.TipHeaderMiddle -> ReadBookConfig.tipHeaderMiddle = value
            ReadStyleIntKey.TipHeaderRight -> ReadBookConfig.tipHeaderRight = value
            ReadStyleIntKey.TipFooterLeft -> ReadBookConfig.tipFooterLeft = value
            ReadStyleIntKey.TipFooterMiddle -> ReadBookConfig.tipFooterMiddle = value
            ReadStyleIntKey.TipFooterRight -> ReadBookConfig.tipFooterRight = value
            ReadStyleIntKey.HeaderFontSize -> ReadBookConfig.headerFontSize = value
            ReadStyleIntKey.PageAnim -> ReadBookConfig.pageAnim = value
            ReadStyleIntKey.UnderlineHeight -> ReadBookConfig.underlineHeight = value
            ReadStyleIntKey.UnderlinePadding -> ReadBookConfig.underlinePadding = value
            ReadStyleIntKey.PaddingTop -> ReadBookConfig.paddingTop = value
            ReadStyleIntKey.PaddingBottom -> ReadBookConfig.paddingBottom = value
            ReadStyleIntKey.PaddingLeft -> ReadBookConfig.paddingLeft = value
            ReadStyleIntKey.PaddingRight -> ReadBookConfig.paddingRight = value
            ReadStyleIntKey.HeaderPaddingTop -> ReadBookConfig.headerPaddingTop = value
            ReadStyleIntKey.HeaderPaddingBottom -> ReadBookConfig.headerPaddingBottom = value
            ReadStyleIntKey.HeaderPaddingLeft -> ReadBookConfig.headerPaddingLeft = value
            ReadStyleIntKey.HeaderPaddingRight -> ReadBookConfig.headerPaddingRight = value
            ReadStyleIntKey.FooterPaddingTop -> ReadBookConfig.footerPaddingTop = value
            ReadStyleIntKey.FooterPaddingBottom -> ReadBookConfig.footerPaddingBottom = value
            ReadStyleIntKey.FooterPaddingLeft -> ReadBookConfig.footerPaddingLeft = value
            ReadStyleIntKey.FooterPaddingRight -> ReadBookConfig.footerPaddingRight = value
            ReadStyleIntKey.BgType -> ReadBookConfig.durConfig.bgType = value
            ReadStyleIntKey.BgTypeNight -> ReadBookConfig.durConfig.bgTypeNight = value
            ReadStyleIntKey.BgTypeEInk -> ReadBookConfig.durConfig.bgTypeEInk = value
            ReadStyleIntKey.BgAlpha -> ReadBookConfig.bgAlpha = value
        }
    }

    private fun updateFloat(key: ReadStyleFloatKey, value: Float) {
        when (key) {
            ReadStyleFloatKey.LetterSpacing -> ReadBookConfig.letterSpacing = value
            ReadStyleFloatKey.TitleSegScaling -> ReadBookConfig.titleSegScaling = value
            ReadStyleFloatKey.ShadowRadius -> ReadBookConfig.shadowRadius = value
            ReadStyleFloatKey.ShadowDx -> ReadBookConfig.shadowDx = value
            ReadStyleFloatKey.ShadowDy -> ReadBookConfig.shadowDy = value
            ReadStyleFloatKey.DottedBase -> ReadBookConfig.durConfig.dottedBase = value
            ReadStyleFloatKey.DottedRatio -> ReadBookConfig.durConfig.dottedRatio = value
        }
    }

    private fun updateBoolean(key: ReadStyleBooleanKey, value: Boolean) {
        when (key) {
            ReadStyleBooleanKey.TextItalic -> ReadBookConfig.textItalic = value
            ReadStyleBooleanKey.TextShadow -> ReadBookConfig.textShadow = value
            ReadStyleBooleanKey.Underline -> ReadBookConfig.underline = value
            ReadStyleBooleanKey.DottedLine -> ReadBookConfig.dottedLine = value
            ReadStyleBooleanKey.UnderlineExtend -> ReadBookConfig.underlineExtend = value
            ReadStyleBooleanKey.ShowHeaderLine -> ReadBookConfig.showHeaderLine = value
            ReadStyleBooleanKey.ShowFooterLine -> ReadBookConfig.showFooterLine = value
            ReadStyleBooleanKey.StatusIconDark -> ReadBookConfig.durConfig.setCurStatusIconDark(value)
        }
    }

    private fun updateString(key: ReadStyleStringKey, value: String) {
        when (key) {
            ReadStyleStringKey.TextFont -> ReadBookConfig.textFont = value
            ReadStyleStringKey.ParagraphIndent -> ReadBookConfig.paragraphIndent = value
            ReadStyleStringKey.TitleFont -> ReadBookConfig.titleFont = value
            ReadStyleStringKey.TitleSegFlag -> ReadBookConfig.titleSegFlag = value
            ReadStyleStringKey.HeaderFont -> ReadBookConfig.headerFont = value
            ReadStyleStringKey.CustomTipHeaderLeft -> ReadBookConfig.customTipHeaderLeft = value
            ReadStyleStringKey.CustomTipHeaderMiddle -> ReadBookConfig.customTipHeaderMiddle = value
            ReadStyleStringKey.CustomTipHeaderRight -> ReadBookConfig.customTipHeaderRight = value
            ReadStyleStringKey.CustomTipFooterLeft -> ReadBookConfig.customTipFooterLeft = value
            ReadStyleStringKey.CustomTipFooterMiddle -> ReadBookConfig.customTipFooterMiddle = value
            ReadStyleStringKey.CustomTipFooterRight -> ReadBookConfig.customTipFooterRight = value
            ReadStyleStringKey.BgStr -> ReadBookConfig.durConfig.bgStr = value
            ReadStyleStringKey.BgStrNight -> ReadBookConfig.durConfig.bgStrNight = value
            ReadStyleStringKey.BgStrEInk -> ReadBookConfig.durConfig.bgStrEInk = value
            ReadStyleStringKey.StyleName -> ReadBookConfig.durConfig.name = value
        }
    }

    private fun updateColor(key: ReadStyleColorKey, value: Int) {
        when (key) {
            ReadStyleColorKey.Text -> ReadBookConfig.durConfig.setCurTextColor(value)
            ReadStyleColorKey.TextAccent -> ReadBookConfig.durConfig.setCurTextAccentColor(value)
            ReadStyleColorKey.Title -> ReadBookConfig.titleColor = value
            ReadStyleColorKey.TitleNight -> ReadBookConfig.titleColorNight = value
            ReadStyleColorKey.TipHeader -> ReadBookConfig.tipHeaderColor = value
            ReadStyleColorKey.TipHeaderNight -> ReadBookConfig.tipHeaderColorNight = value
            ReadStyleColorKey.TipFooter -> ReadBookConfig.tipFooterColor = value
            ReadStyleColorKey.TipFooterNight -> ReadBookConfig.tipFooterColorNight = value
            ReadStyleColorKey.TipDivider -> ReadBookConfig.tipDividerColor = value
            ReadStyleColorKey.Shadow -> ReadBookConfig.durConfig.setCurShadColor(value)
            ReadStyleColorKey.Underline -> ReadBookConfig.durConfig.setUnderlineColor(value)
        }
    }

    private fun buildState(): ReadStyleState = ReadStyleState(
        items = ReadBookConfig.configsSnapshot().map { config ->
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
