package io.legado.app.domain.model.manga

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

@Serializable
enum class MangaCloudOcrProvider { BAIDU, TENCENT, PADDLE, PADDLE_SERVER }

/** Global credentials never enter per-book settings, anchors or translated-image caches. */
@Stable
@Serializable
data class MangaCloudOcrConfig(
    val apiKey: String = "",
    val secretKey: String = "",
    val endpoint: String = "",
    val region: String = "ap-guangzhou",
    val accurate: Boolean = true,
    val model: String = "PP-OCRv6",
    val authScheme: String = "Bearer",
) {
    /** Apply only user-edited fields to the latest shared configuration. */
    fun mergeDraft(base: MangaCloudOcrConfig, draft: MangaCloudOcrConfig): MangaCloudOcrConfig = copy(
        apiKey = if (draft.apiKey != base.apiKey) draft.apiKey else apiKey,
        secretKey = if (draft.secretKey != base.secretKey) draft.secretKey else secretKey,
        endpoint = if (draft.endpoint != base.endpoint) draft.endpoint else endpoint,
        region = if (draft.region != base.region) draft.region else region,
        accurate = if (draft.accurate != base.accurate) draft.accurate else accurate,
        model = if (draft.model != base.model) draft.model else model,
        authScheme = if (draft.authScheme != base.authScheme) draft.authScheme else authScheme,
    )

    override fun toString() = "MangaCloudOcrConfig(credentials=redacted)"
}

/** The host requests access; retry remains an explicit user action. */
class MangaCloudOcrPermissionRequiredException(message: String) : IllegalStateException(message)
