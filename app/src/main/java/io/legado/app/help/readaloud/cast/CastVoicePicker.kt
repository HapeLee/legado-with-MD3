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
     * 用户亲自给角色挑的音色写进 book_voice_bindings。
     *
     * 绑定才是权威读取处：配音页显示的是它（`BookVoiceCastingViewModel`），发音链路取角色音
     * 走的也是它（`BuildSpeechPlanUseCase`、[CastSpeechOverlay]），`cast_characters.voiceId`
     * 只是分配表那一侧的副本。正文胶囊与配音页两条入口都必须调这里，否则各写一半就互相看不见。
     *
     * 主体 id 有两套并存：新建配音角色时档案 id 就是角色 id（见 [CastProfileMirror.ensure]），
     * 但先有人物档案、后来才配音的角色档案 id 与角色 id 不同，而配音页按档案 id 读绑定、
     * 朗读侧按角色 id 读——所以两个 id 都要写，改一次两边才都跟着变。
     * 按「用户锁定」写入（locked=true），自动补音 [syncBinding] 之后不会再改动它。
     * 角色这一份没有音色（[CastCharacter.voiceId] 为空）就是清掉绑定：留着旧绑定，
     * 配音页与朗读都会继续显示/使用用户已经换掉或去掉的那一个。
     */
    suspend fun bindUserVoice(character: CastCharacter) {
        val bookUrl = character.bookUrl
        val voiceId = character.voiceId
        if (bookUrl.isBlank() || character.id.isBlank()) return
        val subjectIds = (
            listOf(character.id) +
                appDb.bookKnowledgeDao.getCharacterProfiles(bookUrl, 500)
                    .filter { it.bookUrl == bookUrl && it.name == character.name }
                    .map { it.id }
            ).distinct()
        val now = System.currentTimeMillis()
        subjectIds.forEach { subjectId ->
            val existing = appDb.readAloudVoiceDao.getBinding(
                bookUrl,
                BookVoiceBinding.SUBJECT_CHARACTER,
                subjectId,
            )
            if (voiceId.isBlank()) {
                existing?.let { appDb.readAloudVoiceDao.deleteBinding(it) }
                return@forEach
            }
            if (existing?.voiceId == voiceId && existing.locked) return@forEach
            appDb.readAloudVoiceDao.upsertBinding(
                existing?.copy(voiceId = voiceId, locked = true, updatedAt = now)
                    ?: BookVoiceBindingEntity(
                        bookUrl = bookUrl,
                        subjectType = BookVoiceBinding.SUBJECT_CHARACTER,
                        subjectId = subjectId,
                        voiceId = voiceId,
                        locked = true,
                    ),
            )
        }
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
