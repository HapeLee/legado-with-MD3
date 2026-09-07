package io.legado.app.domain.gateway

import io.legado.app.domain.model.HomeDashboardSection
import io.legado.app.domain.model.HomeReadingBook
import kotlinx.coroutines.flow.Flow

interface HomeDashboardGateway {
    fun observeTotalReadBooks(): Flow<Int>

    fun observeTotalReadTime(): Flow<Long>

    fun observeReadTime(date: String): Flow<Long>

    fun observeRecentBooks(limit: Int): Flow<List<HomeReadingBook>>

    fun observeDailyGoal(defaultValue: Int): Flow<Int>

    fun observeSelectedSourceSetUrl(): Flow<String?>

    fun observeVisibleSections(): Flow<Set<HomeDashboardSection>>

    suspend fun updateDailyGoal(minutes: Int)

    suspend fun updateSelectedSourceSetUrl(sourceUrl: String)

    suspend fun updateVisibleSections(sections: Set<HomeDashboardSection>)
}
