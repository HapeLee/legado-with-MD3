package io.legado.app.ui.main

import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.SceneStrategyScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ListDetailSceneStrategyTest {

    private fun entry(key: NavKey, metadata: Map<String, Any> = emptyMap()): NavEntry<NavKey> =
        NavEntry(key = key, metadata = metadata) {}

    private val listMetadata = ListDetailSceneStrategy.listPane()
    private val detailMetadata = ListDetailSceneStrategy.detailPane()
    private val bothMetadata = ListDetailSceneStrategy.listAndDetailPane()
    private val containerMetadata = ListDetailSceneStrategy.containerPane()

    private val home = entry(MainRouteHome, metadata = containerMetadata)
    private val settings = entry(MainRouteSettings, metadata = bothMetadata)
    private val settingsTheme = entry(MainRouteSettingsTheme, metadata = detailMetadata)
    private val dictRule = entry(MainRouteDictRule, metadata = detailMetadata)
    private val exploreA = entry(
        MainRouteExploreShow(title = "探索", sourceUrl = "source-a", exploreUrl = null),
        metadata = listMetadata,
    )
    private val exploreB = entry(
        MainRouteExploreShow(title = "探索", sourceUrl = "source-b", exploreUrl = "kind-1"),
        metadata = listMetadata,
    )
    private val exploreUnmarked = entry(
        MainRouteExploreShow(title = "探索", sourceUrl = "source-c", exploreUrl = null)
    )
    private val bookInfo = entry(
        MainRouteBookInfo(name = "书", author = "作者", bookUrl = "book-1"),
        metadata = detailMetadata,
    )
    private val bookInfoUnmarked = entry(
        MainRouteBookInfo(name = "书", author = "作者", bookUrl = "book-3")
    )
    private val otherDetail = entry(MainRouteToc(bookUrl = "book-1"))
    private val search = entry(MainRouteSearch(key = null))

    /** 成员扩展函数同时需要分发接收者（策略）与扩展接收者（scope），两个接收者都要显式给出。 */
    private fun ListDetailSceneStrategy.calculate(
        entries: List<NavEntry<NavKey>>,
    ): ListDetailScene? = SceneStrategyScope<NavKey>().calculateScene(entries) as? ListDetailScene

    @Test
    fun exploreShowFollowedByBookInfoIsAPanePair() {
        val pair = listDetailEntries(listOf(home, exploreA, bookInfo))
        checkNotNull(pair)
        assertSame(exploreA, pair.first)
        assertSame(bookInfo, pair.second)
    }

    @Test
    fun detailWithoutItsListIsNotAPanePair() {
        // 首页可以作为任意次级页面的左栏，所以「没有列表」要用未标记的父级来验证。
        assertNull(listDetailEntries(listOf(exploreUnmarked, bookInfo)))
        assertNull(listDetailEntries(listOf(home)))
        assertNull(listDetailEntries(emptyList()))
    }

    @Test
    fun listWithoutDetailIsNotAPanePair() {
        assertNull(listDetailEntries(listOf(home, exploreA)))
    }

    @Test
    fun unrelatedDetailIsNotAPanePair() {
        assertNull(listDetailEntries(listOf(home, exploreA, otherDetail)))
    }

    @Test
    fun unmarkedEntriesAreNotPanePairs() {
        assertNull(listDetailEntries(listOf(home, exploreUnmarked, bookInfo)))
        assertNull(listDetailEntries(listOf(home, exploreA, bookInfoUnmarked)))
    }

    @Test
    fun homeAndASecondaryPageOpenedFromItBecomePanes() {
        // 「我的」里的书源管理 / 替换净化 / 设置这类次级页面：左边首页容器，右边打开的页面。
        val pair = listDetailEntries(listOf(home, settings))
        checkNotNull(pair)
        assertSame(home, pair.first)
        assertSame(settings, pair.second)
    }

    @Test
    fun dualMarkedPageCanBeEitherSideOfANestedPair() {
        // 设置既是开业时的右栏，又是主题设置的左栏。
        checkNotNull(listDetailEntries(listOf(home, settings)))
        val nested = listDetailEntries(listOf(home, settings, settingsTheme))
        checkNotNull(nested)
        assertSame(settings, nested.first)
        assertSame(settingsTheme, nested.second)
    }

    @Test
    fun twoListOnlyEntriesNeverBecomePanes() {
        assertNull(listDetailEntries(listOf(home, exploreUnmarked)))
    }

    @Test
    fun containerPaneGetsMoreWidthThanAPlainList() {
        assertEquals(420.dp, listPaneWidthOf(totalWidth = 840.dp, isContainer = true))
        assertEquals(360.dp, listPaneWidthOf(totalWidth = 840.dp, isContainer = false))
        assertEquals(420.dp, listPaneWidthOf(totalWidth = 1200.dp, isContainer = true))
    }

    @Test
    fun enteringFromSinglePaneExpandsTheLeftPane() {
        assertEquals(
            PaneArrival.Expand,
            paneArrival(previous = null, next = "home" to "dict"),
        )
    }

    @Test
    fun keepingThePairSettledDoesNotReplayTheAnimation() {
        assertEquals(
            PaneArrival.Settled,
            paneArrival(previous = "home" to "dict", next = "home" to "dict"),
        )
    }

    @Test
    fun switchingTheSelectedItemOnlyReplacesTheRightPane() {
        // 「我的」里先点标签规则，再点字典规则：左栏还是首页，只换右栏。
        assertEquals(
            PaneArrival.Swap,
            paneArrival(previous = "home" to "highlight", next = "home" to "dict"),
        )
    }

    @Test
    fun drillingIntoASecondLevelMovesTheOpenedPageToTheLeft() {
        // 书源管理 → 书源编辑：刚才的右栏成为左栏，右栏换成编辑页。
        assertEquals(
            PaneArrival.Promote,
            paneArrival(previous = "home" to "manage", next = "manage" to "edit"),
        )
        // 两级连续下钻同样成立。
        assertEquals(
            PaneArrival.Promote,
            paneArrival(previous = "manage" to "edit", next = "edit" to "detail"),
        )
    }

    @Test
    fun arrivingAtThePairExpandsThenRepeatedSolvesKeepThatArrival() {
        val strategy = ListDetailSceneStrategy(
            isEnabled = { true },
            stackSize = { 3 },
        )
        val entries = listOf(home, exploreA, bookInfo)
        val first = strategy.calculate(entries)
        checkNotNull(first)
        assertEquals(PaneArrival.Expand, first.arrival)

        // NavDisplay 可能对同一次导航再求解一次：那不能变成「已停稳」而跳过展开。
        val second = strategy.calculate(entries)
        checkNotNull(second)
        assertEquals(PaneArrival.Expand, second.arrival)
    }

    @Test
    fun switchingTheDetailKeepsTheSettledArrivalState() {
        val strategy = ListDetailSceneStrategy(isEnabled = { true }, stackSize = { 2 })
        val first = strategy.calculate(listOf(home, settings))
        checkNotNull(first)
        assertEquals(PaneArrival.Expand, first.arrival)

        val swapped = strategy.calculate(listOf(home, dictRule))
        checkNotNull(swapped)
        assertEquals(PaneArrival.Swap, swapped.arrival)
    }

    @Test
    fun unrelatedPairsDoNotPretendToAnimate() {
        assertEquals(
            PaneArrival.Replace,
            paneArrival(previous = "explore" to "bookInfo", next = "home" to "settings"),
        )
    }

    @Test
    fun listPaneNeverSqueezesTheDetailPaneBelowItsMinimum() {
        // 窄窗口下（理论上进不了双栏，但宽度策略自己也不能算出越界值）左栏被压到下限。
        assertEquals(360.dp, listPaneWidthOf(totalWidth = 640.dp, isContainer = true))
        assertTrue(listPaneWidthOf(totalWidth = 640.dp, isContainer = false) <= 640.dp)
    }

    @Test
    fun pairOnlyConsidersTheTwoTopMostEntries() {
        // 详情上面又压了别的页面时不能把列表拖回来当左栏。
        assertNull(listDetailEntries(listOf(home, exploreA, bookInfo, search)))
    }

    @Test
    fun disabledStrategyFallsThroughToTheNextOne() {
        val strategy = ListDetailSceneStrategy(isEnabled = { false })
        assertNull(strategy.calculate(listOf(home, exploreA, bookInfo)))
    }

    @Test
    fun enabledStrategyConsumesBothDestinationsButBackOnlyRemovesDetail() {
        val strategy = ListDetailSceneStrategy(isEnabled = { true })
        val scene = strategy.calculate(listOf(home, exploreA, bookInfo))
        checkNotNull(scene)
        assertEquals(listOf(exploreA, bookInfo), scene.entries)
        assertEquals(listOf(home, exploreA), scene.previousEntries)
    }

    @Test
    fun homeDetailPairHasHomeAsItsBackTarget() {
        val strategy = ListDetailSceneStrategy(isEnabled = { true })
        val scene = checkNotNull(strategy.calculate(listOf(home, settings)))
        assertEquals(listOf(home), scene.previousEntries)
    }

    @Test
    fun nestedSettingsBackTargetRestoresPreviousPanePair() {
        val strategy = ListDetailSceneStrategy(isEnabled = { true })
        val scene = checkNotNull(strategy.calculate(listOf(home, settings, settingsTheme)))
        val previous = checkNotNull(strategy.calculate(scene.previousEntries))
        assertEquals(listOf(home, settings), previous.entries)
    }

    @Test
    fun replacingTheSelectedBookKeepsTheSceneKeyAndLeftEntry() {
        val strategy = ListDetailSceneStrategy(isEnabled = { true })
        val first = strategy.calculate(listOf(home, exploreA, bookInfo))
        val another = entry(
            MainRouteBookInfo(name = "另一本", author = "作者", bookUrl = "book-2"),
            metadata = detailMetadata,
        )
        val second = strategy.calculate(listOf(home, exploreA, another))
        checkNotNull(first)
        checkNotNull(second)
        assertEquals(first.key, second.key)
        assertSame(first.entries.first(), second.entries.first())
    }

    @Test
    fun switchingTheSourceAlsoChangesTheSceneKey() {
        val strategy = ListDetailSceneStrategy(isEnabled = { true })
        val first = strategy.calculate(listOf(home, exploreA, bookInfo))
        val second = strategy.calculate(listOf(home, exploreB, bookInfo))
        checkNotNull(first)
        checkNotNull(second)
        org.junit.Assert.assertNotEquals(first.key, second.key)
    }
}
