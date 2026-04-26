package io.legado.app.ui.config.bookshelfConfig

import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.config.prefDelegate
import io.legado.app.utils.postEvent

/**
 * 书架配置
 */
object BookshelfConfig {

    /**
     * 书架分组样式: 0: Tab, 1:隐藏Tab, 2:文件夹
     */
    var bookGroupStyle by prefDelegate(PreferKey.bookGroupStyle, 0) {
        postEvent(EventBus.NOTIFY_MAIN, false)
    }

    /**
     * 书架排序方式
     */
    var bookshelfSort by prefDelegate(PreferKey.bookshelfSort, 0)

    /**
     * 书架排序顺序
     */
    var bookshelfSortOrder by prefDelegate(PreferKey.bookshelfSortOrder, 1)

    /**
     * 是否显示未读标志
     */
    var showUnread by prefDelegate(PreferKey.showUnread, true)

    /**
     * 是否显示新未读标志(小圆点)
     */
    var showUnreadNew by prefDelegate(PreferKey.showUnreadNew, true)

    /**
     * 是否显示更新提示
     */
    var showTip by prefDelegate(PreferKey.showTip, false)

    /**
     * 鏄惁鏄剧ず鍒嗙粍鍐呬功绫嶆暟閲?
     */
    var showBookCount by prefDelegate(PreferKey.showBookCount, true)

    /**
     * 是否在列表中显示最后更新时间
     */
    var showLastUpdateTime by prefDelegate(PreferKey.showLastUpdateTime, false)

    /**
     * 是否显示等待更新的书籍数量
     */
    var showWaitUpCount by prefDelegate(PreferKey.showWaitUpCount, false)

    /**
     * 是否显示书架快速滚动条
     */
    var showBookshelfFastScroller by prefDelegate(PreferKey.showBookshelfFastScroller, false)

    /**
     * 是否在书架Tab处显示分类展开按钮
     */
    var shouldShowExpandButton by prefDelegate(PreferKey.shouldShowExpandButton, false)

    /**
     * 书架同时更新书籍数量限制 (0为无限制)
     */
    var bookshelfRefreshingLimit by prefDelegate(PreferKey.bookshelfRefreshingLimit, 0)

    /**
     * 竖屏书架布局模式: 0:列表, 1:网格
     */
    var bookshelfLayoutModePortrait by prefDelegate(PreferKey.bookshelfLayoutModePortrait, 1)

    /**
     * 竖屏书架网格列数
     */
    var bookshelfLayoutGridPortrait by prefDelegate(PreferKey.bookshelfLayoutGridPortrait, 3)

    /**
     * 横屏书架布局模式: 0:列表, 1:网格
     */
    var bookshelfLayoutModeLandscape by prefDelegate(PreferKey.bookshelfLayoutModeLandscape, 1)

    /**
     * 横屏书架网格列数
     */
    var bookshelfLayoutGridLandscape by prefDelegate(PreferKey.bookshelfLayoutGridLandscape, 7)

    /**
     * 竖屏书架列表列数
     */
    var bookshelfLayoutListPortrait by prefDelegate(PreferKey.bookshelfLayoutListPortrait, 1)

    /**
     * 横屏书架列表列数
     */
    var bookshelfLayoutListLandscape by prefDelegate(PreferKey.bookshelfLayoutListLandscape, 1)

    /**
     * 网格模式布局样式: 0:标准, 1:紧凑, 2:仅封面
     */
    var bookshelfGridLayout by prefDelegate(PreferKey.bookshelfGridLayout, 0)

    /**
     * 紧凑模式
     */
    var bookshelfLayoutCompact by prefDelegate(PreferKey.bookshelfLayoutCompact, false)

    /**
     * 是否显示书架分隔线
     */
    var bookshelfShowDivider by prefDelegate(PreferKey.bookshelfShowDivider, true)

    /**
     * 书架标题小字体
     */
    var bookshelfTitleSmallFont by prefDelegate(PreferKey.bookshelfTitleSmallFont, false)

    /**
     * 书架标题居中
     */
    var bookshelfTitleCenter by prefDelegate(PreferKey.bookshelfTitleCenter, true)

    /**
     * 书架标题最大行数
     */
    var bookshelfTitleMaxLines by prefDelegate(PreferKey.bookshelfTitleMaxLines, 2)

    /**
     * 书架封面阴影
     */
    var bookshelfCoverShadow by prefDelegate(PreferKey.bookshelfCoverShadow, false)

    /**
     * 书架搜索按钮是否直接跳转搜索页
     */
    var bookshelfSearchActionDirectToSearch by prefDelegate(
        PreferKey.bookshelfSearchActionDirectToSearch,
        true
    )

    /**
     * 启动时自动刷新书架
     */
    var autoRefreshBook by prefDelegate(PreferKey.autoRefresh, false)

    /**
     * 保存上次停留的分组Id
     */
    var saveTabPosition by prefDelegate(PreferKey.saveTabPosition, BookGroup.IdAll)

}
