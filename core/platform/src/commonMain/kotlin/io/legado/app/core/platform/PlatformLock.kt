package io.legado.app.core.platform

/**
 * 互斥原语（M2-4a）：在 [lock] 上串行执行 [block] 并返回其结果。
 *
 * 共享层需要显式互斥的地方（目前只有 [AppLogStore] 的环形缓冲：日志会被任意线程写，
 * 而 `add` / `removeLastOrNull` / `clear` 对同一个 `ArrayList` 并发操作会真的损坏数据），
 * 但 `kotlin.jvm.Synchronized` / `synchronized` 是 JVM-only、`kotlinx.coroutines` 的
 * `Mutex` 又是挂起语义、用不上。所以按本模块既有做法（[RuleDataStorage]、[SymmetricCrypto]）
 * 把这一件平台无能为力的事收成原语，逻辑仍留在共享层一份。
 *
 * `internal`：它是实现细节，不是给 `:app` / Feature 用的公共能力。
 */
internal expect fun <T> synchronizedOn(lock: Any, block: () -> T): T
