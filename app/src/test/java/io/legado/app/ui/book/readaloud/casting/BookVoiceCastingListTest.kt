package io.legado.app.ui.book.readaloud.casting

import android.app.Application
import android.content.Context
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.CastCharacter
import io.legado.app.data.entities.ChapterRoleAssignment
import io.legado.app.help.readaloud.cast.BookCastStore
import io.legado.app.support.InMemoryAppDatabaseFixture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

/**
 * 「人物配音」列表把人列出来了吗：档案与配音角色是两条记录，
 * 只按档案长列表就会在两者对不上时凭空掉人。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BookVoiceCastingListTest {
    private lateinit var fixture: InMemoryAppDatabaseFixture
    private val db get() = fixture.database
    private val bookUrl = "casting-list-book"

    @Before fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        app.injectAsAppCtx()
        app.getSharedPreferences("ai_cast", Context.MODE_PRIVATE).edit().clear().commit()
        fixture = InMemoryAppDatabaseFixture(app)
    }

    @After fun tearDown() {
        fixture.close()
    }

    /** 用户现场：新音色没进任何池，所以角色只选了音色、没选池。 */
    private suspend fun seedVoiceOnlyCharacter() {
        db.castCharacterDao.insertIgnore(
            CastCharacter("fushun", bookUrl, "福顺", poolLabel = "", voiceId = "loose-voice"),
        )
        db.bookKnowledgeDao.upsertCharacterProfile(
            BookCharacterProfile(id = "fushun", bookUrl = bookUrl, name = "福顺"),
        )
        db.chapterRoleAssignmentDao.upsert(
            ChapterRoleAssignment(bookUrl, 240, 0, "fushun", "福顺"),
        )
    }

    /**
     * 这一页会列出谁：与 `BookVoiceCastingViewModel.load()` 同一套装配口径
     * （角色行 + 启用中的档案 + [castingSources] 的并集），但不走需要 Koin 的那层缓存。
     */
    private suspend fun listedNames(): List<String> {
        val assignments = db.chapterRoleAssignmentDao.getForBook(bookUrl).groupBy { it.characterId }
        val rows = db.castCharacterDao.getByBook(bookUrl).associate { character ->
            val spoken = assignments[character.id].orEmpty()
            character.id to BookCastStore.BookCharacterRow(
                id = character.id,
                name = character.name,
                poolLabel = character.poolLabel,
                voiceId = character.voiceId,
                voiceName = "",
                voiceEffect = character.voiceEffect,
                sortOrder = character.sortOrder,
                chapterCount = spoken.map { it.chapterIndex }.distinct().size,
                lineCount = spoken.size,
                bubbleRuleJson = character.bubbleRuleJson,
            )
        }
        val active = db.bookKnowledgeDao.getCharacterProfiles(bookUrl, 500)
            .filter { it.status == BookCharacterProfile.STATUS_ACTIVE }
        val (profiles, orphanRows) = castingSources(active, rows)
        return profiles.map { it.name } + orphanRows.map { it.name }
    }

    @Test fun `a character with a voice but no pool is still listed`() = runBlocking {
        seedVoiceOnlyCharacter()
        assertEquals(listOf("福顺"), listedNames())
    }

    @Test fun `a cast row whose profile was disabled is still listed`() = runBlocking {
        seedVoiceOnlyCharacter()
        db.bookKnowledgeDao.upsertCharacterProfile(
            db.bookKnowledgeDao.getCharacterProfile(bookUrl, "fushun")!!
                .copy(status = BookCharacterProfile.STATUS_DISABLED),
        )
        val names = listedNames()
        assertTrue("配音角色行还活着，人就不能从这一页凭空消失：$names", names.contains("福顺"))
    }

    @Test fun `profile and row are not listed twice`() = runBlocking {
        seedVoiceOnlyCharacter()
        assertEquals(1, listedNames().count { it == "福顺" })
    }
}
