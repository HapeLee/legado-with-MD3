package io.legado.app.core.platform

/**
 * 中文字符串比较原语（简体中文排序）。actual 在 androidMain 委托 ICU Collator
 * （ULocale.SIMPLIFIED_CHINESE，API 24+；旧版本回退 java.text.Collator + Locale.CHINA），
 * 在 desktopMain 委托 java.text.Collator。保留为 expect/actual 而非普通接口 + DI：
 * 中文排序是每个 target 必须静态提供的语言/系统原语（见 AGENTS.md KMP 纪律）。
 */
expect fun cnCompare(a: String, b: String): Int
