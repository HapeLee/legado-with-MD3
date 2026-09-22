package io.legado.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateAccessTest {

    @Test
    fun `book marked private alone is private`() {
        assertTrue(
            isPrivateBook(
                bookUrl = "url-1",
                group = 0L,
                privateBookUrls = setOf("url-1"),
                privateGroupMask = 0L
            )
        )
    }

    @Test
    fun `book in private group is private`() {
        // groupId 用位掩码存放：0b100 表示第 3 个分组
        assertTrue(
            isPrivateBook(
                bookUrl = "url-1",
                group = 0b100L,
                privateBookUrls = emptySet(),
                privateGroupMask = 0b010L or 0b100L
            )
        )
    }

    @Test
    fun `book outside private groups is public`() {
        assertFalse(
            isPrivateBook(
                bookUrl = "url-1",
                group = 0b001L,
                privateBookUrls = setOf("url-2"),
                privateGroupMask = 0b100L
            )
        )
    }

    @Test
    fun `no private source at all keeps everything public`() {
        assertFalse(
            isPrivateBook(
                bookUrl = "url-1",
                group = 0b111L,
                privateBookUrls = emptySet(),
                privateGroupMask = 0L
            )
        )
    }

    private fun state(
        isUnlocked: Boolean = false,
        grantedBookUrls: Set<String> = emptySet(),
        grantedGroupIds: Set<Long> = emptySet(),
    ) = PrivateAccessState(
        isUnlocked = isUnlocked,
        grantedBookUrls = grantedBookUrls,
        grantedGroupIds = grantedGroupIds,
    )

    @Test
    fun `app session unlock grants every target`() {
        val state = state(isUnlocked = true)
        assertTrue(state.isTargetGranted("url-1", 0b100L))
        assertTrue(state.isTargetGranted(null, 0b001L))
    }

    @Test
    fun `per target unlock grants only the granted book`() {
        val state = state(grantedBookUrls = setOf("url-1"))
        assertTrue(state.isTargetGranted("url-1", 0L))
        assertFalse(state.isTargetGranted("url-2", 0L))
    }

    @Test
    fun `granted group covers the books inside it`() {
        // 进了已获准的私密分组后，组内书籍不应再逐本验证
        val state = state(grantedGroupIds = setOf(0b010L))
        assertTrue(state.isTargetGranted("url-in-group", 0b010L))
        assertFalse(state.isTargetGranted("url-other-group", 0b100L))
    }

    @Test
    fun `union covers both sources at once`() {
        assertTrue(
            isPrivateBook(
                bookUrl = "url-1",
                group = 0b010L,
                privateBookUrls = setOf("url-1"),
                privateGroupMask = 0b010L
            )
        )
    }
}
