package io.legado.app.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import io.legado.app.data.dao.TagGroupRuleDao
import io.legado.app.data.entities.TagGroupRule

/**
 * 最小 @Database 骨架：验证「真实 entity + DAO」去 Android 化后，
 * 在 :core:data commonMain 上 `@ConstructedBy` + `expect object` 模式可用
 * （KSP 为 Android/Desktop 双 target 生成 actual 与 `_Impl`）。
 *
 * 这是 AppDatabase 下沉 commonMain 的原型切片——后续 65 entities + 38 DAO
 * 全部下沉后，AppDatabase 将以同样的形态迁入本模块。
 */
@Database(
    entities = [TagGroupRule::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(CoreDataDatabaseConstructor::class)
abstract class CoreDataDatabase : RoomDatabase() {
    abstract val tagGroupRuleDao: TagGroupRuleDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object CoreDataDatabaseConstructor : RoomDatabaseConstructor<CoreDataDatabase> {
    override fun initialize(): CoreDataDatabase
}
