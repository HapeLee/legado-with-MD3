package io.legado.app.ui.book.manga.config

import com.google.gson.annotations.SerializedName
import io.legado.app.utils.GSON

/**
 * 漫画滤镜配置，落在书籍设置 JSON 里；键名写死在 [SerializedName]，否则 R8 改名后旧数据读不回来。
 */
data class MangaColorFilterConfig(
    @SerializedName("r") var r: Int = 0,
    @SerializedName("g") var g: Int = 0,
    @SerializedName("b") var b: Int = 0,
    @SerializedName("a") var a: Int = 0,
    @SerializedName("l") var l: Int = 0,
    @SerializedName("autoBrightness") var autoBrightness: Boolean = true
) {
    fun toJson(): String {
        if (r == 0 && g == 0 && b == 0 && a == 0 && l == 0) {
            return ""
        }
        return GSON.toJson(this)
    }
}
