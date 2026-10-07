package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 背景音乐分组：**独立表**，可嵌套（parentId 指回本表，空串 = 根层）。
 *
 * 结构与 [VoicePoolGroupEntity] 完全一致：背景音乐池要和角色声音池用同一套
 * 分组/排序/拖动语义，但两套表分开存放，角色候选池的读取链路一行都不碰。
 */
@Entity(
    tableName = "bgm_pool_groups",
    indices = [
        Index(value = ["parentId"]),
        Index(value = ["parentId", "name"], unique = true),
    ],
)
data class BgmPoolGroupEntity(
    @PrimaryKey
    val id: String,
    var name: String,
    /** 父分组 id，空串 = 根层。 */
    @ColumnInfo(defaultValue = "")
    var parentId: String = "",
    /** 同级内的手动顺序（长按拖动排序回写这里）。 */
    @ColumnInfo(defaultValue = "0")
    var order: Int = 0,
    /** 关掉 = 整棵子树不作为背景音乐候选，已分配好的场景不动。 */
    @ColumnInfo(defaultValue = "1")
    var enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
)

/** 背景音乐池：一组配乐的集合（战斗 / 日常 / 悲伤…），场景分配时选到这一层。 */
@Entity(
    tableName = "bgm_pools",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["groupId", "order"]),
    ],
)
data class BgmPoolEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    /** 所属分组 id，空串 = 未分组。 */
    @ColumnInfo(defaultValue = "")
    var groupId: String = "",
    /** 分组内手动顺序（长按拖动排序回写这里）。 */
    @ColumnInfo(defaultValue = "0")
    var order: Int = 0,
    @ColumnInfo(defaultValue = "1")
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * 配乐 → 背景音乐池归属（多对多），与 [VoicePoolMember] 同构。
 *
 * 一首曲子可以进多个池（同一首「夜雨」既能配悲伤也能配悬疑），所以不占用
 * [BgmTrackEntity] 的列；配乐库本身保持扁平。
 */
@Entity(
    tableName = "bgm_pool_members",
    primaryKeys = ["poolId", "trackId"],
    indices = [Index(value = ["trackId"]), Index(value = ["poolId"])],
)
data class BgmPoolMember(
    val poolId: String,
    val trackId: String,
    /** 池内启用开关：停用的曲目不进随机抽取。 */
    @ColumnInfo(defaultValue = "1")
    val enabled: Boolean = true,
)

/**
 * 背景音乐池里的一条配乐：用户从本机导入的音频文件，导入时复制进应用目录。
 *
 * 只存副本路径，不记 content uri —— _uri 权限在部分厂商 ROM 上不可持久化，
 * 而且原文件被删/移动会让播放直接失败。
 */
@Entity(
    tableName = "bgm_tracks",
    indices = [Index(value = ["name"], unique = true)],
)
data class BgmTrackEntity(
    @PrimaryKey
    val id: String,
    var name: String,
    /** 应用私有目录下的绝对路径（externalFiles/bgm）。 */
    var path: String,
    /** 时长，毫秒；0 = 导入时读不出来。 */
    @ColumnInfo(defaultValue = "0")
    var durationMs: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    var sizeBytes: Long = 0L,
    /** 列表内手动顺序。 */
    @ColumnInfo(defaultValue = "0")
    var order: Int = 0,
    /** 关掉即不作为背景音乐候选，和角色声音池的池开关同语义。 */
    @ColumnInfo(defaultValue = "1")
    var enabled: Boolean = true,
    /**
     * 这条配乐自身的音量（0f–1f）。场景还能再压一档（[BgmSceneMark.volume]），
     * 实际播放取两者乘积，所以「导入的音乐太大声」在库里调一次就全池生效。
     */
    @ColumnInfo(defaultValue = "1.0")
    var volume: Float = 1f,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
)
