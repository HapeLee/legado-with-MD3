package io.legado.app.help.readaloud.cast

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookVoiceBindingEntity
import io.legado.app.data.entities.CastCharacter
import io.legado.app.domain.model.readaloud.BookVoiceBinding
import io.legado.app.feature.reader.core.cast.CastMarkers
import io.legado.app.help.book.BookHelp
import io.legado.app.help.readaloud.effect.VoiceEffectStore
import io.legado.app.model.ReadBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 书内分配表的数据出口（「人物与角色配音」页的本书配音角色一节，每本书独立）。
 *
 * 与 VoicePoolStore.assignmentRows（多角色规则页的全书清理视图）区分：
 * 本 Store 只返回当前书的角色 + 每角色已分配的章数/句数，供该页展示与编辑。
 */
object BookCastStore {

    /** 书内角色行：状态 + 分配章数 + 分配句数。 */
    data class BookCharacterRow(
        val id: String,
        val name: String,
        val poolLabel: String,
        val voiceId: String,
        val voiceName: String,
        /** 角色的全局变声器预设名（空 = 不变声），见 [updateCharacter] */
        val voiceEffect: String,
        /** 配音页拖动排序位（1 起，0 = 没排过，按角色档次默认放置）。 */
        val sortOrder: Int,
        val chapterCount: Int,
        val lineCount: Int,
    )

        suspend fun rowsForBook(bookUrl: String): List<BookCharacterRow> {
        CastSyntaxStore.current()
        // 配音角色 = 官方人物档案 + 我们这层音色/变声：进页面先把档案导进来
        CastAssignmentStore.migrateLegacyProfiles(bookUrl)
        val characters = appDb.castCharacterDao.getByBook(bookUrl)
        if (characters.isEmpty()) return emptyList()
        // 自动选音上线前建的角色这里是空的：打开分配表时按池补一次，池内无可用音色时保持原样
        val filled = characters.map { CastVoicePicker.ensureVoice(it) }
        val voices = appDb.readAloudVoiceDao.getVoices().associate { it.id to it.displayName }
        val assignments = appDb.chapterRoleAssignmentDao.getForBook(bookUrl)
            .filter { it.characterId.isNotEmpty() }
            .groupBy { it.characterId }
        return filled.map { c ->
            val rows = assignments[c.id].orEmpty()
            BookCharacterRow(
                id = c.id,
                name = c.name,
                poolLabel = c.poolLabel,
                voiceId = c.voiceId,
                voiceName = voices[c.voiceId].orEmpty(),
                voiceEffect = c.voiceEffect,
                chapterCount = rows.map { it.chapterIndex }.distinct().size,
                lineCount = rows.size,
                sortOrder = c.sortOrder,
            )
        }
    }

    /**
     * 配音页拖动排序落库：按当前显示顺序把 1..n 写回 [CastCharacter.sortOrder]。
     *
     * 整表重编号（而不是只写被拖的那一行）是因为拖完之后的相对顺序才是用户要的，
     * 零散的历史值会让没拖过的行插进已排好的顺序里。
     * 没在这份列表里的角色（拖动期间新增/删除的）清零，回到默认位置等下一次排序。
     */
    suspend fun saveCharacterOrder(bookUrl: String, characterIds: List<String>) {
        val ordered = characterIds.toSet()
        characterIds.forEachIndexed { index, id ->
            appDb.castCharacterDao.setSortOrder(id, index + 1)
        }
        appDb.castCharacterDao.getByBook(bookUrl)
            .filter { it.id !in ordered && it.sortOrder != 0 }
            .forEach { appDb.castCharacterDao.setSortOrder(it.id, 0) }
    }

    /** 行内编辑候选：音色（id、显示名、所属池）+ 同书其它角色。 */
    data class VoiceCandidate(
        val id: String,
        val displayName: String,
        val poolNames: Set<String>,
    )

    data class EditBundle(
        val pools: List<String>,
        val voices: List<VoiceCandidate>,
        val characters: List<CastAssignmentStore.CastCandidate>,
        /** 可选的变声器预设名（已启用的那些）。 */
        val effects: List<String>,
    )

    suspend fun editBundle(bookUrl: String): EditBundle {
        VoicePoolStore.ensureSeeded()
        val pools = VoicePoolStore.enabledPoolNames()
        val poolOf = VoicePoolStore.voicePoolMap()
        return EditBundle(
            pools = pools,
            voices = appDb.readAloudVoiceDao.getEnabledVoices()
                .map { VoiceCandidate(it.id, it.displayName, poolOf[it.id].orEmpty()) },
            characters = appDb.castCharacterDao.getByBook(bookUrl)
                .map { CastAssignmentStore.CastCandidate(it.id, it.name, it.poolLabel, it.voiceId, it.voiceEffect) },
            effects = VoiceEffectStore.enabledNames(),
        )
    }

    /**
     * 分配表内编辑角色状态（确认语义）：改名/换池/换音色/换变声器，同步所有引用行。
     * 名字非法或与其它角色同名同池时失败。
     *
     * [voiceEffect] 是**角色级（全局）**的变声器状态，就存 [CastCharacter.voiceEffect] 这一列：
     * 朗读侧每句都按 characterId 查它（[io.legado.app.help.readaloud.effect.VoiceEffectStore.ofCharacter]），
     * 所以这里改一次，这个角色在书里被分配到的每一句都跟着变。
     * 正文胶囊悬浮窗里那一栏想表达的是「只有这一段这么处理」，它不应该写回这一列。
     */
    suspend fun updateCharacter(
        bookUrl: String,
        characterId: String,
        name: String,
        poolLabel: String,
        voiceId: String,
        voiceEffect: String,
    ): Boolean {
        CastSyntaxStore.current()
        val trimmed = name.trim()
        if (trimmed.isEmpty() || !CastMarkers.isValidName(trimmed)) return false
        val character = appDb.castCharacterDao.getById(characterId) ?: return false
        val pool = poolLabel.trim().take(12)
        if (character.name != trimmed || character.poolLabel != pool) {
            val clash = appDb.castCharacterDao.getByName(bookUrl, trimmed)
                .any { it.id != characterId && it.poolLabel == pool }
            if (clash) return false
        }
        val updated = CastVoicePicker.ensureVoice(
            character.copy(
                name = trimmed,
                poolLabel = pool,
                voiceId = voiceId,
                voiceEffect = voiceEffect.trim().take(24),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        appDb.castCharacterDao.update(updated)
        CastProfileMirror.ensure(updated)
        if (updated.voiceEffect != character.voiceEffect) {
            // 朗读侧的快照按预设表版本失效，不改这里新变声要等重启才听得到
            VoiceEffectStore.invalidateCharacters()
        }
        appDb.chapterRoleAssignmentDao.updateForCharacter(
            bookUrl, updated.id, updated.name, updated.poolLabel, updated.updatedAt,
        )
        // 用户在这里挑的音色与配音页挑的是同一件事：写进绑定（发音链路读它），
        // 并按「手动锁定」处理，否则自动选音会把它改掉。
        if (voiceId.isNotBlank() && voiceId != character.voiceId) {
            val now = System.currentTimeMillis()
            val existing = appDb.readAloudVoiceDao.getBinding(
                bookUrl,
                BookVoiceBinding.SUBJECT_CHARACTER,
                updated.id,
            )
            appDb.readAloudVoiceDao.upsertBinding(
                existing?.copy(voiceId = voiceId, locked = true, updatedAt = now)
                    ?: BookVoiceBindingEntity(
                        bookUrl = bookUrl,
                        subjectType = BookVoiceBinding.SUBJECT_CHARACTER,
                        subjectId = updated.id,
                        voiceId = voiceId,
                        locked = true,
                        source = BookVoiceBinding.SOURCE_USER,
                        confidence = 1f,
                        createdAt = now,
                        updatedAt = now,
                    ),
            )
        }
        return true
    }

    /** 分配表内新建角色（不绑定句子，纯建档案）。 */
    suspend fun createCharacter(
        bookUrl: String,
        name: String,
        poolLabel: String,
        voiceId: String,
    ): Boolean {
        CastSyntaxStore.current()
        val trimmed = name.trim()
        if (trimmed.isEmpty() || !CastMarkers.isValidName(trimmed)) return false
        val pool = poolLabel.trim().take(12)
        if (appDb.castCharacterDao.getByNameAndPool(bookUrl, trimmed, pool) != null) return false
        val character = CastCharacter(
            id = UUID.randomUUID().toString(),
            bookUrl = bookUrl,
            name = trimmed,
            poolLabel = pool,
            voiceId = voiceId,
        )
        if (appDb.castCharacterDao.insertIgnore(character) <= 0L) return false
        val withVoice = CastVoicePicker.ensureVoice(character)
        CastProfileMirror.ensure(withVoice)
        return true
    }
    /**
     * 删除书内角色：把它在别处的痕迹一起清掉，否则下次识别会原样长回来。
     *
     * 光删 cast_characters + chapter_role_assignments 不够，残留有三处会伪装成「没删掉」：
     * ① 由分配弹层创建的用户档案（book_character_profiles，source=user）会被
     * [CastAssignmentStore.migrateLegacyProfiles] 按原 id 再导回来，音色顺着
     * ② 的绑定重新读出来；② book_voice_bindings 里以角色/档案 id 挂着的 character 绑定；
     * ③ book_cast_memory 里那行人物档案——提示词要求「已有池的角色原样填它的池」，
     * 于是同名同池必然复现。④ 朗读侧的变声快照也按 id 记着，一并忘掉。
     *
     * [name] 只由官方人物页传入：档案按 (bookUrl, name) 唯一，删的是一条
     * 名字，而配音表按 id 存行；老角色的行 id 与档案 id 可能不同，不按名字一起删掉，
     * 进页面时的档案补建会照着这条行把刚删的人物长回来。
     */
    suspend fun deleteCharacter(bookUrl: String, characterId: String, name: String? = null) {
        val character = appDb.castCharacterDao.getById(characterId)
        appDb.chapterRoleAssignmentDao.deleteForCharacter(bookUrl, characterId)
        if (character != null) {
            forgetVoiceBinding(bookUrl, characterId)
            val profile = appDb.bookKnowledgeDao
                .getCharacterProfile(bookUrl, character.name)
                ?.takeIf { it.bookUrl == bookUrl }
            if (profile != null) {
                forgetVoiceBinding(bookUrl, profile.id)
                if (profile.source == BookCharacterProfile.SOURCE_USER) {
                    // 我们这条流程建的用户档案只为配音存在：连档案一起删掉。
                    appDb.bookKnowledgeDao.deleteCharacterProfile(bookUrl, profile.id)
                } else if (profile.status == BookCharacterProfile.STATUS_ACTIVE) {
                    // AI 识别出的人物资料归人物页，不能顺手抹掉；但必须停用它，
                    // 否则下次进配音页 migrateLegacyProfiles 会按原 id 把角色长回来，
                    // 表现为「删了又出现」。重新建同名角色时 CastProfileMirror 会解冻。
                    appDb.bookKnowledgeDao.upsertCharacterProfile(
                        profile.copy(
                            status = BookCharacterProfile.STATUS_DISABLED,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
            dropMemoryLine(bookUrl, character.name)
        }
        VoiceEffectStore.forgetCharacter(characterId)
        appDb.castCharacterDao.delete(characterId)
        if (name != null) {
            appDb.castCharacterDao.getByName(bookUrl, name)
                .filter { it.id != characterId }
                .forEach { deleteCharacter(bookUrl, it.id) }
        }
    }

    /**
     * 官方「人物详情」页改了名字或声音池 → 配音角色这一侧跟着改。
     *
     * 两边存的是同一份信息（档案的 voiceAgeBand 列现在装池名），不同步的话
     * 正文胶囊、标记与朗读音色会一直用改动前的池。角色是按需从档案导进来的，
     * 还没导过（本书没进过配音页）时这里没有可改的行，什么都不做。
     *
     * 配音角色建档时用档案 id 作主键，所以按 id 找就行：改名后按新名找不到旧行，
     * 按新名命中的会是另一个角色（即下面要挡的撞名）。
     */
    suspend fun syncFromProfile(
        bookUrl: String,
        profileId: String,
        name: String,
        poolLabel: String,
    ) {
        val trimmed = name.trim()
        // 名字要能写进正文标记，否则注入时会被整条丢掉；这种档案改动宁可让配音侧沿用旧名
        if (trimmed.isEmpty() || !CastMarkers.isValidName(trimmed)) return
        val pool = poolLabel.trim().take(12)
        val character = appDb.castCharacterDao.getById(profileId) ?: return
        if (character.bookUrl != bookUrl ||
            (character.name == trimmed && character.poolLabel == pool)
        ) {
            return
        }
        // (bookUrl, name, poolLabel) 唯一：撞别的角色就什么都不改，留给用户自己合
        if (appDb.castCharacterDao.getByName(bookUrl, trimmed)
                .any { it.id != character.id && it.poolLabel == pool }
        ) {
            return
        }
        val updated = CastVoicePicker.ensureVoice(
            character.copy(
                name = trimmed,
                poolLabel = pool,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        appDb.castCharacterDao.update(updated)
        appDb.chapterRoleAssignmentDao.updateForCharacter(
            bookUrl, updated.id, updated.name, updated.poolLabel, updated.updatedAt,
        )
        reloadReaderChapter(bookUrl)
    }

    private suspend fun forgetVoiceBinding(bookUrl: String, subjectId: String) {
        appDb.readAloudVoiceDao.getBinding(
            bookUrl,
            BookVoiceBinding.SUBJECT_CHARACTER,
            subjectId,
        )?.let { appDb.readAloudVoiceDao.deleteBinding(it) }
    }

    /** 本书记忆里删掉这个主名那一行（别的角色行不动）。 */
    private suspend fun dropMemoryLine(bookUrl: String, name: String) {
        val row = appDb.bookCastMemoryDao.get(bookUrl) ?: return
        val kept = row.memory.lineSequence()
            .filter { it.isNotBlank() && it.split('｜', '|').first().trim() != name }
            .joinToString("\n")
        if (kept == row.memory) return
        appDb.bookCastMemoryDao.upsert(row.copy(memory = kept))
    }

    /**
     * 改完角色后让正在读的这一章重取内容：正文里的角色胶囊写的是名字与池，
     * 不重取就会一直显示改动前的写法。不是本书在读时什么都不做。
     */
    suspend fun reloadReaderChapter(bookUrl: String) = withContext(Dispatchers.IO) {
        val book = ReadBook.book ?: return@withContext
        if (book.bookUrl != bookUrl) return@withContext
        val chapter = appDb.bookChapterDao.getChapter(bookUrl, ReadBook.durChapterIndex)
            ?: return@withContext
        BookHelp.delContent(book, chapter)
        ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
    }
}
