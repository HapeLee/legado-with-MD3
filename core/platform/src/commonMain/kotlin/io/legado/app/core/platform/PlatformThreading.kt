package io.legado.app.core.platform

/**
 * 线程原语（M2-4b）：当前线程是否是平台的 UI/主线程。
 *
 * 背景：`BaseSource.refreshExplore` / `refreshJSLib` 有「必须在后台线程调用」的守卫
 * （对齐迁移前 `:app` `utils.HandlerUtils.isMainThread`）。这两个方法在 `:core:data` 的
 * commonMain 里，读不到 Android 的 `Looper`，所以按本模块既有做法（[RuleDataStorage]、
 * [PlatformTime]）做成 expect/actual 原语，而不是再挂一个 Provider。
 *
 * androidMain：`Looper.getMainLooper().thread`；desktopMain：AWT 事件分发线程。
 */
expect fun isOnMainThread(): Boolean
