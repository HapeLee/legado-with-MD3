package io.legado.app.help.readaloud.cast

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookVoiceBindingEntity
import io.legado.app.data.entities.CastCharacter
import io.legado.app.domain.model.readaloud.BookVoiceBinding
import io.legado.app.ui.config.readConfig.ReadConfig

/**
 * 角色音色的本地自动选择（不联网、不调 AI）。
 *
 * 触发条件：多角色朗读开启、角色已归属某个声音池但还没选音色。
 * 规则：在该池**启用**的音色里选本书其它角色用得最少的一个（并列时按池内顺序），
 * 因此未被占用的音色一定优先，整池都用过一次后才会开始重复，且结果稳定可复现。
 * 选择写回 cast_characters.voiceId，并镜像一条 character 音色绑定，
 * 让既有的发音链路（读 book_voice_bindings）真正用上它。
 */
object CastVoicePicker {

    /** 角色没选音色时按声音池就近补一个；无需/无法补选时原样返回。 */
    suspend fun ensureVoice(character: CastCharacter): CastCharacter {
        if (character.voiceId.isNotBlank() || character.poolLabel.isBlank()) return character
        // 补音既服务发声（多角色朗读）也服务分配表（胶囊里看得见选了谁），任一开启就补
        if (!ReadConfig.useMultiSpeaker && !ReadConfig.multiRoleCast) return character
        val ordered = VoicePoolStore.enabledVoiceIdsOfPool(character.poolLabel)
        if (ordered.isEmpty()) return character
        val usage = appDb.castCharacterDao.getByBook(character.bookUrl)
            .filter { it.id != character.id && it.voiceId.isNotBlank() }
            .groupingBy { it.voiceId }
            .eachCount()
        val voiceId = ordered.minByOrNull { usage[it] ?: 0 } ?: return character
        val updated = character.copy(voiceId = voiceId, updatedAt = System.currentTimeMillis())
        appDb.castCharacterDao.update(updated)
        syncBinding(updated)
        return updated
    }

    /**
     * 把角色音色镜像到 book_voice_bindings（发音链路的权威读取处）。
     *
     * 只有本书角色档案里存在同名/同 id 档案时才写：绑定主体必须是发音分析产出的
     * profileId，凭空插入的绑定永远读不到，只会污染配音页。用户手动锁定的绑定不覆盖。
     */
    private suspend fun syncBinding(character: CastCharacter) {
        val profile = appDb.bookKnowledgeDao
            .getCharacterProfile(character.bookUrl, character.name) ?: return
        val existing = appDb.readAloudVoiceDao.getBinding(
            character.bookUrl,
            BookVoiceBinding.SUBJECT_CHARACTER,
            profile.id,
        )
        // 配音页手动锁定的绑定优先级更高，自动选音不改它
        if (existing?.locked == true) return
        if (existing == null || existing.voiceId != character.voiceId) {
            appDb.readAloudVoiceDao.upsertBinding(
                existing?.copy(
                    voiceId = character.voiceId,
                    updatedAt = System.currentTimeMillis(),
                )
                    ?: BookVoiceBindingEntity(
                        bookUrl = character.bookUrl,
                        subjectType = BookVoiceBinding.SUBJECT_CHARACTER,
                        subjectId = profile.id,
                        voiceId = character.voiceId,
                    ),
            )
        }
    }
}
