package io.legado.app.help.readaloud.cast

/**
 * 角色声音池与背景音乐池共用的行模型。
 *
 * 两套池在数据上是两张表（[VoicePoolStore] / [BgmPoolStore]），但页面的树形列表、
 * 计数、拖动语义要求一致（背景音乐池「都和角色声音池一样」），所以行模型只有一份：
 * 两个 Store 各自把自家实体映射成这里的结构，界面与
 * [io.legado.app.ui.book.readaloud.cast.CastPoolTree] 只认这一套。
 */

/**
 * 池行（含计数）。
 *
 * [groupId] 是权威归属（空串 = 未分组），[groupName] 只是它的显示路径。
 * [groupEnabled] = 所属分组整条链都开着；池自己开着但组关着，对新建分配来说等于关。
 * [total] / [enabledCount] 对角色池是音色数，对背景音乐池是配乐数。
 */
data class CastPoolRow(
    val id: String,
    val name: String,
    val groupId: String,
    val groupName: String,
    val order: Int,
    val enabled: Boolean,
    val groupEnabled: Boolean,
    val isDefault: Boolean,
    val total: Int,
    val enabledCount: Int,
    /**
     * 卡片第二行的说明（正则角色用它显示「命中什么 → 变成什么」）。
     *
     * 空 = 不占行。池那两页没有这一栏，行内容还是「名字 + 所在分组」。
     */
    val subtitle: String = "",
) {
    /** 能不能作为新建分配的候选池。 */
    val usable: Boolean get() = enabled && groupEnabled
}

/** 分组行：可嵌套树拉平后的样子。[path] = 父/子 显示路径，[depth] = 缩进层数。 */
data class CastGroupRow(
    val id: String,
    val name: String,
    val parentId: String,
    val path: String,
    val depth: Int,
    val order: Int,
    val enabled: Boolean,
    /** 自己加上所有祖先都启用；父组关掉，子组和池整体都不可用。 */
    val usable: Boolean,
)

/** 成员行：池内一条素材（角色池 = 音色，背景音乐池 = 配乐）+ 启用标志（复选框）。 */
data class CastMemberRow(
    val id: String,
    val displayName: String,
    /** 音色来自哪个引擎（角色池用；背景音乐池没有引擎，留空）。 */
    val engineName: String = "",
    val enabled: Boolean,
)

/** 池行 + 展开后的成员列表（成员页数据）。 */
data class CastPoolDetail(
    val pool: CastPoolRow,
    val members: List<CastMemberRow>,
)
