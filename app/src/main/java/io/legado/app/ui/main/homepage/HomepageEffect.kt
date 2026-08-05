package io.legado.app.ui.main.homepage

sealed interface HomepageEffect {
    data class NavigateToBookInfo(
        val name: String?,
        val author: String?,
        val bookUrl: String,
        val origin: String? = null,
        val coverPath: String? = null,
        val sharedCoverKey: String?,
    ) : HomepageEffect

    data class NavigateToExploreShow(
        val title: String?,
        val sourceUrl: String,
        val exploreUrl: String?,
    ) : HomepageEffect

    data class ShowSnackbar(val message: String) : HomepageEffect
}
