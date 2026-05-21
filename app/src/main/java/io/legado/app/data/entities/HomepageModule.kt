package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "homepage_modules")
data class HomepageModule(
    @PrimaryKey
    var id: String = "",
    var sourceUrl: String = "",
    var moduleKey: String = "",
    var type: String = "",
    var title: String = "",
    var args: String? = null,
    var layoutConfig: String? = null,
    var url: String? = null,
    var isEnabled: Boolean = true,
    var sortOrder: Int = 0,
    var customSetId: String? = null,
    var isUserCreated: Boolean = false,
    var customTitle: String? = null,
    var customSetTitle: String? = null,
    var sourceJsonHash: String? = null,
    var syncedAt: Long = 0,
)
