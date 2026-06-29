package io.legado.app.ui.main.homepage

import io.legado.app.ui.config.prefStateDelegate

/**
 * 首页配置 — 仅保留无法入库或无需入库的顶层 UI 设置。
 * 模块级配置（开关、排序、自定义集）已迁移到 homepage_modules 表。
 */
object HomepageConfig {

    /**
     * 首页书源隐藏
     */
    private val _homepageSourceHidden = prefStateDelegate("homepageSourceHidden", "")
    var homepageSourceHidden by _homepageSourceHidden

}
