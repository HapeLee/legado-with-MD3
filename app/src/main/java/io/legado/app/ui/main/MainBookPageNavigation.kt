package io.legado.app.ui.main

import androidx.navigation3.runtime.NavKey
import io.legado.app.utils.hideSoftInput

/** Closes a book subpage and returns its result to its retained Nav3 parent. */
internal fun MainActivity.closeBookPage(
    backStack: MutableList<NavKey>,
    fromRoute: NavKey? = backStack.lastOrNull(),
    result: BookPageResult? = null,
): Boolean {
    if (fromRoute == null || backStack.lastOrNull() != fromRoute) return false
    currentFocus?.let { focused ->
        focused.hideSoftInput()
        focused.clearFocus()
    }
    val resolvedResult = result ?: when (fromRoute) {
        is MainRouteToc -> BookPageResult.TocCancelled
        is MainRouteReplaceRules, is MainRouteReplaceEdit -> BookPageResult.RulesClosed
        else -> null
    }
    val parent = backStack.getOrNull(backStack.lastIndex - 1)
    val popped = MainNavigator.navigateBack(this, backStack, navRouteTracker, fromRoute)
    if (popped && resolvedResult != null &&
        (parent is MainRouteReadBook || parent is MainRouteReadManga || parent is MainRouteBookInfo)
    ) {
        navRouteTracker.reportBookPageResult(parent, resolvedResult)
    }
    return popped
}
