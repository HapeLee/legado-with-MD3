package io.legado.app.data.repository

/**
 * 规则/书籍直链上传契约。
 *
 * 接口放 commonMain 的原因：7 个规则列表 VM（`:core:rules` 的 `BaseRuleViewModel` 子类）
 * 与书籍/书源/书架等多处 VM 都要注入它；只要接口留在 `:app`，这些 VM 就无法离开 `:app`。
 * Android 实现 [DirectLinkUploadRepository] 仍留在 `:app`（依赖 `help.DirectLinkUpload`
 * 的 okhttp 上传与配置存储），由 Koin 绑定。
 *
 * `file: Any` 沿用迁移前的签名（实际传 JSON 字符串）；不改动以免波及 12 个调用点。
 */
interface UploadRepository {
    suspend fun upload(
        fileName: String,
        file: Any,
        contentType: String
    ): String
}
