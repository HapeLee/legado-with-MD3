package io.legado.app.platform

import io.legado.app.constant.EventBus
import io.legado.app.feature.settings.readconfig.ReadConfigApplyPlatform
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ConfigUpdateAction
import io.legado.app.ui.book.read.ReadConfigUpdateBus
import io.legado.app.utils.postEvent

/**
 * M5-11a：`ReadConfigApplyPlatform` 的 Android 实现。
 *
 * 迁移前这些调用散在 `:app` 的 `ApplyReadSettingUseCase` 里；本片把那张 `when` 映射表搬进
 * `:feature:settings`（可测），而**执行**留在宿主侧 —— 因为 `ReadBook`（2024 行）、
 * `ReadConfigUpdateBus`、`ConfigUpdateAction` 全都住在 `:app`，且 `ConfigUpdateAction` 是
 * **阅读器**的类型（阅读器收集并分发它，设置页只是投递方）⇒ 不该进共享层。
 * 详见 `ReadConfigApplyPlatform` 的 KDoc。
 *
 * 每个方法与迁移前的调用**逐条对应**（注释里写明原文），顺序也保持一致。
 */
class AndroidReadConfigApplyPlatform : ReadConfigApplyPlatform {

    // `ReadConfigUpdateBus.post({UpdateSystemUi, UpdateStyle})`
    override fun updateSystemUiAndStyle() {
        ReadConfigUpdateBus.post(
            setOf(ConfigUpdateAction.UpdateSystemUi, ConfigUpdateAction.UpdateStyle)
        )
    }

    // `postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)`
    override fun updateActionBar() {
        postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
    }

    // `ReadBook.loadContent(false)`
    override fun reloadContent() {
        ReadBook.loadContent(false)
    }

    // `postEvent(EventBus.UP_SEEK_BAR, true)`
    override fun updateSeekBar() {
        postEvent(EventBus.UP_SEEK_BAR, true)
    }

    // `ReadConfigUpdateBus.post({UpdatePageSlopSquare})`
    override fun updatePageSlopSquare() {
        ReadConfigUpdateBus.post(setOf(ConfigUpdateAction.UpdatePageSlopSquare))
    }

    // `ReadBook.renderCallBack?.upPageAnim(...)` —— 迁移前「无参」即默认值 `false`
    override fun updatePageAnim(animate: Boolean) {
        ReadBook.renderCallBack?.upPageAnim(animate)
    }

    // `ReadConfigUpdateBus.post({InvalidateTextPage})`
    override fun invalidateTextPage() {
        ReadConfigUpdateBus.post(setOf(ConfigUpdateAction.InvalidateTextPage))
    }
}
