package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 正则角色：命中正文里的一段文字，就换这一段的读法。
 *
 * 与 [CastCharacter] 的区别是归属方式：角色表按「哪一句台词」（引号锚点 + 分配表）认人，
 * 这一张按**文字本身**认——正文里出现「爆炸」这两个字，不管它在谁的台词里、在哪一段，
 * 都按这条规则处理。所以它不进分配表、不注入标记，朗读时在已有的朗读单元上再切一刀。
 *
 * 两种去向由 [poolKind] 决定：
 * - [POOL_ROLE]：命中的文字用 [itemId] 那个音色念（角色声音池里的一条音色）。
 * - [POOL_BGM]：命中的文字**不念**，改放 [itemId] 那段音频（背景音乐池里的一条配乐），
 *   走第三条音轨 [io.legado.app.help.readaloud.playback.ReadAloudEffectPlayer]，
 *   与朗读、背景音乐并行。
 *
 * [scope] / [excludeScope] 与官方替换规则同一口径：填书名或书源 URL 的子串，
 * 空 = 全书通用。
 *
 * 朗读侧的消费链：本表 → [io.legado.app.help.readaloud.cast.RegexCastRuleStore.effectsFor]
 * 解成 [io.legado.app.help.readaloud.cast.RegexCastEffect] →
 * [io.legado.app.help.readaloud.cast.RegexCastSplitter] 切分。改字段口径要同步看这两处。
 *
 * 表 `regex_cast_rules` 建表见 `DatabaseMigrations.migration_127_128`（Room version 128），
 * [useRegex] 这一列由 Room version 129 的 AutoMigration 追加，[voiceEffect] 由 130 追加。
 */
@Entity(
    tableName = "regex_cast_rules",
    indices = [
        Index(value = ["enabled", "order"]),
        Index(value = ["groupId", "order"]),
    ],
)
data class RegexCastRule(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,
    /** 角色名称：只在列表里认得出这条规则是干什么的，不参与匹配。 */
    var name: String = "",
    /**
     * 匹配的地方。怎么解释这一串由 [useRegex] 决定，编译入口是
     * [io.legado.app.help.readaloud.cast.RegexCastRuleStore.compile]。
     */
    var pattern: String = "",
    /**
     * 是否按正则匹配。true = [pattern] 当正则编译；false = 整串按字面量匹配，
     * 里面那些 `(`、`[`、`*` 之类都只是普通字符。
     *
     * 只在编译这一步分叉，命中之后换音色还是放音频、怎么切朗读单元都不看这个开关。
     */
    @ColumnInfo(defaultValue = "1")
    var useRegex: Boolean = true,
    @ColumnInfo(defaultValue = "role")
    var poolKind: String = POOL_ROLE,
    /** 声音池 id（角色池或配乐池，看 [poolKind]）。 */
    @ColumnInfo(defaultValue = "")
    var poolId: String = "",
    /** 音色 id / 配乐 id。空 = 只选了池，朗读时按池内启用的随机取一条。 */
    @ColumnInfo(defaultValue = "")
    var itemId: String = "",
    /**
     * 变声器预设名，空 = 不变声。
     *
     * 只对 [POOL_ROLE] 那种有意义（[POOL_BGM] 命中处根本不念，没有声音可变）。
     * 生产方是正则角色编辑弹窗（`RegexCastRuleScreen` 的「变声器」那一行），消费方是
     * [io.legado.app.help.readaloud.cast.RegexCastRuleStore.effectsFor]：它把这一列抄进
     * [io.legado.app.help.readaloud.cast.RegexCastEffect.voiceEffect]，随切分结果落到朗读单元的
     * [io.legado.app.domain.model.readaloud.ChapterSpeechSegment.voiceEffect]，与正文胶囊那一份
     * ([ChapterRoleAssignment.voiceEffect]) 走同一条通道——音高/语速与混响/金属感两层都由
     * [io.legado.app.help.readaloud.effect.VoiceEffectStore] 按名字取预设后套上，
     * 预设被停用或删除就听不到效果（不改声）。
     */
    @ColumnInfo(defaultValue = "")
    var voiceEffect: String = "",
    /** 所属分组 id，指向 [RegexCastGroup.id]，空串 = 未分组；无 Room 外键，级联口径在 RegexCastRuleStore.deleteGroup。 */
    @ColumnInfo(defaultValue = "")
    var groupId: String = "",
    @ColumnInfo(defaultValue = "1")
    var enabled: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    var order: Int = 0,
    /** 特定范围：书名或书源 URL 的子串，空 = 不限。 */
    var scope: String? = null,
    /** 排除范围：命中这些子串的书不应用本条。 */
    var excludeScope: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
) {

    companion object {
        const val POOL_ROLE = "role"
        const val POOL_BGM = "bgm"
    }
}
