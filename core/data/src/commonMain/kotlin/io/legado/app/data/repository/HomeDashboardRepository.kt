package io.legado.app.data.repository

import io.legado.app.data.dao.ReadRecordDao
import io.legado.app.domain.gateway.HomeDashboardGateway
import io.legado.app.domain.model.DEFAULT_HOME_DASHBOARD_SECTIONS
import io.legado.app.domain.model.HomeDashboardSection
import io.legado.app.domain.model.HomeReadingBook
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HomeDashboardRepository(
    private val readRecordDao: ReadRecordDao,
    private val preferences: PreferenceStore,
) : HomeDashboardGateway {
    override fun observeTotalReadBooks(): Flow<Int> = readRecordDao.observeTotalReadBookCount()
    override fun observeTotalReadTime(): Flow<Long> = readRecordDao.getTotalReadTime().map { it ?: 0L }
    override fun observeReadTime(date: String): Flow<Long> = readRecordDao.observeReadTimeByDate(date).map { it ?: 0L }
    override fun observeRecentBooks(limit: Int): Flow<List<HomeReadingBook>> = readRecordDao.observeRecentHomeBooks(limit).map { rows -> rows.map { row ->
        val total = row.totalChapterNum; val index = row.chapterIndex
        HomeReadingBook(row.bookUrl, row.recordName, row.recordAuthor, row.origin, if (row.customCoverUrl.isNullOrEmpty()) row.coverUrl else row.customCoverUrl, row.chapterTitle,
            if (total != null && total > 0 && index != null) (index + 1).coerceIn(0, total).toFloat() / total else null)
    } }
    override fun observeDailyGoal(defaultValue: Int): Flow<Int> = preferences.observeInt(HomeDashboardPreferenceKeys.DailyReadingGoalMinutes, defaultValue)
    override fun observeSelectedSourceSetUrl(): Flow<String?> = preferences.observeString(HomeDashboardPreferenceKeys.SourceSetUrl).map { it.takeIf(String::isNotBlank) }
    override fun observeVisibleSections(): Flow<Set<HomeDashboardSection>> = preferences.observeString(HomeDashboardPreferenceKeys.VisibleSections, DEFAULT_HOME_DASHBOARD_SECTIONS.joinToString(",") { it.storageValue }).map(HomeDashboardSection::fromStorage)
    override suspend fun updateDailyGoal(minutes: Int) = preferences.setInt(HomeDashboardPreferenceKeys.DailyReadingGoalMinutes, minutes)
    override suspend fun updateSelectedSourceSetUrl(sourceUrl: String) = preferences.setString(HomeDashboardPreferenceKeys.SourceSetUrl, sourceUrl)
    override suspend fun updateVisibleSections(sections: Set<HomeDashboardSection>) = preferences.setString(HomeDashboardPreferenceKeys.VisibleSections, HomeDashboardSection.entries.filter(sections::contains).joinToString(",") { it.storageValue })
}
