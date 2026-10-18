package io.legado.app.help.readaloud.cast

import android.app.Application
import android.content.Context
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookVoiceBindingEntity
import io.legado.app.data.entities.CastCharacter
import io.legado.app.data.entities.ChapterRoleAssignment
import io.legado.app.support.InMemoryAppDatabaseFixture
import io.legado.app.utils.GSON
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CastMemoryPersistenceTest {
    private lateinit var fixture: InMemoryAppDatabaseFixture
    private val db get() = fixture.database
    private val bookUrl = "memory-test-book"

    @Before fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        app.injectAsAppCtx()
        app.getSharedPreferences("ai_cast", Context.MODE_PRIVATE).edit().clear().commit()
        fixture = InMemoryAppDatabaseFixture(app)
    }

    @After fun tearDown() {
        fixture.close()
    }

    @Test fun `AI echo never acquires ownership of user aliases and summary`() = runBlocking {
        seed()
        AiCastPresetStore.setMemory(bookUrl, "张三｜小张｜AI 旧简介", fromAi = true)
        val edited = profile().copy(
            aliasesJson = GSON.toJson(listOf("小张", "用户称呼")),
            summary = "用户简介",
        )
        db.bookKnowledgeDao.upsertCharacterProfile(edited)
        CastMemoryMirror.applyProfileToMemory(bookUrl, edited)
        AiCastPresetStore.setMemory(bookUrl, "张三｜小张、用户称呼｜用户简介", fromAi = true)
        AiCastPresetStore.setMemory(bookUrl, "张三｜新称呼｜AI 新简介", fromAi = true)

        assertTrue(profile().aliasNames().contains("用户称呼"))
        assertEquals("用户简介", profile().summary)
    }

    @Test fun `legacy memory is not evidence that user fields belong to AI`() = runBlocking {
        seed()
        val edited = profile().copy(aliasesJson = "[\"用户称呼\"]", summary = "用户简介")
        db.bookKnowledgeDao.upsertCharacterProfile(edited)
        AiCastPresetStore.setMemory(bookUrl, "张三｜用户称呼｜用户简介")
        RuntimeEnvironment.getApplication().getSharedPreferences("ai_cast", Context.MODE_PRIVATE)
            .edit().putString("aiMemory|$bookUrl", "张三｜用户称呼｜用户简介").commit()

        AiCastPresetStore.setMemory(bookUrl, "张三｜新称呼｜新简介", fromAi = true)

        assertTrue(profile().aliasNames().contains("用户称呼"))
        assertEquals("用户简介", profile().summary)
    }

    @Test fun `AI authored identity still updates and removes obsolete aliases`() = runBlocking {
        seed()
        AiCastPresetStore.setMemory(bookUrl, "张三｜旧称呼｜旧简介", fromAi = true)
        AiCastPresetStore.setMemory(bookUrl, "张三｜新称呼｜新简介", fromAi = true)

        assertEquals(listOf("新称呼"), profile().aliasNames())
        assertEquals("新简介", profile().summary)
    }

    @Test fun `pruning preserves profile edited only in its summary`() = runBlocking {
        seed()
        AiCastPresetStore.setMemory(bookUrl, "张三｜｜AI 简介", fromAi = true)
        val edited = profile().copy(summary = "用户简介")
        db.bookKnowledgeDao.upsertCharacterProfile(edited)
        CastMemoryMirror.applyProfileToMemory(bookUrl, edited)

        assertEquals(0, CastMemoryMirror.pruneStaleProfiles(bookUrl, "王五｜｜另一个人"))
        assertEquals(BookCharacterProfile.STATUS_ACTIVE, profile().status)
        assertNotNull(db.castCharacterDao.getById("profile"))
    }

    @Test fun `pruning preserves effect bubble sort and locked voice configurations`() = runBlocking {
        val customRows = listOf(
            CastCharacter("effect", bookUrl, "变声角色", "男青年", voiceEffect = "混响"),
            CastCharacter("bubble", bookUrl, "气泡角色", "男青年", bubbleRuleJson = "{\"bgColor\":123}"),
            CastCharacter("sort", bookUrl, "排序角色", "男青年", sortOrder = 1),
            CastCharacter("voice", bookUrl, "音色角色", "男青年", voiceId = "chosen-voice"),
        )
        customRows.forEach { row ->
            db.castCharacterDao.insertIgnore(row)
            db.bookKnowledgeDao.upsertCharacterProfile(
                BookCharacterProfile(id = row.id, bookUrl = bookUrl, name = row.name),
            )
            AiCastPresetStore.recordAiCreatedProfile(bookUrl, row.id)
        }
        AiCastPresetStore.setMemory(
            bookUrl, customRows.joinToString("\n") { "${it.name}｜｜AI 简介" }, fromAi = true,
        )
        db.readAloudVoiceDao.upsertBinding(
            BookVoiceBindingEntity(bookUrl, "character", "voice", "chosen-voice", locked = true),
        )

        assertEquals(0, CastMemoryMirror.pruneStaleProfiles(bookUrl, "王五｜｜另一个人"))
        customRows.forEach { assertEquals(it, db.castCharacterDao.getById(it.id)) }
    }

    @Test fun `pruning still removes an unused AI identity but keeps its archive`() = runBlocking {
        seed()
        AiCastPresetStore.setMemory(bookUrl, "张三｜小张｜AI 简介", fromAi = true)

        assertEquals(1, CastMemoryMirror.pruneStaleProfiles(bookUrl, "王五｜｜另一个人"))
        assertEquals(BookCharacterProfile.STATUS_DISABLED, profile().status)
        assertEquals("AI 简介", profile().summary)
        assertNull(db.castCharacterDao.getById("profile"))
    }

    @Test fun `pruning preserves user aliases even when AI echoes them`() = runBlocking {
        seed()
        db.bookKnowledgeDao.upsertCharacterProfile(profile().copy(aliasesJson = "[\"用户称呼\"]"))
        AiCastPresetStore.setMemory(bookUrl, "张三｜用户称呼｜AI 简介", fromAi = true)

        assertEquals(0, CastMemoryMirror.pruneStaleProfiles(bookUrl, "王五｜｜另一个人"))
        assertEquals(BookCharacterProfile.STATUS_ACTIVE, profile().status)
    }

    @Test fun `pruning preserves legacy profiles with unknown ownership`() = runBlocking {
        seed(aiCreated = false)
        assertEquals(0, CastMemoryMirror.pruneStaleProfiles(bookUrl, "王五｜｜另一个人"))
        assertNotNull(db.castCharacterDao.getById("profile"))
    }

    @Test fun `AI writing identity fields does not acquire cleanup rights over a legacy profile`() = runBlocking {
        seed(aiCreated = false)
        AiCastPresetStore.setMemory(bookUrl, "张三｜小张｜AI 简介", fromAi = true)

        assertEquals(0, CastMemoryMirror.pruneStaleProfiles(bookUrl, "王五｜｜另一个人"))
        assertNotNull(db.castCharacterDao.getById("profile"))
    }

    @Test fun `user pool edit stays protected after rename and later AI updates`() = runBlocking {
        seed()
        AiCastPresetStore.setMemory(bookUrl, "张三｜小张｜AI 简介", fromAi = true)
        db.bookKnowledgeDao.upsertCharacterProfile(profile().copy(name = "张先生", voiceAgeBand = "男老年"))
        val row = requireNotNull(db.castCharacterDao.getById("profile"))
        db.castCharacterDao.update(row.copy(name = "张先生", poolLabel = "男老年"))
        CastMemoryMirror.syncCharacterRow(bookUrl, "张三", "张先生", "男老年")
        AiCastPresetStore.setMemory(bookUrl, "张先生｜小张｜更新的 AI 简介", fromAi = true)

        assertEquals(0, CastMemoryMirror.pruneStaleProfiles(bookUrl, "王五｜｜另一个人"))
        assertEquals("男老年", profile().voiceAgeBand)
    }

    @Test fun `spoken profile is protected when assignment uses the other id system`() = runBlocking {
        seed()
        AiCastPresetStore.setMemory(bookUrl, "张三｜小张｜AI 简介", fromAi = true)
        db.chapterRoleAssignmentDao.upsert(ChapterRoleAssignment(bookUrl, 0, 0, "other-id", "张三"))

        assertEquals(0, CastMemoryMirror.pruneStaleProfiles(bookUrl, "王五｜｜另一个人"))
        assertNotNull(db.castCharacterDao.getById("profile"))
    }

    private suspend fun seed(aiCreated: Boolean = true) {
        db.bookKnowledgeDao.upsertCharacterProfile(
            BookCharacterProfile(id = "profile", bookUrl = bookUrl, name = "张三"),
        )
        db.castCharacterDao.insertIgnore(CastCharacter("profile", bookUrl, "张三", "男青年"))
        if (aiCreated) AiCastPresetStore.recordAiCreatedProfile(bookUrl, "profile")
    }

    private suspend fun profile() = requireNotNull(
        db.bookKnowledgeDao.getCharacterProfile(bookUrl, "profile"),
    )
}
