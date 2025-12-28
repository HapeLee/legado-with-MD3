package io.legado.app.ui.theme

enum class AppThemeMode {
    Dynamic,
    GR,
    Lemon,
    WH,
    Elink,
    Sora,
    August,
    Carlotta,
    Koharu,
    Yuuka,
    Phoebe,
    Mujika,
    ContentBased,
    Transparent
}

fun resolveThemeMode(value: String): AppThemeMode = when (value) {
    "0" -> AppThemeMode.Dynamic
    "1" -> AppThemeMode.GR
    "2" -> AppThemeMode.Lemon
    "3" -> AppThemeMode.WH
    "4" -> AppThemeMode.Elink
    "5" -> AppThemeMode.Sora
    "6" -> AppThemeMode.August
    "7" -> AppThemeMode.Carlotta
    "8" -> AppThemeMode.Koharu
    "9" -> AppThemeMode.Yuuka
    "10" -> AppThemeMode.Phoebe
    "11" -> AppThemeMode.Mujika
    "12" -> AppThemeMode.ContentBased
    "13" -> AppThemeMode.Transparent
    else -> AppThemeMode.Dynamic
}
