package io.legado.app.ui.main

import java.util.concurrent.atomic.AtomicBoolean

internal class ProcessStartupUpdateCheckGate {

    private val consumed = AtomicBoolean(false)

    fun consume(enabled: Boolean): Boolean = !consumed.getAndSet(true) && enabled
}
