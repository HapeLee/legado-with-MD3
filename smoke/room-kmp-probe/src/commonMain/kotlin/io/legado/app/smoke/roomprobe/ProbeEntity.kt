package io.legado.app.smoke.roomprobe

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Minimal entity to prove Room 2.8.4 KSP can process a `commonMain` entity
 * and generate platform-specific implementations for both targets.
 */
@Entity(tableName = "probe")
data class ProbeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val createdAt: Long,
)
