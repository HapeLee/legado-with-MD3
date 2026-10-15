package io.legado.app.help.readaloud.cast

import org.junit.Assert.assertEquals
import org.junit.Test

/** [CastVoicePicker] 的补音规则：候选只取本书用得最少的那一批，再从候选里随机取一个。 */
class CastVoicePickerTest {

    private val pool = listOf("v1", "v2", "v3")

    private fun pick(usage: Map<String, Int>) =
        CastVoicePicker.leastUsedVoices(pool, usage).random()

    @Test
    fun `unused voices are the only candidates while any is left`() {
        assertEquals(
            setOf("v2", "v3"),
            CastVoicePicker.leastUsedVoices(pool, mapOf("v1" to 1)).toSet(),
        )
        assertEquals(pool.toSet(), CastVoicePicker.leastUsedVoices(pool, emptyMap()).toSet())
    }

    @Test
    fun `repeats only after every voice has been used once`() {
        // v1 已用两次，不在最少的那一批里
        assertEquals(
            setOf("v2", "v3"),
            CastVoicePicker.leastUsedVoices(pool, mapOf("v1" to 2, "v2" to 1, "v3" to 1)).toSet(),
        )
    }

    /** 三个角色进同一个空池：整池用遍之前不许撞音色，所以取法必须是随机的而不是固定一个。 */
    @Test
    fun `three characters in a fresh pool never share a voice`() {
        repeat(200) {
            val usage = HashMap<String, Int>()
            val picks = (1..3).map {
                pick(usage).also { voiceId -> usage[voiceId] = (usage[voiceId] ?: 0) + 1 }
            }
            assertEquals(pool.toSet(), picks.toSet())
        }
    }
}
