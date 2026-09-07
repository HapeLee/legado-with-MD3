package io.legado.app.data.entities

import androidx.room.TypeConverter
import io.legado.app.core.platform.JsonCodec
import io.legado.app.data.entities.rule.BookInfoRule
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.data.entities.rule.ExploreRule
import io.legado.app.data.entities.rule.ReviewRule
import io.legado.app.data.entities.rule.SearchRule
import io.legado.app.data.entities.rule.TocRule

/**
 * [BookSource] 的 Room TypeConverter。
 *
 * 原实体内嵌的 `BookSource.Converters` 依赖 `com.google.gson`（JVM 三方库）无法进 commonMain，
 * 随实体下沉时改用 `core:platform` 的 [JsonCodec] 契约（字节级对齐 `INITIAL_GSON`）。
 *
 * 语义说明：原 app 侧用 `GSON`（额外注册了 6 个 rule JsonDeserializer），其中带
 * 「JSON 原始字符串回退」（旧书源里 rule 以纯字符串存储时先 `asString` 再反序列化）。
 * 改用 [JsonCodec.fromJsonObject] 后不再保留该回退：正常存库的 rule 均为 JSON 对象
 * （序列化侧 `toJson` 对 data class 恒产生 `{...}`），该回退仅影响极老版本手工
 * 构造的纯字符串规则，属低概率边界。反序列化失败返回 null（与原 `getOrNull()` 一致）。
 */
class BookSourceConverters {

    @TypeConverter
    fun exploreRuleToString(exploreRule: ExploreRule?): String =
        JsonCodec.toJson(exploreRule)

    @TypeConverter
    fun stringToExploreRule(json: String?): ExploreRule? =
        JsonCodec.fromJsonObject(json, ExploreRule::class)

    @TypeConverter
    fun searchRuleToString(searchRule: SearchRule?): String =
        JsonCodec.toJson(searchRule)

    @TypeConverter
    fun stringToSearchRule(json: String?): SearchRule? =
        JsonCodec.fromJsonObject(json, SearchRule::class)

    @TypeConverter
    fun bookInfoRuleToString(bookInfoRule: BookInfoRule?): String =
        JsonCodec.toJson(bookInfoRule)

    @TypeConverter
    fun stringToBookInfoRule(json: String?): BookInfoRule? =
        JsonCodec.fromJsonObject(json, BookInfoRule::class)

    @TypeConverter
    fun tocRuleToString(tocRule: TocRule?): String =
        JsonCodec.toJson(tocRule)

    @TypeConverter
    fun stringToTocRule(json: String?): TocRule? =
        JsonCodec.fromJsonObject(json, TocRule::class)

    @TypeConverter
    fun contentRuleToString(contentRule: ContentRule?): String =
        JsonCodec.toJson(contentRule)

    @TypeConverter
    fun stringToContentRule(json: String?): ContentRule? =
        JsonCodec.fromJsonObject(json, ContentRule::class)

    @TypeConverter
    fun stringToReviewRule(json: String?): ReviewRule? = null

    @TypeConverter
    fun reviewRuleToString(reviewRule: ReviewRule?): String = "null"
}
