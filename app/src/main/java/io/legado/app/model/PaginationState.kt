package io.legado.app.model

import androidx.annotation.Keep

@Keep
data class PaginationState(
    val progress: Int = 0,
    val total: Int = 0,
    val isRunning: Boolean = false
)
