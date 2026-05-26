package io.legado.app.domain.model

enum class MatchMode(val value: Int) {
    DEFAULT(0),
    EXACT(1);

    companion object {
        fun of(value: Int) = entries.getOrElse(value) { DEFAULT }
    }
}
