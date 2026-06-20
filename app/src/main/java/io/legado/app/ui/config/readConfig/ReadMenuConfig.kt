package io.legado.app.ui.config.readConfig

import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.prefDelegate

/**
 * 菜单/标题栏/进度条相关配置
 *
 * 被 ReadBookMenuBar、标题栏、进度条读取。
 */
object ReadMenuConfig {

    var titleBarMode by prefDelegate(
        PreferKey.titleBarMode,
        "1"
    )

    var menuAlpha by prefDelegate(
        PreferKey.menuAlpha,
        100
    )

    var readBarStyleFollowPage by prefDelegate(
        PreferKey.readBarStyleFollowPage,
        false
    )

    var readBarStyle by prefDelegate(
        PreferKey.readBarStyle,
        0
    )

    var progressBarBehavior by prefDelegate(
        PreferKey.progressBarBehavior,
        "page"
    )

    var expandTextMenu by prefDelegate(
        PreferKey.expandTextMenu,
        false
    )

    var showSelectMenuIcon by prefDelegate(
        PreferKey.showSelectMenuIcon,
        true
    )

    var textSelectMenuFilter by prefDelegate(
        PreferKey.textSelectMenuFilter,
        ""
    )

    var showReadTitleAddition by prefDelegate(
        PreferKey.showReadTitleAddition,
        true
    )

    var showMenuIcon by prefDelegate(
        PreferKey.showMenuIcon,
        true
    )

    // --- 点击区域配置 ---

    var clickActionTL by prefDelegate(
        PreferKey.clickActionTL,
        2
    )

    var clickActionTC by prefDelegate(
        PreferKey.clickActionTC,
        2
    )

    var clickActionTR by prefDelegate(
        PreferKey.clickActionTR,
        1
    )

    var clickActionML by prefDelegate(
        PreferKey.clickActionML,
        2
    )

    var clickActionMC by prefDelegate(
        PreferKey.clickActionMC,
        0
    )

    var clickActionMR by prefDelegate(
        PreferKey.clickActionMR,
        1
    )

    var clickActionBL by prefDelegate(
        PreferKey.clickActionBL,
        2
    )

    var clickActionBC by prefDelegate(
        PreferKey.clickActionBC,
        1
    )

    var clickActionBR by prefDelegate(
        PreferKey.clickActionBR,
        1
    )

    fun hasMenuClickArea(): Boolean {
        return clickActionTL * clickActionTC * clickActionTR *
                clickActionML * clickActionMC * clickActionMR *
                clickActionBL * clickActionBC * clickActionBR == 0
    }

    fun detectClickArea() {
        if (!hasMenuClickArea()) {
            clickActionMC = 0
        }
    }
}
