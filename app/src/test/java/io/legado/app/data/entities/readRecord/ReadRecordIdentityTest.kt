package io.legado.app.data.entities.readRecord

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 阅读记录身份规范化、会话指纹和归属决定的边界测试。 */
class ReadRecordIdentityTest {
    @Test
    fun normalizesAllWhitespaceAndTrimsEdges() {
        assertEquals("示例 书", ReadRecordIdentity.bookName(" \u2003示例\u00a0 书\t"))
        assertEquals("作者 名", ReadRecordIdentity.author("作者\n\r\t名"))
    }

    @Test
    fun emptyAuthorRemainsDistinctFromRealAuthor() {
        assertEquals("书\u0000", ReadRecordIdentity.key("书", ""))
        assertTrue(ReadRecordIdentity.key("书", "") != ReadRecordIdentity.key("书", "未知作者"))
    }

    @Test
    fun sessionFingerprintIsStableAndIncludesWords() {
        val first = ReadRecordSession(deviceId = "device", bookName = "书", bookAuthor = "作者", startTime = 10L, endTime = 20L, words = 3L)
        val same = ReadRecordSession(deviceId = "device", bookName = "书", bookAuthor = "作者", startTime = 10L, endTime = 20L, words = 3L)
        val differentWords = same.copy(words = 4L)
        assertEquals(first.stableFingerprint, same.stableFingerprint)
        assertTrue(first.stableFingerprint != differentWords.stableFingerprint)
    }

    @Test
    fun aliasDecisionCanBeReplacedAndRevoked() {
        val key = ReadRecordAliasDecision.key("书 ", " 作者")
        val encoded = ReadRecordAliasDecision.encode(key, ReadRecordAliasAction.MERGE)
        assertEquals(ReadRecordAliasAction.MERGE, ReadRecordAliasDecision.decode(encoded, key))
        val replaced = ReadRecordAliasDecision.removeForKey(encoded, key) +
            if (ReadRecordAliasDecision.removeForKey(encoded, key).isBlank()) "" else "\n"
        assertTrue(ReadRecordAliasDecision.decode(replaced, key) == null)
    }
}
