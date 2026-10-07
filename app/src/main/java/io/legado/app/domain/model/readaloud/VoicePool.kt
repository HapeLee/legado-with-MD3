package io.legado.app.domain.model.readaloud

import io.legado.app.data.entities.BookCharacterProfile

/**
 * 声音池：对角色音色的粗分类（性别 × 年龄段合成一个标签，如「女青年」），
 * 用于角色配音分配界面的选择与 `<<名字（池）>>` 标记文本。
 *
 * 与 [BookCharacterProfile.voiceGender] / [BookCharacterProfile.voiceAgeBand] 双向映射，
 * 不新增存储字段。
 */
enum class VoicePool(
    val label: String,
    val gender: String,
    val ageBand: String,
) {
    BoyChild("男童", BookCharacterProfile.VOICE_GENDER_MALE, BookCharacterProfile.VOICE_AGE_CHILD),
    BoyTeen("男少年", BookCharacterProfile.VOICE_GENDER_MALE, BookCharacterProfile.VOICE_AGE_TEEN),
    YoungMan("男青年", BookCharacterProfile.VOICE_GENDER_MALE, BookCharacterProfile.VOICE_AGE_YOUNG_ADULT),
    MiddleAgedMan("男中年", BookCharacterProfile.VOICE_GENDER_MALE, BookCharacterProfile.VOICE_AGE_ADULT),
    OldMan("男老年", BookCharacterProfile.VOICE_GENDER_MALE, BookCharacterProfile.VOICE_AGE_ELDERLY),
    MaleVoice("男声", BookCharacterProfile.VOICE_GENDER_MALE, BookCharacterProfile.VOICE_AGE_UNKNOWN),
    GirlChild("女童", BookCharacterProfile.VOICE_GENDER_FEMALE, BookCharacterProfile.VOICE_AGE_CHILD),
    GirlTeen("女少女", BookCharacterProfile.VOICE_GENDER_FEMALE, BookCharacterProfile.VOICE_AGE_TEEN),
    YoungWoman("女青年", BookCharacterProfile.VOICE_GENDER_FEMALE, BookCharacterProfile.VOICE_AGE_YOUNG_ADULT),
    MiddleAgedWoman("女中年", BookCharacterProfile.VOICE_GENDER_FEMALE, BookCharacterProfile.VOICE_AGE_ADULT),
    OldWoman("女老年", BookCharacterProfile.VOICE_GENDER_FEMALE, BookCharacterProfile.VOICE_AGE_ELDERLY),
    FemaleVoice("女声", BookCharacterProfile.VOICE_GENDER_FEMALE, BookCharacterProfile.VOICE_AGE_UNKNOWN),
    Neutral("中性声", BookCharacterProfile.VOICE_GENDER_UNKNOWN, BookCharacterProfile.VOICE_AGE_UNKNOWN),
    ;

    companion object {
        val ByLabel: Map<String, VoicePool> = entries.associateBy { it.label }

        /** 角色档案的性别/年龄字段回推声音池；优先精确匹配，退回性别+未知年龄，最后中性。 */
        fun fromGenderAge(gender: String, ageBand: String): VoicePool =
            entries.firstOrNull { it.gender == gender && it.ageBand == ageBand }
                ?: entries.firstOrNull {
                    it.gender == gender && it.ageBand == BookCharacterProfile.VOICE_AGE_UNKNOWN
                }
                ?: Neutral

        fun labelOf(gender: String, ageBand: String): String = fromGenderAge(gender, ageBand).label
    }
}
