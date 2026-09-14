package io.legado.app.feature.replacerules

import kotlin.uuid.Uuid

/**
 * 替换规则编辑页的入参契约。
 *
 * 这里刻意不实现 `NavKey`：路由 key 由 host 层的 `MainRouteReplaceEdit` 承载并由
 * `MainNavGraph` 映射过来，Feature 侧因此既不依赖 navigation3，也不反向依赖 `ui.main`。
 */
data class ReplaceEditRoute(
    val id: Long = -1,
    val pattern: String? = null,
    val isRegex: Boolean = false,
    val scope: String? = null,
    val isScopeTitle: Boolean = false,
    val isScopeContent: Boolean = false,
    val sessionId: String = Uuid.random().toString()
)
