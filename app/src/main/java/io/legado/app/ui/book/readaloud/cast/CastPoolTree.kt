package io.legado.app.ui.book.readaloud.cast

import io.legado.app.help.readaloud.cast.CastGroupRow
import io.legado.app.help.readaloud.cast.CastPoolRow

/**
 * 「分组树 + 池」这套列表的纯逻辑：拉平、拖动落点归属、松手回写的整表计划。
 *
 * 角色声音池（[MultiRoleRuleViewModel]）与背景音乐池（[BgmPoolViewModel]）用的是同一套
 * 交互，行模型也共用 [CastPoolRow]/[CastGroupRow]，所以这套规则只写一遍：
 * 在各 ViewModel 里各抄一份，两份行为迟早分叉。
 */
object CastPoolTree {

    /** 「未分组」段的 id：它不对应分组行，只作拖动落点与收起状态用。 */
    const val UNGROUPED_ID = ""
    const val UNGROUPED_KEY = "row_ungrouped"
    const val GROUP_KEY = "row_group_"
    const val POOL_KEY = "row_pool_"

    /**
     * 分组树 + 池 → 页面的一条拉平列表（拖动排序就作用在它上面）。
     *
     * 顺序：未分组表头 → 未分组的池 → 各分组（DFS，子组紧跟父组、缩进一层）→ 组内池。
     * 收起的分组只留表头，整棵子树隐藏。搜索时不分层，直接列命中的池。
     */
    fun buildRows(
        groups: List<CastGroupRow>,
        pools: List<CastPoolRow>,
        collapsed: Set<String>,
        query: String,
    ): List<PoolTreeRow> {
        if (query.isNotBlank()) {
            return pools
                .filter {
                    it.name.contains(query, ignoreCase = true) ||
                        it.groupName.contains(query, ignoreCase = true)
                }
                .map { PoolTreeRow(key = POOL_KEY + it.id, groupId = it.groupId, isGroup = false, pool = it) }
        }
        val byGroup = pools.groupBy { it.groupId }
        fun poolRows(groupId: String, depth: Int) = byGroup[groupId].orEmpty()
            .map {
                PoolTreeRow(
                    key = POOL_KEY + it.id,
                    groupId = groupId,
                    isGroup = false,
                    depth = depth,
                    pool = it,
                )
            }
        val rows = ArrayList<PoolTreeRow>()
        rows += PoolTreeRow(
            key = UNGROUPED_KEY,
            groupId = "",
            isGroup = true,
            isUngroupedHeader = true,
        )
        if (!collapsed.contains(UNGROUPED_ID)) rows += poolRows(UNGROUPED_ID, 1)
        // listGroups() 已经是 DFS 前序：收起某组后，比它更深的所有行都属于它的子树，整段跳过
        var hiddenDepth = Int.MAX_VALUE
        groups.forEach { group ->
            if (group.depth > hiddenDepth) return@forEach
            hiddenDepth = Int.MAX_VALUE
            rows += PoolTreeRow(
                key = GROUP_KEY + group.id,
                groupId = group.id,
                isGroup = true,
                depth = group.depth,
                title = group.name,
                group = group,
            )
            if (collapsed.contains(group.id)) {
                hiddenDepth = group.depth
            } else {
                rows += poolRows(group.id, group.depth + 1)
            }
        }
        return rows
    }

    /**
     * 被拖行的落点归属（文件夹那种「范围」算法，null = 非法落点、保持原父级）。
     *
     * 默认：松手位置往上找最近的一个分组头，落在谁的范围里就归谁；一路向上没有分组头 =
     * 未分组（池）/ 根层（分组）。
     *
     * 例外（这才是「拖出」）：上下两侧紧挨着两个分组头，且下面那个不比上面那个深 —— 说明
     * 这一格根本不在上面那个组的范围里，而是两个同级组之间的缝隙，落点层级跟下面那个齐。
     * 没有这条，把子组挪回根层就只能一路拖到列表最顶上，中间任何位置都会被上面最近的
     * 分组头吸进去，看起来就像「只有拖进去、没有拖出来」。
     *
     * 分组不能拖进自己或自己的子树，那种落点直接判非法，否则树会成环、整组从页面上消失。
     * 拖动过程中的高亮和松手回写都走这一个函数，所见即所得。
     */
    fun dropParentOf(
        rows: List<PoolTreeRow>,
        index: Int,
        groupParent: Map<String, String>,
    ): String? {
        val row = rows.getOrNull(index) ?: return null
        val above = rows.getOrNull(index - 1)?.group
        val below = rows.getOrNull(index + 1)?.group
        var parent = if (above != null && below != null && below.depth <= above.depth) {
            groupParent[below.id] ?: below.parentId
        } else {
            var found = ""
            for (i in index - 1 downTo 0) {
                val header = rows[i].group ?: continue
                found = header.id
                break
            }
            found
        }
        val group = row.group ?: return parent
        if (parent.isEmpty()) return parent
        return parent.takeUnless {
            it == group.id || isGroupDescendant(it, group.id, groupParent)
        }
    }

    /** [candidateId] 是不是 [ancestorId] 自己或后代——是就不能把后者拖到前者下面，会成环。 */
    fun isGroupDescendant(
        candidateId: String,
        ancestorId: String,
        groupParent: Map<String, String>,
    ): Boolean {
        var cursor: String? = candidateId
        var guard = 0
        while (cursor != null && guard++ < 32) {
            if (cursor == ancestorId) return true
            cursor = groupParent[cursor]
        }
        return false
    }

    /** 一次拖动结束后的回写计划：`id to (父级 id, 顺序)`，交给各自 Store 的 saveSlots。 */
    typealias SlotList = List<Pair<String, Pair<String, Int>>>

    /**
     * 松手落库计划：顺序按当前可见列表整体回写，但父级只重算「被拖的那一行」。
     *
     * reorderable 一次只挪一行，分组表头会被单独拖离自己的子树。要是按可见顺序反推所有行
     * 的父级，一个组头挪走就会把它名下的池算到别的组头上——那是改坏无关数据。所以其余行的
     * parentId/groupId 原样保留，组头被拖到别处时整棵子树自然跟着走；收起的子树不在列表里，
     * 同样不动。
     *
     * 返回 null = 不该回写（搜索态下列表是拉平的命中结果，回写会打乱归属）。
     */
    fun savePlan(
        rows: List<PoolTreeRow>,
        pools: List<CastPoolRow>,
        groups: List<CastGroupRow>,
        draggedKey: String?,
    ): Pair<SlotList, SlotList>? {
        val targets = rows.filter { !it.isUngroupedHeader }
        if (targets.isEmpty()) return null
        val poolParent = pools.associate { it.id to it.groupId }.toMutableMap()
        val groupParent = groups.associate { it.id to it.parentId }.toMutableMap()
        val index = targets.indexOfFirst { it.key == draggedKey }
        if (index >= 0) {
            val row = targets[index]
            val target = dropParentOf(targets, index, groupParent)
            if (target != null) {
                row.pool?.let { poolParent[it.id] = target }
                row.group?.let { groupParent[it.id] = target }
            }
        }
        val poolSlots = ArrayList<Pair<String, Pair<String, Int>>>(targets.size)
        val groupSlots = ArrayList<Pair<String, Pair<String, Int>>>(targets.size)
        targets.forEachIndexed { i, row ->
            row.pool?.let { poolSlots += it.id to ((poolParent[it.id] ?: it.groupId) to i) }
            row.group?.let { g -> groupSlots += g.id to ((groupParent[g.id] ?: g.parentId) to i) }
        }
        return poolSlots to groupSlots
    }
}
