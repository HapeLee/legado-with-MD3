package io.legado.app.ui.config.readConfig

import io.legado.app.constant.EventBus
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.utils.postEvent

/** Applies runtime reader changes after a setting has entered the effective settings snapshot. */
class ApplyReadSettingUseCase {

    operator fun invoke(intent: ReadConfigIntent) {
        when (intent) {
            is ReadConfigIntent.HideStatusBarChanged,
            is ReadConfigIntent.HideNavigationBarChanged -> {
                postEvent(EventBus.UP_CONFIG, arrayListOf(0, 2))
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
                postEvent(EventBus.UP_CONFIG, arrayListOf(4))
            }

            is ReadConfigIntent.NoAnimScrollPageChanged -> ReadBook.callBack?.upPageAnim()
            is ReadConfigIntent.OptimizeRenderChanged -> updateStyle()
            else -> Unit
        }
    }

    private fun updateLayout() {
        ChapterProvider.upLayout()
        ReadBook.loadContent(false)
    }

    private fun updateStyle() {
        ChapterProvider.upStyle()
        ReadBook.callBack?.upPageAnim(true)
        ReadBook.loadContent(false)
    }
}
