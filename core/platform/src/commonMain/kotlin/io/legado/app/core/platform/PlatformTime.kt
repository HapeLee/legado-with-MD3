package io.legado.app.core.platform

/**
 * 平台时间原语。actual 在 androidMain / desktopMain 委托 `System.currentTimeMillis()` /
 * `System.nanoTime()`。保留为 expect/actual 而非普通接口 + DI：系统时间是每个 target
 * 必须静态提供的语言/系统原语（见 AGENTS.md KMP 纪律：expect/actual 只用于平台原语）。
 */
expect fun systemTimeMillis(): Long

expect fun systemTimeNanos(): Long
