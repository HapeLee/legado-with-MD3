package io.legado.app.data.entities.rule

import android.os.Parcelable
import com.google.gson.JsonDeserializer
import io.legado.app.utils.INITIAL_GSON
import kotlinx.parcelize.Parcelize

/**
 * 发现结果规则
 */
@Parcelize
data class ExploreRule(
    override var bookList: String? = null,
    override var name: String? = null,
    override var author: String? = null,
    override var intro: String? = null,
    override var kind: String? = null,
    override var lastChapter: String? = null,
    override var updateTime: String? = null,
    override var bookUrl: String? = null,
    override var coverUrl: String? = null,
    override var wordCount: String? = null,
    /** 发现页翻页参数名，留空按 page 处理；用于标签/分类跳转的地址自动追加页码。 */
    var pageParam: String? = null
) : BookListRule, Parcelable {

    companion object {

        val jsonDeserializer = JsonDeserializer<ExploreRule?> { json, _, _ ->
            when {
                json.isJsonObject -> INITIAL_GSON.fromJson(json, ExploreRule::class.java)
                json.isJsonPrimitive -> INITIAL_GSON.fromJson(
                    json.asString,
                    ExploreRule::class.java
                )
                else -> null
            }
        }

    }

}