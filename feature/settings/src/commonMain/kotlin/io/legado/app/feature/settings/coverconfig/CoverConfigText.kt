package io.legado.app.feature.settings.coverconfig

import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.cover_rule_fields_required
import io.legado.app.feature.settings.res.restore_default
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * 封面设置页的一次性提示文案。
 *
 * 迁移前 VM 直接把 `R.string.*`（`@StringRes Int`）塞进 Effect、由页面
 * `context.toastOnUi(res)` 弹出 —— `Int` 资源 id 是 Android 概念，进不了共享层
 * ⇒ 换成枚举 + 查表。与 `feature/about` 的 `AboutMessage`、本模块
 * `backup/BackupConfigText` **同一模式、同一理由**。
 */
enum class CoverConfigToast {
    /** 已恢复默认封面规则。 */
    RestoredDefault,

    /** 搜索地址与规则表达式都不能为空。 */
    RuleFieldsRequired,
}

private fun CoverConfigToast.resource(): StringResource = when (this) {
    CoverConfigToast.RestoredDefault -> Res.string.restore_default
    CoverConfigToast.RuleFieldsRequired -> Res.string.cover_rule_fields_required
}

/**
 * 取提示文案。是 `suspend`（`getString` 需要）—— 宿主在 `LaunchedEffect` 里调，
 * 正好对应迁移前 `context.toastOnUi(res)` 所在的那个位置。
 */
suspend fun CoverConfigToast.localizedText(): String = getString(resource())
