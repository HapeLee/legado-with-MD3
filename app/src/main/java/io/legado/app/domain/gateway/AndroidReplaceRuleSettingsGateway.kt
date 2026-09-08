package io.legado.app.domain.gateway

import android.content.Context
import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString

private const val DEFAULT_SORT_MODE = "desc"

/**
 * [ReplaceRuleSettingsGateway] 的 Android 实现，落点仍是 `PreferKey.replaceSortMode`。
 *
 * 注意 `PreferKey.replaceSortMode` 的**常量值是 `"desc"`**——作为偏好存储的 key，
 * 这看起来是当初把 key 与默认值写反了。此处**不做修正**：改 key 会让老用户的
 * 排序设置静默丢失，属于需要单独决策的行为变更。默认值同样沿用 `"desc"`，
 * 保证迁移前后读写语义完全一致。
 */
class AndroidReplaceRuleSettingsGateway(
    private val context: Context,
) : ReplaceRuleSettingsGateway {

    override fun getSortMode(): String {
        return context.getPrefString(PreferKey.replaceSortMode, DEFAULT_SORT_MODE)
            ?: DEFAULT_SORT_MODE
    }

    override suspend fun setSortMode(mode: String) {
        context.putPrefString(PreferKey.replaceSortMode, mode)
    }
}
