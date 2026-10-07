package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 本书角色记忆：AI 分配角色跨章节读写的轻量人物档案（主名｜别名/身份｜人物关系｜声音池）。
 *
 * 只属于本书（主键 = bookUrl），不是全局。用于把「同一个人的不同称呼」
 * （特工化名、卧底代号、成长年龄阶段等）归并为同一个角色，避免 AI 因称呼
 * 不同而重复建档；分配悬浮窗可直接查看/编辑，AI 跑完一章后自动回写。
 */
@Entity(tableName = "book_cast_memory")
data class BookCastMemory(
    @PrimaryKey
    val bookUrl: String,
    val memory: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)
