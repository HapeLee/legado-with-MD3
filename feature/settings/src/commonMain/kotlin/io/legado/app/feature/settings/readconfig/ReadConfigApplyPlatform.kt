package io.legado.app.feature.settings.readconfig

/**
 * M5-11a：把「改完阅读设置后要去通知正在运行的阅读器」这一组动作收成的窄契约
 * （与 M5-7 的 `DownloadCachePlatform` 同一形态：**feature 模块自己定义窄契约，Android 实现
 * 留在 `:app`，Koin 注入**）。
 *
 * 迁移前 `ApplyReadSettingUseCase` 直接调四个 `:app` 符号：
 * `ReadConfigUpdateBus`（`ui.book.read`）、`ConfigUpdateAction`（`ui.book.read` 的 sealed
 * interface，被 10 个 `:app` 文件使用）、`ReadBook`（`model`，2024 行）、
 * `utils.postEvent`。
 *
 * ⚠️ **刻意没有**把 `ConfigUpdateAction` 搬进共享层：它是**阅读器**的类型（阅读器收集并分发这些
 * 动作），设置页只是**投递方**。搬过来会让共享层反向依赖阅读器的概念 —— 与既有判据
 * 「契约发 Effect、宿主执行平台动作」相反。所以本契约把每个动作组命名成**一个方法**，
 * 由宿主那侧的实现去拼具体的 `ConfigUpdateAction` 集合 / 调 `ReadBook`。
 *
 * 七个方法与迁移前的调用一一对应（顺序即原 `when` 的分支顺序）：
 *
 * | 方法 | 迁移前 |
 * |---|---|
 * | [updateSystemUiAndStyle] | `ReadConfigUpdateBus.post({UpdateSystemUi, UpdateStyle})` |
 * | [updateActionBar] | `postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)` |
 * | [reloadContent] | `ReadBook.loadContent(false)` |
 * | [updateSeekBar] | `postEvent(EventBus.UP_SEEK_BAR, true)` |
 * | [updatePageSlopSquare] | `ReadConfigUpdateBus.post({UpdatePageSlopSquare})` |
 * | [updatePageAnim] | `ReadBook.renderCallBack?.upPageAnim([animate])` |
 * | [invalidateTextPage] | `ReadConfigUpdateBus.post({InvalidateTextPage})` |
 *
 * 全部**同步**方法：迁移前这些调用就是同步的（`loadContent` / `upPageAnim` / `tryEmit`），
 * 本片不改这一性质。
 */
interface ReadConfigApplyPlatform {
    fun updateSystemUiAndStyle()

    fun updateActionBar()

    fun reloadContent()

    fun updateSeekBar()

    fun updatePageSlopSquare()

    /** [animate] 对应 `renderCallBack.upPageAnim(upRecorder)` —— 迁移前无参调用即 `false`。 */
    fun updatePageAnim(animate: Boolean)

    fun invalidateTextPage()
}
