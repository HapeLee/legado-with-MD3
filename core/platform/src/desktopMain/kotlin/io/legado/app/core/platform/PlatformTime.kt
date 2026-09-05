package io.legado.app.core.platform

actual fun systemTimeMillis(): Long = System.currentTimeMillis()

actual fun systemTimeNanos(): Long = System.nanoTime()
