package io.legado.app.feature.settings.coverconfig

import kotlinx.coroutines.flow.Flow

/**
 * M5-12a：封面设置页需要的「封面图库」那点东西（当前列表 + 选中项 + 切换选中）。
 *
 * ⚠️ **为什么是契约而不是把 `CoverAlbumUseCase` 搬进共享层**：那个 use case 看着住在
 * `io.legado.app.domain.usecase`（一个**长得像共享层**的包），实际定义在 `:app`；
 * 它依赖的 `CoverAlbumGateway` / `CoverAlbumImageInput` 同样在 `:app`，而
 * `CoverAlbumImageInput` 用了 **`java.io.InputStream`**（JVM 专用）⇒ 整条链要上提就得先
 * 为「图片输入」定一个跨端表示，那是**另一个独立的设计决定**（与 M5-11b 定 BackHandler /
 * KeyEvent 同性质）。
 *
 * 本片不该顺手把它定了 ⇒ 改为只把**共享层真正需要的那三个能力**收成契约，
 * 由 `:app` 的 `AndroidCoverAlbumProvider` 用现有 `CoverAlbumUseCase` 实现。
 * 链本身（gateway / repository / InputStream）留在 `:app`，不动。
 *
 * 只暴露 **UI 类型**（[CoverAlbumSelectionUiState]），`CoverAlbum` 等领域模型不外泄。
 */
interface CoverAlbumProvider {
    /** 图库列表与当前选中项。 */
    val selection: Flow<CoverAlbumSelectionUiState>

    suspend fun selectAlbum(albumId: String?)
}
