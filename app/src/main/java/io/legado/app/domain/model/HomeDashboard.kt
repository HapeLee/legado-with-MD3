package io.legado.app.domain.model

data class HomeDashboard(
    val totalReadBooks: Int,
    val totalReadTimeMillis: Long,
    val todayReadTimeMillis: Long,
    val dailyGoalMinutes: Int,
    val recentBooks: List<HomeReadingBook>,
)

data class HomeReadingBook(
    val bookUrl: String?,
    val name: String,
    val author: String,
    val origin: String?,
    val coverPath: String?,
    val chapterTitle: String?,
    val chapterProgress: Float?,
    val isPrivate: Boolean,
)

enum class HomeDashboardSection(val storageValue: String) {
    RecentBook("recent_book"),
    TotalReadBooks("total_read_books"),
    TotalReadTime("total_read_time"),
    RecentBooks("recent_books"),
    DailyGoal("daily_goal"),
    WebDavBackup("webdav_backup"),
    DimensionalBookshelf("dimensional_bookshelf");

    companion object {
        fun fromStorage(value: String): Set<HomeDashboardSection> {
            if (value.isBlank()) return emptySet()
            val sections = value
                .split(',')
                .map(String::trim)
                .mapNotNull { stored ->
                    entries.firstOrNull { it.storageValue == stored }
                }
                .toSet()
            return sections.ifEmpty { DEFAULT_HOME_DASHBOARD_SECTIONS }
        }
    }
}

val DEFAULT_HOME_DASHBOARD_SECTIONS: Set<HomeDashboardSection> =
    HomeDashboardSection.entries.filterNot { it == HomeDashboardSection.DimensionalBookshelf }.toSet()

const val DEFAULT_DAILY_READING_GOAL_MINUTES = 30
const val MAX_DAILY_READING_GOAL_MINUTES = 24 * 60
