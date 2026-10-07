package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * 正文背景音乐场景标记：某章某一段起，背景音乐换成这一条。
 *
 * 与 [ChapterRoleAssignment] 同一套锚点思路（章内序号，不存字符偏移），差别在于它
 * **不往正文里注入任何字符**：朗读链路读到的还是原正文，胶囊只是界面上给这段加的
 * 视觉标记，所以正文一字不改、也不会被 TTS 念出来。
 *
 * 池名/曲名存名字而不是 id：导库、换设备后仍认得出来，找不到时按「没分配」处理，
 * 不会让朗读因为一条过期记录而失败。
 */
@Entity(
    tableName = "bgm_scene_marks",
    primaryKeys = ["bookUrl", "chapterIndex", "paragraphOrdinal"],
    indices = [
        Index(value = ["bookUrl", "chapterIndex"]),
        Index(value = ["bookUrl"]),
    ],
)
data class BgmSceneMark(
    val bookUrl: String,
    val chapterIndex: Int,
    /** 正文段落序号，从 0 开始（跳过空行；章节标题不算正文）。 */
    val paragraphOrdinal: Int,
    /** 背景音乐池名：朗读时从池内启用的配乐里随机取一首。 */
    @ColumnInfo(defaultValue = "")
    var poolName: String = "",
    /** 指定配乐名，非空时优先于 [poolName]（用户点名单首的场景）。 */
    @ColumnInfo(defaultValue = "")
    var trackName: String = "",
    /** 空串 = 这段起「不要背景音乐」（用来掐掉从上一章延续过来的场景）。 */
    @ColumnInfo(defaultValue = "1")
    var enabled: Boolean = true,
    /** 本段音量（0f–1f），与配乐曲目自身音量相乘；1f = 不额外压。 */
    @ColumnInfo(defaultValue = "1.0")
    var volume: Float = 1f,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
)
