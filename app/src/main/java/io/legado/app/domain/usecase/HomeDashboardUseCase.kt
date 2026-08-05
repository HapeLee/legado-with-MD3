package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.HomeDashboardGateway
import io.legado.app.domain.model.DEFAULT_DAILY_READING_GOAL_MINUTES
import io.legado.app.domain.model.HomeDashboard
import io.legado.app.domain.model.HomeDashboardSection
import io.legado.app.domain.model.MAX_DAILY_READING_GOAL_MINUTES
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime

class HomeDashboardUseCase(
    private val gateway: HomeDashboardGateway,
    private val clock: Clock,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<HomeDashboard> {
        val todayReadTime = currentDate()
            .flatMapLatest(gateway::observeReadTime)

        return combine(
            gateway.observeTotalReadBooks(),
            gateway.observeTotalReadTime(),
            todayReadTime,
            gateway.observeRecentBooks(RECENT_BOOK_LIMIT),
            gateway.observeDailyGoal(DEFAULT_DAILY_READING_GOAL_MINUTES),
        ) { totalBooks, totalTime, todayTime, recentBooks, dailyGoal ->
            HomeDashboard(
                totalReadBooks = totalBooks,
                totalReadTimeMillis = totalTime,
                todayReadTimeMillis = todayTime,
                dailyGoalMinutes = dailyGoal.coerceIn(
                    1,
                    MAX_DAILY_READING_GOAL_MINUTES,
                ),
                recentBooks = recentBooks,
            )
        }
    }

    suspend fun updateDailyGoal(minutes: Int) {
        gateway.updateDailyGoal(
            minutes.coerceIn(1, MAX_DAILY_READING_GOAL_MINUTES)
        )
    }

    fun observeSelectedSourceSetUrl(): Flow<String?> =
        gateway.observeSelectedSourceSetUrl()

    fun observeVisibleSections(): Flow<Set<HomeDashboardSection>> =
        gateway.observeVisibleSections()

    suspend fun updateSelectedSourceSetUrl(sourceUrl: String) {
        gateway.updateSelectedSourceSetUrl(sourceUrl)
    }

    suspend fun updateVisibleSections(sections: Set<HomeDashboardSection>) {
        gateway.updateVisibleSections(sections)
    }

    private fun currentDate(): Flow<String> = flow {
        while (true) {
            val now = ZonedDateTime.now(clock)
            emit(now.toLocalDate().toString())
            val nextDay = now.toLocalDate()
                .plusDays(1)
                .atStartOfDay(clock.zone)
            delay(
                Duration.between(now, nextDay)
                    .toMillis()
                    .coerceAtLeast(MIN_DATE_REFRESH_DELAY_MILLIS)
            )
        }
    }.distinctUntilChanged()

    private companion object {
        const val RECENT_BOOK_LIMIT = 7
        const val MIN_DATE_REFRESH_DELAY_MILLIS = 1_000L
    }
}
