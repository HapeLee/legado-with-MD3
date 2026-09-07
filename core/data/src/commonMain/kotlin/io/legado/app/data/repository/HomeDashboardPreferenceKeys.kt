package io.legado.app.data.repository

internal object HomeDashboardPreferenceKeys {
    const val DailyReadingGoalMinutes = "daily_reading_goal_minutes"
    const val SourceSetUrl = "home_source_set_url"
    const val VisibleSections = "home_dashboard_sections"
}

internal object CheckSourcePreferenceKeys {
    const val Timeout = "checkSourceTimeout"
    const val Search = "checkSearch"
    const val Discovery = "checkDiscovery"
    const val Info = "checkInfo"
    const val Category = "checkCategory"
    const val Content = "checkContent"
}
