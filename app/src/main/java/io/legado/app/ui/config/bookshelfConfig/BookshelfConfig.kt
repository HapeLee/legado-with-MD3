package io.legado.app.ui.config.bookshelfConfig

import androidx.compose.runtime.State
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.config.prefStateDelegate
import io.legado.app.utils.postEvent

/**
 * 书架配置
 */
object BookshelfConfig {

    /**
     * 书架分组样式: 0: Tab, 1:隐藏Tab, 2:文件夹
     */
    private val _bookGroupStyle = prefStateDelegate(PreferKey.bookGroupStyle, 0) {
        postEvent(EventBus.NOTIFY_MAIN, false)
    }
    var bookGroupStyle by _bookGroupStyle
    val bookGroupStyleState: State<Int> get() = _bookGroupStyle.state

    /**
     * 书架排序方式
     */
    private val _bookshelfSort = prefStateDelegate(PreferKey.bookshelfSort, 0)
    var bookshelfSort by _bookshelfSort
    val bookshelfSortState: State<Int> get() = _bookshelfSort.state

    /**
     * 书架排序顺序
     */
    private val _bookshelfSortOrder = prefStateDelegate(PreferKey.bookshelfSortOrder, 1)
    var bookshelfSortOrder by _bookshelfSortOrder
    val bookshelfSortOrderState: State<Int> get() = _bookshelfSortOrder.state

    /**
     * 是否显示未读标志
     */
    private val _showUnread = prefStateDelegate(PreferKey.showUnread, true)
    var showUnread by _showUnread
    val showUnreadState: State<Boolean> get() = _showUnread.state

    /**
     * 是否显示新未读标志(小圆点)
     */
    private val _showUnreadNew = prefStateDelegate(PreferKey.showUnreadNew, true)
    var showUnreadNew by _showUnreadNew
    val showUnreadNewState: State<Boolean> get() = _showUnreadNew.state

    /**
     * 是否显示更新提示
     */
    private val _showTip = prefStateDelegate(PreferKey.showTip, false)
    var showTip by _showTip
    val showTipState: State<Boolean> get() = _showTip.state

    /**
     * 鏄惁鏄剧ず鍒嗙粍鍐呬功绫嶆暟閲?
     */
    private val _showBookCount = prefStateDelegate(PreferKey.showBookCount, true)
    var showBookCount by _showBookCount
    val showBookCountState: State<Boolean> get() = _showBookCount.state

    /**
     * 是否在列表中显示最后更新时间
     */
    private val _showLastUpdateTime = prefStateDelegate(PreferKey.showLastUpdateTime, false)
    var showLastUpdateTime by _showLastUpdateTime
    val showLastUpdateTimeState: State<Boolean> get() = _showLastUpdateTime.state

    /**
     * 是否在列表中显示书籍简介
     */
    private val _showBookIntro = prefStateDelegate(PreferKey.showBookIntro, false)
    var showBookIntro by _showBookIntro
    val showBookIntroState: State<Boolean> get() = _showBookIntro.state

    /**
     * 是否显示简介
     */
    private val _bookshelfShowIntro = prefStateDelegate(PreferKey.bookshelfShowIntro, true)
    var bookshelfShowIntro by _bookshelfShowIntro
    val bookshelfShowIntroState: State<Boolean> get() = _bookshelfShowIntro.state

    /**
     * 是否显示标签
     */
    private val _bookshelfShowTag = prefStateDelegate(PreferKey.bookshelfShowTag, true)
    var bookshelfShowTag by _bookshelfShowTag
    val bookshelfShowTagState: State<Boolean> get() = _bookshelfShowTag.state

    /**
     * 是否显示最新章节
     */
    private val _bookshelfShowLatestChapter = prefStateDelegate(PreferKey.bookshelfShowLatestChapter, true)
    var bookshelfShowLatestChapter by _bookshelfShowLatestChapter
    val bookshelfShowLatestChapterState: State<Boolean> get() = _bookshelfShowLatestChapter.state

    /**
     * 列表模式下简介显示行数 (0为显示全部)
     */
    private val _bookshelfIntroMaxLines = prefStateDelegate(PreferKey.bookshelfIntroMaxLines, 0)
    var bookshelfIntroMaxLines by _bookshelfIntroMaxLines
    val bookshelfIntroMaxLinesState: State<Int> get() = _bookshelfIntroMaxLines.state

    /**
     * 是否显示等待更新的书籍数量
     */
    private val _showWaitUpCount = prefStateDelegate(PreferKey.showWaitUpCount, false)
    var showWaitUpCount by _showWaitUpCount
    val showWaitUpCountState: State<Boolean> get() = _showWaitUpCount.state

    /**
     * 是否显示书架快速滚动条
     */
    private val _showBookshelfFastScroller = prefStateDelegate(PreferKey.showBookshelfFastScroller, false)
    var showBookshelfFastScroller by _showBookshelfFastScroller
    val showBookshelfFastScrollerState: State<Boolean> get() = _showBookshelfFastScroller.state

    /**
     * 是否在书架Tab处显示分类展开按钮
     */
    private val _shouldShowExpandButton = prefStateDelegate(PreferKey.shouldShowExpandButton, false)
    var shouldShowExpandButton by _shouldShowExpandButton
    val shouldShowExpandButtonState: State<Boolean> get() = _shouldShowExpandButton.state

    /**
     * 书架同时更新书籍数量限制 (0为无限制)
     */
    private val _bookshelfRefreshingLimit = prefStateDelegate(PreferKey.bookshelfRefreshingLimit, 0)
    var bookshelfRefreshingLimit by _bookshelfRefreshingLimit
    val bookshelfRefreshingLimitState: State<Int> get() = _bookshelfRefreshingLimit.state

    /**
     * 竖屏书架布局模式: 0:列表, 1:网格
     */
    private val _bookshelfLayoutModePortrait = prefStateDelegate(PreferKey.bookshelfLayoutModePortrait, 1)
    var bookshelfLayoutModePortrait by _bookshelfLayoutModePortrait
    val bookshelfLayoutModePortraitState: State<Int> get() = _bookshelfLayoutModePortrait.state

    /**
     * 竖屏书架网格列数
     */
    private val _bookshelfLayoutGridPortrait = prefStateDelegate(PreferKey.bookshelfLayoutGridPortrait, 3)
    var bookshelfLayoutGridPortrait by _bookshelfLayoutGridPortrait
    val bookshelfLayoutGridPortraitState: State<Int> get() = _bookshelfLayoutGridPortrait.state

    /**
     * 横屏书架布局模式: 0:列表, 1:网格
     */
    private val _bookshelfLayoutModeLandscape = prefStateDelegate(PreferKey.bookshelfLayoutModeLandscape, 1)
    var bookshelfLayoutModeLandscape by _bookshelfLayoutModeLandscape
    val bookshelfLayoutModeLandscapeState: State<Int> get() = _bookshelfLayoutModeLandscape.state

    /**
     * 横屏书架网格列数
     */
    private val _bookshelfLayoutGridLandscape = prefStateDelegate(PreferKey.bookshelfLayoutGridLandscape, 7)
    var bookshelfLayoutGridLandscape by _bookshelfLayoutGridLandscape
    val bookshelfLayoutGridLandscapeState: State<Int> get() = _bookshelfLayoutGridLandscape.state

    /**
     * 竖屏书架列表列数
     */
    private val _bookshelfLayoutListPortrait = prefStateDelegate(PreferKey.bookshelfLayoutListPortrait, 1)
    var bookshelfLayoutListPortrait by _bookshelfLayoutListPortrait
    val bookshelfLayoutListPortraitState: State<Int> get() = _bookshelfLayoutListPortrait.state

    /**
     * 横屏书架列表列数
     */
    private val _bookshelfLayoutListLandscape = prefStateDelegate(PreferKey.bookshelfLayoutListLandscape, 1)
    var bookshelfLayoutListLandscape by _bookshelfLayoutListLandscape
    val bookshelfLayoutListLandscapeState: State<Int> get() = _bookshelfLayoutListLandscape.state

    /**
     * 文件夹竖屏布局模式: 0:列表, 1:网格
     */
    private val _bookshelfFolderLayoutModePortrait = prefStateDelegate(PreferKey.bookshelfFolderLayoutModePortrait, 1)
    var bookshelfFolderLayoutModePortrait by _bookshelfFolderLayoutModePortrait
    val bookshelfFolderLayoutModePortraitState: State<Int> get() = _bookshelfFolderLayoutModePortrait.state

    /**
     * 文件夹竖屏网格列数
     */
    private val _bookshelfFolderLayoutGridPortrait = prefStateDelegate(PreferKey.bookshelfFolderLayoutGridPortrait, 3)
    var bookshelfFolderLayoutGridPortrait by _bookshelfFolderLayoutGridPortrait
    val bookshelfFolderLayoutGridPortraitState: State<Int> get() = _bookshelfFolderLayoutGridPortrait.state

    /**
     * 文件夹横屏布局模式: 0:列表, 1:网格
     */
    private val _bookshelfFolderLayoutModeLandscape = prefStateDelegate(PreferKey.bookshelfFolderLayoutModeLandscape, 1)
    var bookshelfFolderLayoutModeLandscape by _bookshelfFolderLayoutModeLandscape
    val bookshelfFolderLayoutModeLandscapeState: State<Int> get() = _bookshelfFolderLayoutModeLandscape.state

    /**
     * 文件夹横屏网格列数
     */
    private val _bookshelfFolderLayoutGridLandscape = prefStateDelegate(PreferKey.bookshelfFolderLayoutGridLandscape, 7)
    var bookshelfFolderLayoutGridLandscape by _bookshelfFolderLayoutGridLandscape
    val bookshelfFolderLayoutGridLandscapeState: State<Int> get() = _bookshelfFolderLayoutGridLandscape.state

    /**
     * 文件夹竖屏列表列数
     */
    private val _bookshelfFolderLayoutListPortrait = prefStateDelegate(PreferKey.bookshelfFolderLayoutListPortrait, 1)
    var bookshelfFolderLayoutListPortrait by _bookshelfFolderLayoutListPortrait
    val bookshelfFolderLayoutListPortraitState: State<Int> get() = _bookshelfFolderLayoutListPortrait.state

    /**
     * 文件夹横屏列表列数
     */
    private val _bookshelfFolderLayoutListLandscape = prefStateDelegate(PreferKey.bookshelfFolderLayoutListLandscape, 1)
    var bookshelfFolderLayoutListLandscape by _bookshelfFolderLayoutListLandscape
    val bookshelfFolderLayoutListLandscapeState: State<Int> get() = _bookshelfFolderLayoutListLandscape.state

    /**
     * 网格模式布局样式: 0:标准, 1:紧凑, 2:仅封面
     */
    private val _bookshelfGridLayout = prefStateDelegate(PreferKey.bookshelfGridLayout, 0)
    var bookshelfGridLayout by _bookshelfGridLayout
    val bookshelfGridLayoutState: State<Int> get() = _bookshelfGridLayout.state

    /**
     * 紧凑模式
     */
    private val _bookshelfLayoutCompact = prefStateDelegate(PreferKey.bookshelfLayoutCompact, false)
    var bookshelfLayoutCompact by _bookshelfLayoutCompact
    val bookshelfLayoutCompactState: State<Boolean> get() = _bookshelfLayoutCompact.state

    /**
     * 是否显示书架分隔线
     */
    private val _bookshelfShowDivider = prefStateDelegate(PreferKey.bookshelfShowDivider, true)
    var bookshelfShowDivider by _bookshelfShowDivider
    val bookshelfShowDividerState: State<Boolean> get() = _bookshelfShowDivider.state

    /**
     * 书架标题小字体
     */
    private val _bookshelfTitleSmallFont = prefStateDelegate(PreferKey.bookshelfTitleSmallFont, false)
    var bookshelfTitleSmallFont by _bookshelfTitleSmallFont
    val bookshelfTitleSmallFontState: State<Boolean> get() = _bookshelfTitleSmallFont.state

    /**
     * 书架标题居中
     */
    private val _bookshelfTitleCenter = prefStateDelegate(PreferKey.bookshelfTitleCenter, true)
    var bookshelfTitleCenter by _bookshelfTitleCenter
    val bookshelfTitleCenterState: State<Boolean> get() = _bookshelfTitleCenter.state

    /**
     * 书架标题最大行数
     */
    private val _bookshelfTitleMaxLines = prefStateDelegate(PreferKey.bookshelfTitleMaxLines, 2)
    var bookshelfTitleMaxLines by _bookshelfTitleMaxLines
    val bookshelfTitleMaxLinesState: State<Int> get() = _bookshelfTitleMaxLines.state

    /**
     * 书架封面阴影
     */
    private val _bookshelfCoverShadow = prefStateDelegate(PreferKey.bookshelfCoverShadow, false)
    var bookshelfCoverShadow by _bookshelfCoverShadow
    val bookshelfCoverShadowState: State<Boolean> get() = _bookshelfCoverShadow.state

    /**
     * 书架卡片背景颜色
     */
    private val _bookshelfCardColor = prefStateDelegate(PreferKey.bookshelfCardColor, 0)
    var bookshelfCardColor by _bookshelfCardColor
    val bookshelfCardColorState: State<Int> get() = _bookshelfCardColor.state

    /**
     * 书架卡片背景颜色 (夜间)
     */
    private val _bookshelfCardColorDark = prefStateDelegate(PreferKey.bookshelfCardColorDark, 0)
    var bookshelfCardColorDark by _bookshelfCardColorDark
    val bookshelfCardColorDarkState: State<Int> get() = _bookshelfCardColorDark.state

    /**
     * 文件夹在列表模式下的样式: 0: 默认, 2: 横排封面
     */
    private val _bookshelfGroupListStyle = prefStateDelegate(PreferKey.bookshelfGroupListStyle, 0)
    var bookshelfGroupListStyle by _bookshelfGroupListStyle
    val bookshelfGroupListStyleState: State<Int> get() = _bookshelfGroupListStyle.state

    /**
     * 文件夹在列表模式下横排封面的数量
     */
    private val _bookshelfGroupCoverCount = prefStateDelegate(PreferKey.bookshelfGroupCoverCount, 4)
    var bookshelfGroupCoverCount by _bookshelfGroupCoverCount
    val bookshelfGroupCoverCountState: State<Int> get() = _bookshelfGroupCoverCount.state

    /**
     * 书架搜索按钮是否直接跳转搜索页
     */
    private val _bookshelfSearchActionDirectToSearch = prefStateDelegate(
        PreferKey.bookshelfSearchActionDirectToSearch,
        true
    )
    var bookshelfSearchActionDirectToSearch by _bookshelfSearchActionDirectToSearch
    val bookshelfSearchActionDirectToSearchState: State<Boolean> get() = _bookshelfSearchActionDirectToSearch.state

    /**
     * 启动时自动刷新书架
     */
    private val _autoRefreshBook = prefStateDelegate(PreferKey.autoRefresh, false)
    var autoRefreshBook by _autoRefreshBook
    val autoRefreshBookState: State<Boolean> get() = _autoRefreshBook.state

    /**
     * 保存上次停留的分组Id
     */
    private val _saveTabPosition = prefStateDelegate(PreferKey.saveTabPosition, BookGroup.IdAll)
    var saveTabPosition by _saveTabPosition
    val saveTabPositionState: State<Long> get() = _saveTabPosition.state

}
