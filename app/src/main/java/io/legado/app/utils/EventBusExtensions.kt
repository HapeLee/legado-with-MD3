@file:Suppress("unused")

package io.legado.app.utils

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.legado.app.utils.eventBus.AppEventBus
import kotlinx.coroutines.launch

/**
 * 事件总线封装。底层由 [AppEventBus]（Flow）承载，不再依赖 Android-only 的 LiveEventBus。
 *
 * 订阅刻意**不**用 `repeatOnLifecycle(STARTED)`：原 `App.kt` 配置
 * `LiveEventBus.config().lifecycleObserverAlwaysActive(true)`，即宿主未处于 STARTED 时
 * 也要收事件。这里用 `lifecycleScope.launch` 保持等价（DESTROY 时随作用域取消）。
 */

inline fun <reified EVENT> postEvent(tag: String, event: EVENT) {
    AppEventBus.post(tag, event)
}

inline fun <reified EVENT> AppCompatActivity.observeEvent(
    vararg tags: String,
    noinline observer: (EVENT) -> Unit,
) {
    tags.forEach { tag ->
        lifecycleScope.launch {
            AppEventBus.observe<EVENT>(tag).collect { observer(it) }
        }
    }
}

inline fun <reified EVENT> AppCompatActivity.observeEventSticky(
    vararg tags: String,
    noinline observer: (EVENT) -> Unit,
) {
    tags.forEach { tag ->
        lifecycleScope.launch {
            AppEventBus.observeSticky<EVENT>(tag).collect { observer(it) }
        }
    }
}

inline fun <reified EVENT> Fragment.observeEvent(
    vararg tags: String,
    noinline observer: (EVENT) -> Unit,
) {
    tags.forEach { tag ->
        viewLifecycleOwner.lifecycleScope.launch {
            AppEventBus.observe<EVENT>(tag).collect { observer(it) }
        }
    }
}

inline fun <reified EVENT> Fragment.observeEventSticky(
    vararg tags: String,
    noinline observer: (EVENT) -> Unit,
) {
    tags.forEach { tag ->
        viewLifecycleOwner.lifecycleScope.launch {
            AppEventBus.observeSticky<EVENT>(tag).collect { observer(it) }
        }
    }
}

inline fun <reified EVENT> LifecycleService.observeEvent(
    vararg tags: String,
    noinline observer: (EVENT) -> Unit,
) {
    tags.forEach { tag ->
        lifecycleScope.launch {
            AppEventBus.observe<EVENT>(tag).collect { observer(it) }
        }
    }
}

inline fun <reified EVENT> LifecycleService.observeEventSticky(
    vararg tags: String,
    noinline observer: (EVENT) -> Unit,
) {
    tags.forEach { tag ->
        lifecycleScope.launch {
            AppEventBus.observeSticky<EVENT>(tag).collect { observer(it) }
        }
    }
}
