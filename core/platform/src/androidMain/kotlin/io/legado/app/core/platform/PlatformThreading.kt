package io.legado.app.core.platform

import android.os.Looper

/**
 * [isOnMainThread] 的 Android 实现：与迁移前 `utils.HandlerUtils.isMainThread` 同语义——
 * 缓存主 `Looper` 的线程再与当前线程做引用比较（`Looper.getMainLooper()` 每次调用有开销）。
 */
private val mainThread: Thread by lazy { Looper.getMainLooper().thread }

actual fun isOnMainThread(): Boolean = mainThread === Thread.currentThread()
