package io.legado.app.help.readaloud.cast

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.CastCharacter

/**
 * 配音角色并入官方人物档案：分配出去的人物在「人物」页要看得见，能配头像、别名、简介。
 *
 * 新建角色时档案 id 直接用角色 id，音色绑定（book_voice_bindings 以档案 id 为主体）、
 * 朗读覆盖层与人物页就指向同一条记录。池名写进 `voiceAgeBand` 那一列——
 * 该列现在存的就是声音池名（见 [VoicePoolStore.poolNameOrEmpty]）。
 */
object CastProfileMirror {

    /**
     * 本书所有配音角色补一遍档案。
     *
     * 自动选音与档案镜像上线之前建的角色只有 `cast_characters` 行，官方「人物」一节会显示
     * 「还没有人物档案」，两边看着像两批人。进配音页时补一次，之后 id 一致、双向都读得到。
     */
    suspend fun backfillAll(bookUrl: String) {
        if (bookUrl.isBlank()) return
        appDb.castCharacterDao.getByBook(bookUrl).forEach { ensure(it) }
    }

    suspend fun ensure(character: CastCharacter) {
        if (character.name.isBlank()) return
        val bookUrl = character.bookUrl
        // 改名后的角色按 id 还能找回原档案：不找回就会撞 (bookUrl, name) 唯一键
        val target = appDb.bookKnowledgeDao.getCharacterProfile(bookUrl, character.name)
            ?.takeIf { it.bookUrl == bookUrl }
            ?: appDb.bookKnowledgeDao.getCharacterProfile(bookUrl, character.id)
                ?.takeIf { it.bookUrl == bookUrl }
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
