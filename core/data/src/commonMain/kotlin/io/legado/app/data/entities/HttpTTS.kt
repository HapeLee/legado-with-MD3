package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.legado.app.core.platform.systemTimeMillis

/**
 * 在线朗读引擎
 */
@Entity(tableName = "httpTTS")
data class HttpTTS(
    @PrimaryKey
    val id: Long = systemTimeMillis(),
    var name: String = "",
    var url: String = "",
    var contentType: String? = null,
    @ColumnInfo(defaultValue = "0")
    override var concurrentRate: String? = "0",
    override var loginUrl: String? = null,
    override var loginUi: String? = null,
    override var header: String? = null,
    override var jsLib: String? = null,
    @ColumnInfo(defaultValue = "0")
    override var enabledCookieJar: Boolean? = false,
    var loginCheckJs: String? = null,
    /**
     * 源级语速, 0..80, 实际倍速为 (speed + 5) / 10, null 时使用默认值 5 (1 倍速)。
     * 仅当源接口支持语速参数 ({{speakSpeed}}) 时影响合成结果。
     */
    var speed: Int? = null,
    @ColumnInfo(defaultValue = "0")
    var lastUpdateTime: Long = systemTimeMillis()
) : BaseSource {

    override fun getTag(): String {
        return name
    }

    override fun getKey(): String {
        return "httpTts:$id"
    }

}
