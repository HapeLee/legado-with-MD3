package io.legado.app.ui.config.readConfig

import io.legado.app.constant.EventBus
import io.legado.app.domain.gateway.ReadSettingsUpdate
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.utils.postEvent

/** Applies runtime reader changes after a setting has entered the effective settings snapshot. */
class ApplyReadSettingUseCase {

    operator fun invoke(update: ReadSettingsUpdate) {
        when (update) {
            is ReadSettingsUpdate.HideStatusBar,
            is ReadSettingsUpdate.HideNavigationBar -> {
                postEvent(EventBus.UP_CONFIG, arrayListOf(0, 2))
            }

            is ReadSettingsUpdate.ReadMenuBlurAlpha,
            is ReadSettingsUpdate.ReadSliderMode,
            is ReadSettingsUpdate.ShowReadTitleAddition,
            is ReadSettingsUpdate.ShowMenuIcon -> {
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }

            is ReadSettingsUpdate.TextFullJustify,
            is ReadSettingsUpdate.TextBottomJustify,
            is ReadSettingsUpdate.UseZhLayout,
            is ReadSettingsUpdate.DoubleHorizontalPage -> updateLayout()

            is ReadSettingsUpdate.ProgressBarBehavior -> {
                postEvent(EventBus.UP_SEEK_BAR, true)
            }

            is ReadSettingsUpdate.PageTouchSlop -> {
                postEvent(EventBus.UP_CONFIG, arrayListOf(4))
            }

            is ReadSettingsUpdate.NoAnimScrollPage -> ReadBook.callBack?.upPageAnim()
            is ReadSettingsUpdate.OptimizeRender -> updateStyle()
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
