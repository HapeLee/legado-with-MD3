package io.legado.app.ui.config.readConfig

import io.legado.app.constant.EventBus
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ConfigUpdateAction
import io.legado.app.ui.book.read.ReadConfigUpdateBus
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.utils.postEvent

/** Applies runtime reader changes after a setting has entered the effective settings snapshot. */
class ApplyReadSettingUseCase {

    operator fun invoke(intent: ReadConfigIntent) {
        when (intent) {
            is ReadConfigIntent.HideStatusBarChanged,
            is ReadConfigIntent.HideNavigationBarChanged -> {
                ReadConfigUpdateBus.post(
                    setOf(ConfigUpdateAction.UpdateSystemUi, ConfigUpdateAction.UpdateStyle)
                )
            }

            is ReadConfigIntent.ReadMenuBlurAlphaChanged,
            is ReadConfigIntent.ReadSliderModeChanged,
            is ReadConfigIntent.ShowReadTitleAdditionChanged,
            is ReadConfigIntent.ShowMenuIconChanged -> {
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }

            is ReadConfigIntent.TextFullJustifyChanged,
            is ReadConfigIntent.TextBottomJustifyChanged,
            is ReadConfigIntent.UseZhLayoutChanged,
            is ReadConfigIntent.DoubleHorizontalPageChanged -> updateLayout()

            is ReadConfigIntent.ProgressBarBehaviorChanged -> {
                postEvent(EventBus.UP_SEEK_BAR, true)
            }

            is ReadConfigIntent.PageTouchSlopChanged -> {
                ReadConfigUpdateBus.post(setOf(ConfigUpdateAction.UpdatePageSlopSquare))
            }

            is ReadConfigIntent.NoAnimScrollPageChanged -> ReadBook.renderCallBack?.upPageAnim()
            is ReadConfigIntent.OptimizeRenderChanged -> updateStyle()
            else -> Unit
        }
    }

    private fun updateLayout() {
        // textBottomJustify 属于 RenderStyle 快照，重排前得先重建，否则排版读到旧值
        ChapterProvider.upRenderStyle()
        ChapterProvider.upLayout()
        ReadBook.loadContent(false)
    }

    private fun updateStyle() {
        ChapterProvider.upStyle()
        ReadBook.renderCallBack?.upPageAnim(true)
        ReadBook.loadContent(false)
    }
}
