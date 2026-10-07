package io.legado.app.help.readaloud.cast

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.CastCharacter

/**
 * 配音角色并入官方人物档案：分配出去的人物在「人物」页要看得见，能配头像、别名、简介。
 *
 * 新建角色时档案 id 直接用角色 id，音色绑定（book_voice_bindings 以档案 id 为主体）、
 * 朗读覆盖层与人物页就指向同一条记录。池名写进 `voiceAgeBand` 那一列——
 * 该列存的就是声音池名（见 [VoicePoolStore.poolNameOrEmpty]）。
 */
object CastProfileMirror {

    /**
     * 本书所有配音角色补一遍档案。
     *
     * 只有 `cast_characters` 行、没有档案的旧角色，官方「人物」一节会显示「还没有人物档案」，
     * 两边 id 对不上。进配音页时补一次，之后 id 一致、双向都读得到。
     */
    suspend fun backfillAll(bookUrl: String) {
        if (bookUrl.isBlank()) return
        appDb.castCharacterDao.getByBook(bookUrl).forEach { ensure(it) }
    }

    suspend fun ensure(character: CastCharacter) {
        if (character.name.isBlank()) return
        val bookUrl = character.bookUrl
        // 先按 id 找：配音角色建档用的就是角色 id，那条档案一定是这个人的。
        // 按名字兜底只认**名字完全相等**的那条：`getCharacterProfile` 连 aliasesJson 一起 LIKE，
        // 拿它按名找会把「记着这个别名的别人」捞出来改名，一次错误的 upsert 就同时破坏
        // 档案与角色行的对应、气泡和配音跟着错。称呼归并是 [canonicalCastName] 的活，不在这里。
        val target = appDb.bookKnowledgeDao.getCharacterProfile(bookUrl, character.id)
            ?.takeIf { it.bookUrl == bookUrl }
            ?: appDb.bookKnowledgeDao.getCharacterProfiles(bookUrl, 500).firstOrNull {
                it.bookUrl == bookUrl && it.name == character.name && it.id != character.id
            }
        val now = System.currentTimeMillis()
        if (target != null) {
            val sameIdentity = target.name == character.name &&
                    VoicePoolStore.poolNameOrEmpty(target.voiceAgeBand) == character.poolLabel
            if (sameIdentity && target.status == BookCharacterProfile.STATUS_ACTIVE) return
            appDb.bookKnowledgeDao.upsertCharacterProfile(
                target.copy(
                    name = character.name,
                    voiceAgeBand = character.poolLabel,
                    // 重新建同名角色就是把这条档案放回配音链路（删除时置过 DISABLED）
                    status = BookCharacterProfile.STATUS_ACTIVE,
                    updatedAt = now,
                ),
            )
            return
        }
        appDb.bookKnowledgeDao.upsertCharacterProfile(
            BookCharacterProfile(
                id = character.id,
                bookUrl = bookUrl,
                name = character.name,
                voiceAgeBand = character.poolLabel,
                source = BookCharacterProfile.SOURCE_USER,
                createdAt = character.createdAt,
                updatedAt = now,
            ),
        )
    }
}
