package io.legado.app.data.entities

import androidx.room.TypeConverter
import io.legado.app.data.entities.rule.BookInfoRule
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.data.entities.rule.ExploreRule
import io.legado.app.data.entities.rule.ReviewRule
import io.legado.app.data.entities.rule.SearchRule
import io.legado.app.data.entities.rule.TocRule
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject

/**
 * [BookSource] 的 Room TypeConverter。
 *
 * 原实体内嵌的 `BookSource.Converters` 依赖 `com.google.gson`（JVM 三方库）无法进 commonMain，
 * 故随实体下沉时外置到本文件，并在 `AppDatabase` 上以全局 `@TypeConverters` 声明。
 */
class BookSourceConverters {

    @TypeConverter
    fun exploreRuleToString(exploreRule: ExploreRule?): String =
        GSON.toJson(exploreRule)

    @TypeConverter
    fun stringToExploreRule(json: String?) =
        GSON.fromJsonObject<ExploreRule>(json).getOrNull()

    @TypeConverter
    fun searchRuleToString(searchRule: SearchRule?): String =
        GSON.toJson(searchRule)

    @TypeConverter
    fun stringToSearchRule(json: String?) =
        GSON.fromJsonObject<SearchRule>(json).getOrNull()

    @TypeConverter
    fun bookInfoRuleToString(bookInfoRule: BookInfoRule?): String =
        GSON.toJson(bookInfoRule)

    @TypeConverter
    fun stringToBookInfoRule(json: String?) =
        GSON.fromJsonObject<BookInfoRule>(json).getOrNull()

    @TypeConverter
    fun tocRuleToString(tocRule: TocRule?): String =
        GSON.toJson(tocRule)

    @TypeConverter
    fun stringToTocRule(json: String?) =
        GSON.fromJsonObject<TocRule>(json).getOrNull()

    @TypeConverter
    fun contentRuleToString(contentRule: ContentRule?): String =
        GSON.toJson(contentRule)

    @TypeConverter
    fun stringToContentRule(json: String?) =
        GSON.fromJsonObject<ContentRule>(json).getOrNull()

    @TypeConverter
    fun stringToReviewRule(json: String?): ReviewRule? = null

    @TypeConverter
    fun reviewRuleToString(reviewRule: ReviewRule?): String = "null"

}
