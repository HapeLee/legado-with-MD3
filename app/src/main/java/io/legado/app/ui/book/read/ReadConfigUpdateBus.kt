package io.legado.app.ui.book.read

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Typed replacement for the legacy `EventBus.UP_CONFIG` integer-code channel.
 * Producers post [ConfigUpdateAction] sets; [ReadBookViewModel] collects and
 * forwards them to the ReadView as [ReadBookEffect.UpdateReadViewConfig].
 */
object ReadConfigUpdateBus {

    private val _events = MutableSharedFlow<Set<ConfigUpdateAction>>(extraBufferCapacity = 64)
    val events: SharedFlow<Set<ConfigUpdateAction>> = _events

    fun post(actions: Set<ConfigUpdateAction>) {
        if (actions.isNotEmpty()) {
            _events.tryEmit(actions)
        }
    }
}
