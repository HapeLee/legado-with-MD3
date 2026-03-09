package io.legado.app.ui.config.readConfig

import androidx.lifecycle.ViewModel
import io.legado.app.constant.EventBus
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.utils.postEvent

class ReadConfigViewModel : ViewModel() {

    fun updateHideStatusBar(hide: Boolean) {
        ReadConfig.hideStatusBar = hide
        ReadBookConfig.hideStatusBar = hide
        postEvent(EventBus.UP_CONFIG, arrayListOf(0, 2))
    }

    fun updateHideNavigationBar(hide: Boolean) {
        ReadConfig.hideNavigationBar = hide
        ReadBookConfig.hideNavigationBar = hide
        postEvent(EventBus.UP_CONFIG, arrayListOf(0, 2))
    }

    fun upLayout() {
        ChapterProvider.upLayout()
        ReadBook.loadContent(false)
    }

    fun upStyle() {
        ChapterProvider.upStyle()
        ReadBook.callBack?.upPageAnim(true)
        ReadBook.loadContent(false)
    }

    fun upPageAnim() {
        ReadBook.callBack?.upPageAnim()
    }

    fun updateMenuAlpha(alpha: Int) {
        ReadConfig.menuAlpha = alpha
        postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
    }

    fun updatePageTouchSlop(slop: Int) {
        ReadConfig.pageTouchSlop = slop
        postEvent(EventBus.UP_CONFIG, arrayListOf(4))
    }

    fun updateReadSliderMode(mode: String) {
        ReadConfig.readSliderMode = mode
        postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
    }

    fun updateProgressBarBehavior(behavior: String) {
        ReadConfig.progressBarBehavior = behavior
        postEvent(EventBus.UP_SEEK_BAR, true)
    }

    fun updateShowReadTitleAddition(show: Boolean) {
        ReadConfig.showReadTitleAddition = show
        postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
    }
}
