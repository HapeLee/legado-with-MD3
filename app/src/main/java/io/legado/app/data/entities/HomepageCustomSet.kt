package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "homepage_custom_sets")
data class HomepageCustomSet(
    @PrimaryKey
    var id: String = "",
    var name: String = "",
    var sortOrder: Int = 0,
)
