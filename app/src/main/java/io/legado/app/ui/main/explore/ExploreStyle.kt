package io.legado.app.ui.main.explore

enum class ExploreStyle(val storageValue: String) {
    DiscoveryModules("discovery_modules"),
    ClassicDiscovery("classic_discovery");

    companion object {
        fun fromStorageValue(value: String): ExploreStyle {
            return entries.firstOrNull { it.storageValue == value } ?: ClassicDiscovery
        }
    }
}
