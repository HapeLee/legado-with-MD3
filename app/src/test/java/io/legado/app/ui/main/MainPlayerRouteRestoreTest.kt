package io.legado.app.ui.main

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class MainPlayerRouteRestoreTest {
    @Test
    fun oldReadAloudPlayerStackStillDeserializesAfterUpgrade() {
        val saved =
            """[{"type":"io.legado.app.ui.main.MainRouteHome"},{"type":"io.legado.app.ui.main.MainRouteReadAloudPlayer"}]"""
        assertEquals(
            listOf(MainRouteHome, MainRouteReadAloudPlayer),
            Json.decodeFromString<List<MainRoute>>(saved)
        )
    }

    @Test
    fun oldAudioPlayerStackRetainsItsBookAndBookshelfFlag() {
        val saved =
            """[{"type":"io.legado.app.ui.main.MainRouteHome"},{"type":"io.legado.app.ui.main.MainRouteAudioPlay","bookUrl":"audio-a","inBookshelf":false}]"""
        assertEquals(
            listOf(MainRouteHome, MainRouteAudioPlay("audio-a", false)),
            Json.decodeFromString<List<MainRoute>>(saved)
        )
    }
}
