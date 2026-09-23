package io.legado.app.platform

import io.legado.app.feature.settings.coverconfig.CoverRulePlatform
import io.legado.app.feature.settings.coverconfig.CoverRuleSpec
import io.legado.app.help.DefaultData
import io.legado.app.model.BookCover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * M5-12a：`CoverRulePlatform` 的 Android 实现。
 *
 * 迁移前这些调用在 `:app` 的 `CoverConfigViewModel` 里。本片把 VM 搬进
 * `:feature:settings` 后，**读写**留在宿主侧 —— `BookCover`（178 行，深绑
 * `android.graphics.Bitmap` / `Drawable` / `appCtx`）与 `DefaultData`（`appCtx.assets`
 * 读 `defaultData/coverRule.json`）都进不了共享层。
 *
 * 与契约的约定一致：只传 [CoverRuleSpec] 原始值，`BookCover.CoverRule` 这个嵌套类型
 * 留在 `:app`（详见 `CoverRulePlatform` 的 KDoc）。
 *
 * 四个方法都在 `Dispatchers.IO`（迁移前 VM 里显式写了 `Dispatchers.IO`，本片把它下沉到这里）。
 */
class AndroidCoverRulePlatform : CoverRulePlatform {

    override suspend fun current(): CoverRuleSpec = withContext(Dispatchers.IO) {
        BookCover.getCoverRule().toSpec()
    }

    override suspend fun default(): CoverRuleSpec = withContext(Dispatchers.IO) {
        DefaultData.coverRule.toSpec()
    }

    override suspend fun save(spec: CoverRuleSpec) {
        withContext(Dispatchers.IO) {
            BookCover.saveCoverRule(BookCover.CoverRule(spec.enabled, spec.searchUrl, spec.expression))
        }
    }

    override suspend fun delete() {
        withContext(Dispatchers.IO) {
            BookCover.delCoverRule()
        }
    }

    /** `CoverRule` 的字段是 `enable`（不是 `enabled`）—— 与构造时的参数名不同，别写错。 */
    private fun BookCover.CoverRule.toSpec(): CoverRuleSpec = CoverRuleSpec(
        enabled = enable,
        searchUrl = searchUrl,
        expression = coverRule,
    )
}
