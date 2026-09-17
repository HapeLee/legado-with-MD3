package io.legado.app.feature.about

/**
 * 读取宿主**内置文本文件**（M5-1c）。
 *
 * 「关于」页的三份文档（`privacyPolicy.md` / `LICENSE.md` / `disclaimer.md`）来自 Android 的
 * `assets`：迁移前是 `String(context.assets.open(fileName).readBytes())`。`assets` 是 Android
 * 的概念，desktop 没有等价物（资源要打进 jar，且必须先确定与 assets 一致的解码口径），
 * 因此抽成契约而不是在共享层假装能读。
 *
 * 只有「读一个自带的 UTF-8 文本」这一件事，所以不做成通用的文件读取：通用读取是
 * `:core:platform` 的 `FileSystem`，而它的语义是「按路径读写」，与「按打包名读内置资源」
 * 不是一回事。
 *
 * 失败语义：文件不存在/读取失败返回 `null`。迁移前这里没做错误处理（`assets.open` 抛
 * `IOException` 会走 `execute{}.onError` 从而**静默什么都不发生**）——保持「拿不到就不弹层」
 * 的行为，但用 `null` 显式表达，不再靠异常吞掉。
 */
interface BundledTextReader {

    suspend fun readText(fileName: String): String?
}
