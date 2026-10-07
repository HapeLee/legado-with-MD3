package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 变声器预设：给某个角色的声音再加一层「音高 / 语速 / 混响 / 金属感」。
 *
 * 全部参数都用平台自带能力实现，不引入第三方库：
 * - [pitch]/[speed] 走 Media3 内置的 Sonic 音高变换（`PlaybackParameters`）与系统 TTS 的
 *   `setPitch/setSpeechRate`，任何朗读引擎都能生效；
 * - [reverbPreset] 走平台 `PresetReverb`（0 = 不加混响，其余是 AudioEffect 的预设号）；
 * - [metal] 走平台 `Equalizer` 的中频带通，做出机器人/电话音那种金属感。
 *
 * 主键用名字而不是 id：角色表里存的就是预设名，导库换设备后仍认得出来；
 * 找不到预设时按「不变声」处理，不会因为一条过期记录让朗读失败。
 */
@Entity(
    tableName = "voice_effect_presets",
    indices = [Index(value = ["enabled", "order"])],
)
data class VoiceEffectPreset(
    /** 预设名 = 身份，角色表按名字引用。 */
    @PrimaryKey
    val name: String,
    /** 音高倍率，与音色自带音高相乘（0.25–4）。 */
    @ColumnInfo(defaultValue = "1")
    var pitch: Float = 1f,
    /** 语速倍率，与朗读语速相乘（0.5–2）。 */
    @ColumnInfo(defaultValue = "1")
    var speed: Float = 1f,
    /** 平台混响预设号，0 = 不加混响（见 AudioEffect.PRESET_*）。 */
    @ColumnInfo(defaultValue = "0")
    var reverbPreset: Int = 0,
    /** 中频带通：机器人/电话音那种金属感。 */
    @ColumnInfo(defaultValue = "0")
    var metal: Boolean = false,
    @ColumnInfo(defaultValue = "1")
    var enabled: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    var order: Int = 0,
    /** 内置预设：删除时连带回收，导入同名记录会覆盖参数。 */
    @ColumnInfo(defaultValue = "0")
    var builtin: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
)
