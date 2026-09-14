package io.legado.app.host.desktop

import io.legado.app.data.repository.UploadRepository

/**
 * [UploadRepository] 的 desktop 实现：**显式不可用**。
 *
 * Android 实现是 `DirectLinkUploadRepository`（依赖 `help.DirectLinkUpload` 的 okhttp 上传
 * 与配置存储）。desktop 侧没有这套配置与网络栈，因此这里**不做静默空实现**——
 * 按 AGENTS.md「平台能力不可用时必须显式建模为 capability/unsupported，不得用静默空实现
 * 伪造跨平台支持」，调用即抛 [UnsupportedOperationException]，并说明缺什么。
 *
 * 这样用户在 desktop 上点「上传」看到的是一条明确的能力缺口，而不是「上传成功但什么都没发生」。
 *
 * 已知后果：`DictRuleScreen` 的「上传选中规则」在 desktop 上会走这条异常路径。M1-4 的
 * 主路径（列表渲染 + 增删改 + 导入导出）不经过它；把上传接进 desktop 需要先给
 * `:core:platform` 的 `HttpClient` 补 desktop 实现，属独立切片。
 */
object DesktopUploadRepository : UploadRepository {

    override suspend fun upload(
        fileName: String,
        file: Any,
        contentType: String
    ): String = throw UnsupportedOperationException(
        "desktop host 未实现直链上传（file=$fileName, contentType=$contentType）。" +
            "需要 :core:platform 的 HttpClient 契约先有 desktop 实现，并用它替换 DirectLinkUpload。"
    )
}
