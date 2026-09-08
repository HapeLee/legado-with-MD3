package io.legado.app.base.rules

/**
 * 规则导入/导出的平台能力契约。
 *
 * 抽出来的原因：`BaseRuleViewModel` 原先直接依赖 `okHttpClient` / `AppConst` /
 * `ContentResolver` / `Uri` / `utils.{isAbsUrl,isUri,readText}`，这些依赖既让基类无法在
 * 单测里替换，也让它没法随规则基类一起下沉到独立模块。这里把「怎么取到导入文本」与
 * 「怎么写出导出文本」收敛成两个方法，Android 实现留在 `:app`
 * （[AndroidRuleTransferPlatform]），由 Koin 注入。
 *
 * 契约不暴露 `Context` / `File` / `Uri`（见 AGENTS.md「新的共享领域契约不得暴露
 * Context/File/Uri」）：目标位置用 [String] 表达。
 *
 * 注意：**不是**用来替换 okhttp 的通用 HTTP 契约。`readImportSource` 的语义必须与
 * 迁移前逐字一致（`decompressed().text("utf-8")`、`#requestWithoutUA` 特例），
 * 换实现即改变规则导入行为。
 */
interface RuleTransferPlatform {

    /**
     * 把导入文本解析为 JSON 文本：
     * - 绝对 URL：HTTP GET；URL 以 `#requestWithoutUA` 结尾时去掉该后缀并带 `UA: null` 头；
     * - URI（如 `content://`）：读取该资源文本；
     * - 其余：原样返回。
     */
    suspend fun readImportSource(text: String): String

    /**
     * 把 [content] 写入 [targetUri] 指向的位置（通常是 SAF 返回的 `content://`）。
     *
     * 目标无法打开时**不抛异常、静默跳过**——迁移前 `openOutputStream` 返回 null 即如此，
     * 调用方随后仍会报告「导出成功」。改这条语义等于改用户可见行为。
     */
    suspend fun writeExport(targetUri: String, content: String)
}
