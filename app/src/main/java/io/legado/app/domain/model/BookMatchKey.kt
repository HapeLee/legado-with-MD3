package io.legado.app.domain.model

import io.legado.app.data.entities.readRecord.ReadRecordIdentity
import java.text.Normalizer

/**
 * 「是否算同一部作品」的比对键。
 *
 * 加入书架查重、阅读记录归属确认都依赖这个判断，必须共用同一份实现：如果一边按规范化比较、
 * 另一边按原始字符串比较，就会出现「查重说有两本、归属确认却以为只有一本」这种自相矛盾的结论。
 *
 * 规则：先做 NFKC（不同书源经常给出全角/半角混排的书名），再沿用 [ReadRecordIdentity] 折叠空白。
 * 这里只用于比对，不改阅读记录本身的聚合键。
 */
object BookMatchKey {

    fun of(value: String): String =
        ReadRecordIdentity.bookName(Normalizer.normalize(value, Normalizer.Form.NFKC))

    /** 任一方作者为空时视为可能同一部作品（旧记录的作者常缺失）。 */
    fun authorCompatible(left: String, right: String): Boolean =
        left.isBlank() || right.isBlank() || left == right
}
