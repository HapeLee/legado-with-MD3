package io.legado.app.ui.book.read

import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.constant.ReadMenuBlurMode
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.domain.gateway.ReadStyleBooleanKey
import io.legado.app.domain.gateway.ReadStyleColorKey
import io.legado.app.domain.gateway.ReadStyleFloatKey
import io.legado.app.domain.gateway.ReadStyleGateway
import io.legado.app.domain.gateway.ReadStyleIntKey
import io.legado.app.domain.gateway.ReadStyleMutation
import io.legado.app.domain.gateway.ReadStyleStringKey
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ReadSessionState
import io.legado.app.utils.postEvent
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 阅读页配置更新的分发器：把一条 [ConfigUpdate] 落到对应的 repository，并发出相应 effect。
 *
 * **无自持状态**——和 AI/高亮/正文编辑三个域不同，这里没有属于自己的 UiState。
 * 它写的 `menuConfig` 是阅读器 chrome 的状态，被菜单栏读，所以留在 [ReadBookUiState]，
 * 经 [Host.updateMenuConfig] 回写。摘出来的收益是 780 行离开 god object、
 * 158 分支的配置分发变成可独立审阅的一块。
 *
 * 纯排版项由 [toReadStyleMutation] 转成 [ReadStyleMutation] 交给 repository；
 * 转不出 mutation 的（只写 DataStore 的项）走 [Host.refreshConfigSnapshots] ——
 * 这是排版快照三条重建通道里的第三条，删之前先看 `collectReadStyle` 上的注释。
 */
class ReadConfigUpdateDelegate(
    private val scope: CoroutineScope,
    private val host: Host,
    private val readSettingsRepository: ReadSettingsRepository,
    private val readBookStyleConfigRepository: ReadStyleGateway,
) {

    interface Host {
        /** 当前菜单配置快照，个别分支要读旧值做回退判断。 */
        val menuConfig: ReadMenuConfig

        fun updateMenuConfig(transform: (ReadMenuConfig) -> ReadMenuConfig)

        /** 重建 styleConfig + sheetConfig 两份快照（只写 DataStore 的配置项走这条）。 */
        fun refreshConfigSnapshots()

        fun emitEffect(effect: ReadBookEffect)

        /** 重新打开「自动建议日夜」时，清掉本次会话已忽略提醒的标记。 */
        fun resetDayNightReminderDismissal()
    }

    fun handle(update: ConfigUpdate) {
        val styleMutation = update.toReadStyleMutation()
        styleMutation?.let(readBookStyleConfigRepository::updateCurrentStyle)
        when (update) {
            // --- Text style ---
            is ConfigUpdate.TextSize,
            is ConfigUpdate.LetterSpacing,
            is ConfigUpdate.LineSpacing,
            is ConfigUpdate.ParagraphSpacing,
            is ConfigUpdate.ParagraphIndent,
            is ConfigUpdate.TextItalic,
            is ConfigUpdate.TextBold,
            is ConfigUpdate.TextColor,
            is ConfigUpdate.TextAccentColor -> Unit

            // --- Title style ---
            is ConfigUpdate.TitleMode,
            is ConfigUpdate.TitleBold,
            is ConfigUpdate.TitleSegScaling,
            is ConfigUpdate.TitleLineSpacingExtra,
            is ConfigUpdate.TitleLineSpacingSub,
            is ConfigUpdate.TitleSize,
            is ConfigUpdate.TitleTopSpacing,
            is ConfigUpdate.TitleBottomSpacing,
            is ConfigUpdate.TitleColor,
            is ConfigUpdate.TitleColorNight,
            is ConfigUpdate.TitleFont,
            is ConfigUpdate.TitleSegType,
            is ConfigUpdate.TitleSegDistance,
            is ConfigUpdate.TitleSegFlag -> Unit

            // --- Header / footer tips ---
            is ConfigUpdate.HeaderMode,
            is ConfigUpdate.FooterMode,
            is ConfigUpdate.TipHeaderLeft,
            is ConfigUpdate.TipHeaderMiddle,
            is ConfigUpdate.TipHeaderRight,
            is ConfigUpdate.TipFooterLeft,
            is ConfigUpdate.TipFooterMiddle,
            is ConfigUpdate.TipFooterRight,
            is ConfigUpdate.CustomTipHeaderLeft,
            is ConfigUpdate.CustomTipHeaderMiddle,
            is ConfigUpdate.CustomTipHeaderRight,
            is ConfigUpdate.CustomTipFooterLeft,
            is ConfigUpdate.CustomTipFooterMiddle,
            is ConfigUpdate.CustomTipFooterRight,
            is ConfigUpdate.HeaderFont,
            is ConfigUpdate.HeaderFontSize,
            is ConfigUpdate.FooterFont,
            is ConfigUpdate.FooterFontSize,
            is ConfigUpdate.ApplyHeaderStyle,
            is ConfigUpdate.TipHeaderColor,
            is ConfigUpdate.TipHeaderColorNight,
            is ConfigUpdate.TipFooterColor,
            is ConfigUpdate.TipFooterColorNight,
            is ConfigUpdate.TipDividerColor -> Unit

            // --- Layout / style ---
            is ConfigUpdate.StyleSelect -> {
                scope.launch {
                    readSettingsRepository.setStyleSelect(ReadSessionState.isComic, update.index)
                }
            }
            is ConfigUpdate.ShareLayout -> {
                scope.launch {
                    readSettingsRepository.setShareLayout(update.value)
                }
            }
            is ConfigUpdate.PageAnim -> Unit

            // --- Menu appearance ---
            is ConfigUpdate.MenuBgColor -> {
                scope.launch {
                    readSettingsRepository.setReadMenuBgColor(update.color)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            is ConfigUpdate.MenuAccentColor -> {
                scope.launch {
                    readSettingsRepository.setReadMenuAccentColor(update.color)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            is ConfigUpdate.MenuContainerColor -> {
                scope.launch {
                    readSettingsRepository.setReadMenuContainerColor(update.color)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            is ConfigUpdate.MenuBgColorNight -> {
                scope.launch {
                    readSettingsRepository.setReadMenuBgColorNight(update.color)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            is ConfigUpdate.MenuAccentColorNight -> {
                scope.launch {
                    readSettingsRepository.setReadMenuAccentColorNight(update.color)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            is ConfigUpdate.MenuContainerColorNight -> {
                scope.launch {
                    readSettingsRepository.setReadMenuContainerColorNight(update.color)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            is ConfigUpdate.MenuTextColor -> {
                scope.launch {
                    readSettingsRepository.setReadMenuTextColor(update.color)
                }
                host.updateMenuConfig {
                    it.copy(readMenuTextColor = update.color)
                }
            }
            is ConfigUpdate.MenuTextColorNight -> {
                scope.launch {
                    readSettingsRepository.setReadMenuTextColorNight(update.color)
                }
                host.updateMenuConfig {
                    it.copy(readMenuTextColorNight = update.color)
                }
            }
            is ConfigUpdate.MenuColorMode -> {
                val value = update.value.coerceIn(0, 1)
                scope.launch {
                    readSettingsRepository.setReadMenuColorMode(value)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            is ConfigUpdate.ReadBarStyle -> {
                val value = update.value.coerceIn(0, 2)
                scope.launch {
                    readSettingsRepository.setReadBarStyle(value)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }

            // --- Menu bar border ---
            is ConfigUpdate.BorderWidth -> {
                scope.launch {
                    readSettingsRepository.setReadMenuBorderWidth(update.value)
                }
                host.updateMenuConfig { it.copy(readMenuBorderWidth = update.value) }
            }
            is ConfigUpdate.BorderColor -> {
                scope.launch {
                    readSettingsRepository.setReadMenuBorderColor(update.color)
                }
                host.updateMenuConfig { it.copy(readMenuBorderColor = update.color) }
            }
            is ConfigUpdate.BorderColorNight -> {
                scope.launch {
                    readSettingsRepository.setReadMenuBorderColorNight(update.color)
                }
                host.updateMenuConfig { it.copy(readMenuBorderColorNight = update.color) }
            }

            // --- Shadow ---
            is ConfigUpdate.TextShadow,
            is ConfigUpdate.ShadowRadius,
            is ConfigUpdate.ShadowDx,
            is ConfigUpdate.ShadowDy,
            is ConfigUpdate.ShadowColor -> Unit

            // --- Underline ---
            is ConfigUpdate.Underline,
            is ConfigUpdate.DottedLine,
            is ConfigUpdate.UnderlineExtend,
            is ConfigUpdate.UnderlineHeight,
            is ConfigUpdate.UnderlinePadding,
            is ConfigUpdate.DottedBase,
            is ConfigUpdate.DottedRatio,
            is ConfigUpdate.UnderlineColor -> Unit

            // --- Body padding ---
            is ConfigUpdate.PaddingTop,
            is ConfigUpdate.PaddingBottom,
            is ConfigUpdate.PaddingLeft,
            is ConfigUpdate.PaddingRight -> Unit

            // --- Header padding ---
            is ConfigUpdate.HeaderPaddingTop,
            is ConfigUpdate.HeaderPaddingBottom,
            is ConfigUpdate.HeaderPaddingLeft,
            is ConfigUpdate.HeaderPaddingRight,
            is ConfigUpdate.ShowHeaderLine -> Unit

            // --- Footer padding ---
            is ConfigUpdate.FooterPaddingTop,
            is ConfigUpdate.FooterPaddingBottom,
            is ConfigUpdate.FooterPaddingLeft,
            is ConfigUpdate.FooterPaddingRight,
            is ConfigUpdate.ShowFooterLine -> Unit

            // --- Background / display ---
            is ConfigUpdate.BgStr,
            is ConfigUpdate.BgStrNight,
            is ConfigUpdate.BgStrEInk,
            is ConfigUpdate.BgType,
            is ConfigUpdate.BgTypeNight,
            is ConfigUpdate.BgTypeEInk,
            is ConfigUpdate.BgAlpha,
            is ConfigUpdate.StatusIconDark,
            is ConfigUpdate.StyleName -> Unit
            is ConfigUpdate.MenuIconShowText -> {
                scope.launch {
                    readSettingsRepository.setReadMenuIconShowText(update.value)
                }
                host.updateMenuConfig { it.copy(readMenuIconShowText = update.value) }
            }
            is ConfigUpdate.MenuIconStyle -> {
                val value = update.value.coerceIn(0, 2)
                scope.launch {
                    readSettingsRepository.setReadMenuIconStyle(value)
                }
                host.updateMenuConfig { it.copy(readMenuIconStyle = value) }
            }
            is ConfigUpdate.TitleBarIconStyle -> {
                val value = update.value.coerceIn(0, 2)
                scope.launch {
                    readSettingsRepository.setTitleBarIconStyle(value)
                }
                host.updateMenuConfig { it.copy(titleBarIconStyle = value) }
            }
            is ConfigUpdate.MenuIconItemsPerRow -> {
                val value = update.value.coerceIn(2, 8)
                scope.launch {
                    readSettingsRepository.setReadMenuIconItemsPerRow(value)
                }
                host.updateMenuConfig { it.copy(readMenuIconItemsPerRow = value) }
            }
            is ConfigUpdate.MenuIconRowCount -> {
                val value = update.value.coerceIn(1, 2)
                scope.launch {
                    readSettingsRepository.setReadMenuIconRowCount(value)
                }
                host.updateMenuConfig { it.copy(readMenuIconRowCount = value) }
            }
            is ConfigUpdate.MenuBottomCornerRadius -> {
                val value = update.value.coerceIn(0, 32)
                scope.launch {
                    readSettingsRepository.setReadMenuBottomCornerRadius(value)
                }
                host.updateMenuConfig { it.copy(readMenuBottomCornerRadius = value) }
            }
            is ConfigUpdate.FloatingBottomBar -> {
                val needsBlurFallback = !update.value &&
                        host.menuConfig.readMenuBottomBarBlurMode ==
                        ReadMenuBlurMode.LiquidGlass
                scope.launch {
                    readSettingsRepository.setReadMenuFloatingBottomBar(update.value)
                    if (needsBlurFallback) {
                        readSettingsRepository.setReadMenuBottomBarBlurMode(ReadMenuBlurMode.Haze)
                    }
                }
                host.updateMenuConfig {
                    it.copy(
                        readMenuFloatingBottomBar = update.value,
                        readMenuBottomBarBlurMode = if (needsBlurFallback) ReadMenuBlurMode.Haze
                        else it.readMenuBottomBarBlurMode,
                    )
                }
            }
            is ConfigUpdate.ShowMenuIcon -> {
                scope.launch {
                    readSettingsRepository.setShowMenuIcon(update.value)
                }
                host.updateMenuConfig { it.copy(showMenuIcon = update.value) }
            }
            is ConfigUpdate.MenuTopBarBlurMode -> {
                val mode = update.value.coerceIn(0, 2).let {
                    if (it == ReadMenuBlurMode.LiquidGlass) ReadMenuBlurMode.Haze else it
                }
                scope.launch {
                    readSettingsRepository.setReadMenuTopBarBlurMode(mode)
                }
                host.updateMenuConfig {
                    it.copy(readMenuTopBarBlurMode = mode)
                }
            }

            is ConfigUpdate.MenuBottomBarBlurMode -> {
                val mode = update.value.coerceIn(0, 2)
                scope.launch {
                    readSettingsRepository.setReadMenuBottomBarBlurMode(mode)
                }
                host.updateMenuConfig {
                    it.copy(readMenuBottomBarBlurMode = mode)
                }
            }

            is ConfigUpdate.MenuTopBarLiquidGlassButtons -> {
                scope.launch {
                    readSettingsRepository.setReadMenuTopBarLiquidGlassButtons(update.value)
                }
                host.updateMenuConfig {
                    it.copy(readMenuTopBarLiquidGlassButtons = update.value)
                }
            }

            is ConfigUpdate.MenuTopBarMergeButtons -> {
                scope.launch {
                    readSettingsRepository.setReadMenuTopBarMergeButtons(update.value)
                }
                host.updateMenuConfig {
                    it.copy(readMenuTopBarMergeButtons = update.value)
                }
            }

            is ConfigUpdate.MenuTopBarTitleCapsule -> {
                scope.launch {
                    readSettingsRepository.setReadMenuTopBarTitleCapsule(update.value)
                }
                host.updateMenuConfig {
                    it.copy(readMenuTopBarTitleCapsule = update.value)
                }
            }

            is ConfigUpdate.MenuBottomBarLiquidGlassButtons -> {
                scope.launch {
                    readSettingsRepository.setReadMenuBottomBarLiquidGlassButtons(update.value)
                }
                host.updateMenuConfig {
                    it.copy(readMenuBottomBarLiquidGlassButtons = update.value)
                }
            }

            is ConfigUpdate.MenuFloatingIconLiquidGlass -> {
                scope.launch {
                    readSettingsRepository.setReadMenuFloatingIconLiquidGlass(update.value)
                }
                host.updateMenuConfig {
                    it.copy(readMenuFloatingIconLiquidGlass = update.value)
                }
            }

            is ConfigUpdate.MenuTopBarBlurSelection -> {
                val mode = update.mode.coerceIn(0, 2).let {
                    if (it == ReadMenuBlurMode.LiquidGlass) ReadMenuBlurMode.Haze else it
                }
                val style = update.style.coerceIn(0, 1)
                scope.launch {
                    readSettingsRepository.setReadMenuTopBarBlurMode(mode)
                    readSettingsRepository.setReadMenuTopBarBlurStyle(style)
                }
                host.updateMenuConfig {
                    it.copy(
                        readMenuTopBarBlurMode = mode,
                        readMenuTopBarBlurStyle = style,
                    )
                }
            }

            is ConfigUpdate.MenuBottomBarBlurStyle -> {
                val style = update.value.coerceIn(0, 1)
                scope.launch {
                    readSettingsRepository.setReadMenuBottomBarBlurStyle(style)
                }
                host.updateMenuConfig {
                    it.copy(readMenuBottomBarBlurStyle = style)
                }
            }
            is ConfigUpdate.MenuBlurRadius -> {
                scope.launch {
                    readSettingsRepository.setReadMenuBlurRadius(update.value)
                }
                host.updateMenuConfig { it.copy(readMenuBlurRadius = update.value) }
            }
            is ConfigUpdate.MenuBlurAlpha -> {
                scope.launch {
                    readSettingsRepository.setReadMenuBlurAlpha(update.value)
                }
                host.updateMenuConfig { it.copy(readMenuBlurAlpha = update.value) }
            }
            is ConfigUpdate.MenuBlurColor -> {
                scope.launch {
                    readSettingsRepository.setReadMenuBlurColor(update.color)
                }
                host.updateMenuConfig { it.copy(readMenuBlurColor = update.color) }
            }
            is ConfigUpdate.MenuBlurColorNight -> {
                scope.launch {
                    readSettingsRepository.setReadMenuBlurColorNight(update.color)
                }
                host.updateMenuConfig { it.copy(readMenuBlurColorNight = update.color) }
            }
            is ConfigUpdate.MenuPaletteStyle -> {
                scope.launch {
                    readSettingsRepository.setReadMenuPaletteStyle(update.value)
                }
                host.updateMenuConfig { it.copy(readMenuPaletteStyle = update.value) }
            }
            is ConfigUpdate.MenuLensRadius -> {
                scope.launch {
                    readSettingsRepository.setReadMenuLensRadius(update.value)
                }
                host.updateMenuConfig { it.copy(readMenuLensRadius = update.value) }
            }
            is ConfigUpdate.MenuCustomIcon -> {
                val icons = ReadBookConfig.readMenuCustomIcons.toMutableMap()
                if (update.path.isBlank()) {
                    icons.remove(update.id)?.let { path ->
                        runCatching { java.io.File(path).delete() }
                    }
                } else {
                    icons[update.id] = update.path
                }
                scope.launch {
                    readSettingsRepository.setReadMenuCustomIcons(
                        ReadBookConfig.encodeReadMenuCustomIcons(icons)
                    )
                }
                host.updateMenuConfig { it.copy(readMenuCustomIcons = icons.toImmutableMap()) }
            }
            is ConfigUpdate.TitleBarCustomIcon -> {
                val icons = ReadBookConfig.titleBarCustomIcons.toMutableMap()
                if (update.path.isBlank()) {
                    icons.remove(update.id)?.let { path ->
                        runCatching { java.io.File(path).delete() }
                    }
                } else {
                    icons[update.id] = update.path
                }
                scope.launch {
                    readSettingsRepository.setTitleBarCustomIcons(
                        ReadBookConfig.encodeReadMenuCustomIcons(icons)
                    )
                }
                host.updateMenuConfig { it.copy(titleBarCustomIcons = icons.toImmutableMap()) }
            }
            is ConfigUpdate.TitleBarIconPosition -> {
                scope.launch {
                    readSettingsRepository.setTitleBarIconPosition(update.value)
                }
                host.updateMenuConfig { it.copy(titleBarIconPosition = update.value) }
            }
            is ConfigUpdate.ShowTitleBarIcons -> {
                scope.launch {
                    readSettingsRepository.setShowTitleBarIcons(update.value)
                }
                host.updateMenuConfig { it.copy(showTitleBarIcons = update.value) }
            }
            is ConfigUpdate.TitleBarCompact -> {
                scope.launch {
                    readSettingsRepository.update { it.copy(titleBarCompact = update.value) }
                }
                host.updateMenuConfig { it.copy(titleBarCompact = update.value) }
            }

            // --- System UI (also persists to DataStore) ---
            is ConfigUpdate.HideStatusBar -> {
                scope.launch {
                    readSettingsRepository.setHideStatusBar(update.value)
                }
            }
            is ConfigUpdate.HideNavigationBar -> {
                scope.launch {
                    readSettingsRepository.setHideNavigationBar(update.value)
                }
            }

            // --- Display toggles ---
            is ConfigUpdate.PaddingDisplayCutouts -> {
                scope.launch {
                    readSettingsRepository.setPaddingDisplayCutouts(update.value)
                }
            }
            is ConfigUpdate.TitleBarMode -> {
                scope.launch {
                    readSettingsRepository.setTitleBarMode(update.value)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            is ConfigUpdate.ReadBodyToLh -> {
                scope.launch {
                    readSettingsRepository.setReadBodyToLh(update.value)
                }
            }
            is ConfigUpdate.DefaultSourceChangeAll -> {
                scope.launch {
                    readSettingsRepository.setDefaultSourceChangeAll(update.value)
                }
            }
            is ConfigUpdate.TextFullJustify -> {
                scope.launch {
                    readSettingsRepository.setTextFullJustify(update.value)
                }
            }
            is ConfigUpdate.TextBottomJustify -> {
                scope.launch {
                    readSettingsRepository.setTextBottomJustify(update.value)
                }
            }
            is ConfigUpdate.AdaptSpecialStyle -> {
                scope.launch {
                    readSettingsRepository.setAdaptSpecialStyle(update.value)
                }
            }
            is ConfigUpdate.UseZhLayout -> {
                scope.launch {
                    readSettingsRepository.setUseZhLayout(update.value)
                }
            }
            is ConfigUpdate.ShowBrightnessView -> {
                scope.launch {
                    readSettingsRepository.setShowBrightnessView(update.value)
                }
                host.updateMenuConfig {
                    it.copy(showBrightnessView = update.value)
                }
                postEvent(PreferKey.showBrightnessView, "")
            }

            is ConfigUpdate.BrightnessVwPos -> {
                scope.launch {
                    readSettingsRepository.setBrightnessVwPos(update.value)
                }
                host.updateMenuConfig {
                    it.copy(brightnessVwPos = update.value)
                }
            }

            is ConfigUpdate.BrightnessAuto -> {
                host.updateMenuConfig {
                    it.copy(brightnessAuto = update.value)
                }
                scope.launch {
                    readSettingsRepository.setBrightnessAuto(update.value)
                }
                host.emitEffect(
                    ReadBookEffect.ToggleBrightnessAuto(
                        update.value,
                        host.menuConfig.readBrightness
                    )
                )
            }
            is ConfigUpdate.UseUnderlineGlobal -> {
                scope.launch {
                    readSettingsRepository.setUseUnderline(update.value)
                }
            }
            is ConfigUpdate.ReadSliderMode -> {
                scope.launch {
                    readSettingsRepository.setReadSliderMode(update.value)
                }
                host.updateMenuConfig {
                    it.copy(readSliderMode = update.value)
                }
            }
            is ConfigUpdate.DoubleHorizontalPage -> {
                scope.launch {
                    readSettingsRepository.setDoubleHorizontalPage(update.value)
                }
            }
            is ConfigUpdate.ProgressBarBehavior -> {
                scope.launch {
                    readSettingsRepository.setProgressBarBehavior(update.value)
                }
            }
            is ConfigUpdate.MouseWheelPage -> {
                scope.launch {
                    readSettingsRepository.setMouseWheelPage(update.value)
                }
            }
            is ConfigUpdate.VolumeKeyPage -> {
                scope.launch {
                    readSettingsRepository.setVolumeKeyPage(update.value)
                }
            }
            is ConfigUpdate.VolumeKeyPageOnPlay -> {
                scope.launch {
                    readSettingsRepository.setVolumeKeyPageOnPlay(update.value)
                }
            }
            is ConfigUpdate.KeyPageOnLongPress -> {
                scope.launch {
                    readSettingsRepository.setKeyPageOnLongPress(update.value)
                }
            }
            is ConfigUpdate.SwipeToAddBookmark -> {
                scope.launch {
                    readSettingsRepository.update { it.copy(swipeToAddBookmark = update.value) }
                }
            }
            is ConfigUpdate.BookmarkBadgeSize -> {
                scope.launch {
                    readSettingsRepository.update { it.copy(bookmarkBadgeSize = update.value) }
                    // 等写入落地再发 UpdateStyle，否则 upBookmarkBadge 读到旧尺寸
                    readSettingsRepository.preferences.first { it.bookmarkBadgeSize == update.value }
                    host.emitEffect(
                        ReadBookEffect.UpdateReadViewConfig(setOf(ConfigUpdateAction.UpdateStyle))
                    )
                }
            }
            is ConfigUpdate.SliderVibrator -> {
                scope.launch {
                    readSettingsRepository.setSliderVibrator(update.value)
                }
            }
            is ConfigUpdate.UseNewTocSheet -> {
                scope.launch {
                    readSettingsRepository.setUseNewTocSheet(update.value)
                }
            }
            is ConfigUpdate.MaxLengthWithNoToc -> {
                scope.launch {
                    readSettingsRepository.setMaxLengthWithNoToc(update.value)
                }
            }
            is ConfigUpdate.SelectVibrator -> {
                scope.launch {
                    readSettingsRepository.setSelectVibrator(update.value)
                }
            }
            is ConfigUpdate.AutoChangeSource -> {
                scope.launch {
                    readSettingsRepository.setAutoChangeSource(update.value)
                }
            }
            is ConfigUpdate.AutoSuggestDayNight -> {
                if (update.value) {
                    host.resetDayNightReminderDismissal()
                }
                scope.launch {
                    readSettingsRepository.setAutoSuggestDayNight(update.value)
                }
            }
            is ConfigUpdate.ReadingAnchorEnabled -> {
                scope.launch {
                    readSettingsRepository.setReadingAnchorEnabled(update.value)
                }
            }
            is ConfigUpdate.SelectText -> {
                scope.launch {
                    readSettingsRepository.setSelectText(update.value)
                }
                host.emitEffect(ReadBookEffect.UpTextSelectAble(update.value))
            }
            is ConfigUpdate.NoAnimScrollPage -> {
                scope.launch {
                    readSettingsRepository.setNoAnimScrollPage(update.value)
                }
                host.emitEffect(ReadBookEffect.UpPageAnim(upRecorder = false))
            }
            is ConfigUpdate.OptimizeRender -> {
                scope.launch {
                    readSettingsRepository.setOptimizeRender(update.value)
                }
            }
            is ConfigUpdate.ClickImgWay -> {
                scope.launch {
                    readSettingsRepository.setClickImgWay(update.value)
                }
            }
            is ConfigUpdate.DisableReturnKey -> {
                scope.launch {
                    readSettingsRepository.setDisableReturnKey(update.value)
                }
            }
            is ConfigUpdate.ExpandTextMenu -> {
                scope.launch {
                    readSettingsRepository.setExpandTextMenu(update.value)
                }
            }
            is ConfigUpdate.ShowSelectMenuIcon -> {
                scope.launch {
                    readSettingsRepository.setShowSelectMenuIcon(update.value)
                }
            }
            is ConfigUpdate.ShowReadTitleAddition -> {
                scope.launch {
                    readSettingsRepository.setShowReadTitleAddition(update.value)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }

            // --- Auto read ---
            is ConfigUpdate.AutoReadSpeed -> {
                scope.launch {
                    readSettingsRepository.setAutoReadSpeed(update.value)
                }
            }

            // --- Chinese converter ---
            is ConfigUpdate.ChineseConverterType -> {
                scope.launch {
                    readSettingsRepository.setChineseConverterType(update.value)
                }
            }
        }

        // 走了 gateway 的更新由 collectReadStyle 重建快照；只写 DataStore 的更新
        // 不经 gateway，需在此手工重建——且必须两份一起，
        // 否则像 ChineseConverterType 这种本就在 sheetConfig 里的项会一直显示旧值。
        if (styleMutation == null) {
            host.refreshConfigSnapshots()
        }
        if (update.actions.isNotEmpty()) {
            host.emitEffect(ReadBookEffect.UpdateReadViewConfig(update.actions))
        }
    }

    private fun ConfigUpdate.toReadStyleMutation(): ReadStyleMutation? = when (this) {
        is ConfigUpdate.TextSize -> intMutation(ReadStyleIntKey.TextSize, value)
        is ConfigUpdate.LetterSpacing -> floatMutation(ReadStyleFloatKey.LetterSpacing, value)
        is ConfigUpdate.LineSpacing -> intMutation(ReadStyleIntKey.LineSpacing, value)
        is ConfigUpdate.ParagraphSpacing -> intMutation(ReadStyleIntKey.ParagraphSpacing, value)
        is ConfigUpdate.ParagraphIndent -> stringMutation(ReadStyleStringKey.ParagraphIndent, value)
        is ConfigUpdate.TextItalic -> booleanMutation(ReadStyleBooleanKey.TextItalic, value)
        is ConfigUpdate.TextBold -> intMutation(ReadStyleIntKey.TextBold, value)
        is ConfigUpdate.TextColor -> colorMutation(ReadStyleColorKey.Text, color)
        is ConfigUpdate.TextAccentColor -> colorMutation(ReadStyleColorKey.TextAccent, color)
        is ConfigUpdate.TitleMode -> intMutation(ReadStyleIntKey.TitleMode, value)
        is ConfigUpdate.TitleBold -> intMutation(ReadStyleIntKey.TitleBold, value)
        is ConfigUpdate.TitleSegScaling -> floatMutation(ReadStyleFloatKey.TitleSegScaling, value)
        is ConfigUpdate.TitleLineSpacingExtra ->
            intMutation(ReadStyleIntKey.TitleLineSpacingExtra, value)
        is ConfigUpdate.TitleLineSpacingSub ->
            intMutation(ReadStyleIntKey.TitleLineSpacingSub, value)
        is ConfigUpdate.TitleSize -> intMutation(ReadStyleIntKey.TitleSize, value)
        is ConfigUpdate.TitleTopSpacing -> intMutation(ReadStyleIntKey.TitleTopSpacing, value)
        is ConfigUpdate.TitleBottomSpacing ->
            intMutation(ReadStyleIntKey.TitleBottomSpacing, value)
        is ConfigUpdate.TitleColor -> colorMutation(ReadStyleColorKey.Title, color)
        is ConfigUpdate.TitleColorNight -> colorMutation(ReadStyleColorKey.TitleNight, color)
        is ConfigUpdate.TitleFont -> stringMutation(ReadStyleStringKey.TitleFont, path)
        is ConfigUpdate.TitleSegType -> intMutation(ReadStyleIntKey.TitleSegType, value)
        is ConfigUpdate.TitleSegDistance -> intMutation(ReadStyleIntKey.TitleSegDistance, value)
        is ConfigUpdate.TitleSegFlag -> stringMutation(ReadStyleStringKey.TitleSegFlag, value)
        is ConfigUpdate.HeaderMode -> intMutation(ReadStyleIntKey.HeaderMode, value)
        is ConfigUpdate.FooterMode -> intMutation(ReadStyleIntKey.FooterMode, value)
        is ConfigUpdate.TipHeaderLeft -> intMutation(ReadStyleIntKey.TipHeaderLeft, value)
        is ConfigUpdate.TipHeaderMiddle -> intMutation(ReadStyleIntKey.TipHeaderMiddle, value)
        is ConfigUpdate.TipHeaderRight -> intMutation(ReadStyleIntKey.TipHeaderRight, value)
        is ConfigUpdate.TipFooterLeft -> intMutation(ReadStyleIntKey.TipFooterLeft, value)
        is ConfigUpdate.TipFooterMiddle -> intMutation(ReadStyleIntKey.TipFooterMiddle, value)
        is ConfigUpdate.TipFooterRight -> intMutation(ReadStyleIntKey.TipFooterRight, value)
        is ConfigUpdate.HeaderFont -> stringMutation(ReadStyleStringKey.HeaderFont, path)
        is ConfigUpdate.CustomTipHeaderLeft ->
            stringMutation(ReadStyleStringKey.CustomTipHeaderLeft, value)
        is ConfigUpdate.CustomTipHeaderMiddle ->
            stringMutation(ReadStyleStringKey.CustomTipHeaderMiddle, value)
        is ConfigUpdate.CustomTipHeaderRight ->
            stringMutation(ReadStyleStringKey.CustomTipHeaderRight, value)
        is ConfigUpdate.CustomTipFooterLeft ->
            stringMutation(ReadStyleStringKey.CustomTipFooterLeft, value)
        is ConfigUpdate.CustomTipFooterMiddle ->
            stringMutation(ReadStyleStringKey.CustomTipFooterMiddle, value)
        is ConfigUpdate.CustomTipFooterRight ->
            stringMutation(ReadStyleStringKey.CustomTipFooterRight, value)
        is ConfigUpdate.HeaderFontSize -> intMutation(ReadStyleIntKey.HeaderFontSize, value)
        is ConfigUpdate.FooterFont -> stringMutation(ReadStyleStringKey.FooterFont, path)
        is ConfigUpdate.FooterFontSize -> intMutation(ReadStyleIntKey.FooterFontSize, value)
        is ConfigUpdate.ApplyHeaderStyle ->
            booleanMutation(ReadStyleBooleanKey.ApplyHeaderStyle, value)
        is ConfigUpdate.TipHeaderColor -> colorMutation(ReadStyleColorKey.TipHeader, color)
        is ConfigUpdate.TipHeaderColorNight ->
            colorMutation(ReadStyleColorKey.TipHeaderNight, color)
        is ConfigUpdate.TipFooterColor -> colorMutation(ReadStyleColorKey.TipFooter, color)
        is ConfigUpdate.TipFooterColorNight ->
            colorMutation(ReadStyleColorKey.TipFooterNight, color)
        is ConfigUpdate.TipDividerColor -> colorMutation(ReadStyleColorKey.TipDivider, color)
        is ConfigUpdate.PageAnim -> intMutation(ReadStyleIntKey.PageAnim, value)
        is ConfigUpdate.TextShadow -> booleanMutation(ReadStyleBooleanKey.TextShadow, value)
        is ConfigUpdate.ShadowRadius -> floatMutation(ReadStyleFloatKey.ShadowRadius, value)
        is ConfigUpdate.ShadowDx -> floatMutation(ReadStyleFloatKey.ShadowDx, value)
        is ConfigUpdate.ShadowDy -> floatMutation(ReadStyleFloatKey.ShadowDy, value)
        is ConfigUpdate.ShadowColor -> colorMutation(ReadStyleColorKey.Shadow, color)
        is ConfigUpdate.Underline -> booleanMutation(ReadStyleBooleanKey.Underline, value)
        is ConfigUpdate.DottedLine -> booleanMutation(ReadStyleBooleanKey.DottedLine, value)
        is ConfigUpdate.UnderlineExtend ->
            booleanMutation(ReadStyleBooleanKey.UnderlineExtend, value)
        is ConfigUpdate.UnderlineHeight -> intMutation(ReadStyleIntKey.UnderlineHeight, value)
        is ConfigUpdate.UnderlinePadding -> intMutation(ReadStyleIntKey.UnderlinePadding, value)
        is ConfigUpdate.DottedBase -> floatMutation(ReadStyleFloatKey.DottedBase, value)
        is ConfigUpdate.DottedRatio -> floatMutation(ReadStyleFloatKey.DottedRatio, value)
        is ConfigUpdate.UnderlineColor -> colorMutation(ReadStyleColorKey.Underline, color)
        is ConfigUpdate.PaddingTop -> intMutation(ReadStyleIntKey.PaddingTop, value)
        is ConfigUpdate.PaddingBottom -> intMutation(ReadStyleIntKey.PaddingBottom, value)
        is ConfigUpdate.PaddingLeft -> intMutation(ReadStyleIntKey.PaddingLeft, value)
        is ConfigUpdate.PaddingRight -> intMutation(ReadStyleIntKey.PaddingRight, value)
        is ConfigUpdate.HeaderPaddingTop -> intMutation(ReadStyleIntKey.HeaderPaddingTop, value)
        is ConfigUpdate.HeaderPaddingBottom ->
            intMutation(ReadStyleIntKey.HeaderPaddingBottom, value)
        is ConfigUpdate.HeaderPaddingLeft -> intMutation(ReadStyleIntKey.HeaderPaddingLeft, value)
        is ConfigUpdate.HeaderPaddingRight ->
            intMutation(ReadStyleIntKey.HeaderPaddingRight, value)
        is ConfigUpdate.ShowHeaderLine ->
            booleanMutation(ReadStyleBooleanKey.ShowHeaderLine, value)
        is ConfigUpdate.FooterPaddingTop -> intMutation(ReadStyleIntKey.FooterPaddingTop, value)
        is ConfigUpdate.FooterPaddingBottom ->
            intMutation(ReadStyleIntKey.FooterPaddingBottom, value)
        is ConfigUpdate.FooterPaddingLeft -> intMutation(ReadStyleIntKey.FooterPaddingLeft, value)
        is ConfigUpdate.FooterPaddingRight ->
            intMutation(ReadStyleIntKey.FooterPaddingRight, value)
        is ConfigUpdate.ShowFooterLine ->
            booleanMutation(ReadStyleBooleanKey.ShowFooterLine, value)
        is ConfigUpdate.BgStr -> stringMutation(ReadStyleStringKey.BgStr, value)
        is ConfigUpdate.BgStrNight -> stringMutation(ReadStyleStringKey.BgStrNight, value)
        is ConfigUpdate.BgStrEInk -> stringMutation(ReadStyleStringKey.BgStrEInk, value)
        is ConfigUpdate.BgType -> intMutation(ReadStyleIntKey.BgType, value)
        is ConfigUpdate.BgTypeNight -> intMutation(ReadStyleIntKey.BgTypeNight, value)
        is ConfigUpdate.BgTypeEInk -> intMutation(ReadStyleIntKey.BgTypeEInk, value)
        is ConfigUpdate.BgAlpha -> intMutation(ReadStyleIntKey.BgAlpha, value)
        is ConfigUpdate.StatusIconDark ->
            booleanMutation(ReadStyleBooleanKey.StatusIconDark, value)
        is ConfigUpdate.StyleName -> stringMutation(ReadStyleStringKey.StyleName, value)
        else -> null
    }

}

internal fun intMutation(key: ReadStyleIntKey, value: Int) =
    ReadStyleMutation.IntValue(key, value)

internal fun floatMutation(key: ReadStyleFloatKey, value: Float) =
    ReadStyleMutation.FloatValue(key, value)

internal fun booleanMutation(key: ReadStyleBooleanKey, value: Boolean) =
    ReadStyleMutation.BooleanValue(key, value)

internal fun stringMutation(key: ReadStyleStringKey, value: String) =
    ReadStyleMutation.StringValue(key, value)

internal fun colorMutation(key: ReadStyleColorKey, value: Int) =
    ReadStyleMutation.ColorValue(key, value)
