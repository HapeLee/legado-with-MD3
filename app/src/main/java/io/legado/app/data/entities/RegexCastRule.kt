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
 *   走第三条音轨，与背景音乐和朗读并行。
 *
 * [scope] / [excludeScope] 与官方替换规则同一口径：填书名或书源 URL 的子串，
 * 空 = 全书通用。
 */
@Entity(
    tableName = "regex_cast_rules",
    indices = [Index(value = ["enabled", "order"])],
)
data class RegexCastRule(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,
    /** 角色名称：只在列表里认得出这条规则是干什么的，不参与匹配。 */
    var name: String = "",
    /** 匹配的地方。文本与正则同一张表：先当正则编，编不过再按字面量兜。 */
    var pattern: String = "",
    @ColumnInfo(defaultValue = "role")
    var poolKind: String = POOL_ROLE,
    /** 声音池 id（角色池或配乐池，看 [poolKind]）。 */
    @ColumnInfo(defaultValue = "")
    var poolId: String = "",
    /** 音色 id / 配乐 id。空 = 只选了池，朗读时按池内启用的随机取一条。 */
    @ColumnInfo(defaultValue = "")
    var itemId: String = "",
    /** 列表分组，空 = 未分组。只用来把一长串规则分开看。 */
    @ColumnInfo(defaultValue = "")
    var group: String = "",
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
