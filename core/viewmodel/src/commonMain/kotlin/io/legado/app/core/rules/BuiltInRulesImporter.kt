package io.legado.app.core.rules

/**
 * 「导入内置（出厂）规则」的平台能力契约。
 *
 * 抽出来的原因：TXT 目录规则页的「导入内置规则」原先由 ViewModel 直接调用
 * `help.DefaultData.importDefaultTocRules()`，而 `DefaultData` 是 `:app` 独有的
 * 133 行大杂烩——依赖 `appDb`、`splitties appCtx`、`LocalConfig`/`ThemeConfigStore`/
 * `ReadBookConfig`、`model.BookCover`、`java.io.File` 与 `runBlocking`，既无法下沉，
 * 也让 ViewModel 无法随规则页一起进入 `:feature:*` 模块。
 *
 * 契约只暴露「导入某一类内置规则」这一个动作，不暴露 `Context` / `File` / assets
 * （见 AGENTS.md「新的共享领域契约不得暴露 Context/File/Uri」）。Android 实现留在 `:app`
 * （`AndroidBuiltInRulesImporter`），由 Koin 注入。
 *
 * 语义必须与迁移前保持一致：`DefaultData.importDefaultTocRules()` 是
 * 「先删默认规则，再插入内置规则」，实现不得改成「追加」或「按名字去重」。
 *
 * 包位置（M1-3b）：原在 `io.legado.app.base.rules`，与 [RuleTransferPlatform] 一起搬到
 * `io.legado.app.core.rules`，理由见后者 KDoc。
 */
interface BuiltInRulesImporter {

    /** 导入内置 TXT 目录规则（覆盖式：先清默认项再写入）。 */
    suspend fun importTxtTocRules()
}
