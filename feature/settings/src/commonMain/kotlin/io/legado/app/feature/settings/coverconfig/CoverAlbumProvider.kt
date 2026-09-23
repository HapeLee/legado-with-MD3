package io.legado.app.feature.settings.coverconfig

import kotlinx.coroutines.flow.Flow

/**
 * 封面图库的全部能力（列表 / 选中 / 增删改 / 加图）。
 *
 * ⚠️ **为什么是契约而不是把 `CoverAlbumUseCase` 搬进共享层**：那个 use case 看着住在
 * `io.legado.app.domain.usecase`（一个**长得像共享层**的包），实际定义在 `:app`；
 * 它依赖的 `CoverAlbumGateway` / `CoverAlbumImageInput` 同样在 `:app`，而
 * `CoverAlbumImageInput` 用了 **`java.io.InputStream`**（JVM 专用）⇒ 整条链要上提就得先
 * 为「图片输入」定一个跨端表示，那是**另一个独立的设计决定**（与 M5-11b 定 BackHandler /
 * KeyEvent 同性质）。本模块不顺手把它定了 ⇒ 链留在 `:app`，由 `AndroidCoverAlbumProvider`
 * 用现有 `CoverAlbumUseCase` 实现。
 *
 * 只暴露 **UI 类型**（[CoverAlbumSelectionUiState]），`CoverAlbum` 等领域模型不外泄。
 *
 * 演进：
 * - M5-12a 只收了封面设置页需要的三个能力（[selection] / [selectAlbum]）；
 * - M5-14a 把**图库管理页**也迁进共享层，于是补上 [createAlbum] / [renameAlbum] /
 *   [deleteAlbum] / [addImages] / [removeImage] —— **扩同一个契约而不是另造一个**：
 *   这两页同属「封面图库」这一个功能面，宿主侧也只需要一个实现（与
 *   `DownloadCachePlatform` / `ReadConfigApplyPlatform` 的粒度判据一致）。
 *
 * ⚠️ [addImages] 收的是**选中的 URI 串**，不是图片输入对象 —— 把 URI 变成
 * `CoverAlbumImageInput`（含 `openStream`）这件事需要 `ContentResolver`，属宿主职责。
 */
interface CoverAlbumProvider {
    /** 图库列表与当前选中项。 */
    val selection: Flow<CoverAlbumSelectionUiState>

    suspend fun selectAlbum(albumId: String?)

    /** @return 新建相册的 id（管理页建完即进入编辑态，需要它）。 */
    suspend fun createAlbum(name: String): String

    suspend fun renameAlbum(albumId: String, name: String)

    suspend fun deleteAlbum(albumId: String)

    /** [uriStrings] 是宿主从系统选择器拿到的结果。 */
    suspend fun addImages(albumId: String, isDark: Boolean, uriStrings: List<String>)

    suspend fun removeImage(albumId: String, isDark: Boolean, imageId: String)
}
