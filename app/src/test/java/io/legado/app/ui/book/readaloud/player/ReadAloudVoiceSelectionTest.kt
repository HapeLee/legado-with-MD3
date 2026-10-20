package io.legado.app.ui.book.readaloud.player

import io.legado.app.domain.model.readaloud.ReadAloudEngineSelection
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.utils.GSON
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 音色选中判定。
 *
 * 回归背景：HTTP TTS 的 `ttsEngine` 只保存引擎 id，扁平音色列表用引擎 id 判定选中，
 * 导致同一引擎下的所有音色一起显示为已选。
 */
class ReadAloudVoiceSelectionTest {

    private fun httpVoice(speakerId: String, name: String = speakerId.ifBlank { "引擎默认" }) =
        ReadAloudVoice(
            id = "http-${speakerId.ifBlank { "default" }}",
            engineType = ReadAloudVoice.ENGINE_HTTP,
            engineId = "42",
            speakerId = speakerId,
            displayName = name,
        )

    private fun cloudVoice(speakerId: String) = ReadAloudVoice(
        id = "cloud-${speakerId.ifBlank { "default" }}",
        engineType = ReadAloudVoice.ENGINE_CLOUD,
        engineId = "engine-1",
        speakerId = speakerId,
        displayName = "云端音色$speakerId",
    )

    private fun systemVoice(engineId: String) = ReadAloudVoice(
        id = "system-${engineId.ifBlank { "default" }}",
        engineType = ReadAloudVoice.ENGINE_SYSTEM,
        engineId = engineId,
        speakerId = "",
        displayName = "系统音色",
    )

    @Test
    fun `http engine without binding highlights only the engine row`() {
        val engineRow = httpVoice(speakerId = "", name = "源 A")
        val speakerA = httpVoice(speakerId = "tone-1")
        val speakerB = httpVoice(speakerId = "tone-2")

        assertTrue(isReadAloudVoiceSelected(engineRow, null, "42"))
        assertFalse(isReadAloudVoiceSelected(speakerA, null, "42"))
        assertFalse(isReadAloudVoiceSelected(speakerB, null, "42"))
    }

    @Test
    fun `http engine value does not match other engines`() {
        assertFalse(isReadAloudVoiceSelected(httpVoice(speakerId = ""), null, "7"))
        assertFalse(isReadAloudVoiceSelected(httpVoice(speakerId = ""), null, null))
    }

    @Test
    fun `narrator binding wins over the engine value`() {
        val engineRow = httpVoice(speakerId = "", name = "源 A")
        val speaker = httpVoice(speakerId = "tone-1")

        assertFalse(isReadAloudVoiceSelected(engineRow, speaker.id, "42"))
        assertTrue(isReadAloudVoiceSelected(speaker, speaker.id, "42"))
    }

    @Test
    fun `cloud engine default selection does not highlight every speaker`() {
        val defaultSelection = GSON.toJson(
            ReadAloudEngineSelection(
                engineType = ReadAloudVoice.ENGINE_CLOUD,
                engineId = "engine-1",
                speakerId = "",
                displayName = "默认",
            )
        )

        assertTrue(isReadAloudVoiceSelected(cloudVoice(""), null, defaultSelection))
        assertFalse(isReadAloudVoiceSelected(cloudVoice("speaker-a"), null, defaultSelection))
        assertFalse(isReadAloudVoiceSelected(cloudVoice("speaker-b"), null, defaultSelection))
    }

    @Test
    fun `cloud speaker selection matches only that speaker`() {
        val selection = GSON.toJson(
            ReadAloudEngineSelection(
                engineType = ReadAloudVoice.ENGINE_CLOUD,
                engineId = "engine-1",
                speakerId = "speaker-a",
                displayName = "音色 A",
            )
        )

        assertTrue(isReadAloudVoiceSelected(cloudVoice("speaker-a"), null, selection))
        assertFalse(isReadAloudVoiceSelected(cloudVoice("speaker-b"), null, selection))
        assertFalse(isReadAloudVoiceSelected(cloudVoice(""), null, selection))
    }

    @Test
    fun `system default row only matches when no engine is configured`() {
        assertTrue(isReadAloudVoiceSelected(systemVoice(""), null, null))

        val selection = GSON.toJson(SelectItem("系统音色", "com.example.tts"))
        assertTrue(isReadAloudVoiceSelected(systemVoice("com.example.tts"), null, selection))
        assertFalse(isReadAloudVoiceSelected(systemVoice(""), null, selection))
    }
}
