package io.legado.app.data.repository.manga

import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import android.icu.text.BreakIterator
import android.os.Build
import android.text.TextPaint
import java.util.Locale

internal enum class MangaGlyphDirection { UPRIGHT, SIDEWAYS, COMBINED }
internal data class MangaTextUnit(val text: String, val display: String = text, val direction: MangaGlyphDirection = MangaGlyphDirection.UPRIGHT)

internal object MangaVerticalText {
    private const val forms = "、︑。︒，︐：︓；︔！︕？︖（︵）︶〔︹〕︺【︻】︼《︽》︾〈︿〉﹀「﹁」﹂『﹃』﹄［﹇］﹈…︙—︱"
    private val verticalForms = forms.chunked(2).associate { it[0] to it[1] }
    val noStart = "、。，．：；？！.,:;!?)]}）］｝〕〉》」』】〙〗〟’”ー々ぁぃぅぇぉっゃゅょァィゥェォッャュョヵヶ"
    val noEnd = "([{（［｛〔〈《「『【〘〖〝‘“"

    fun graphemes(text: String): List<String> {
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(text)
        val result = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            result += text.substring(start, end)
            start = end; end = iterator.next()
        }
        return result
    }

    private fun rotated(text: String): Boolean {
        val code = text.codePointAt(0)
        if ('\uFE0F' in text || '\u200D' in text) return false // emoji presentation stays upright as one cluster
        if (Build.VERSION.SDK_INT >= 29) {
            val value = UCharacter.getIntPropertyValue(code, UProperty.VERTICAL_ORIENTATION)
            return value == UCharacter.VerticalOrientation.ROTATED || value == UCharacter.VerticalOrientation.TRANSFORMED_ROTATED
        }
        // Android 26–28 ICU predates the property; use the bundled UCD data instead.
        return !MangaUnicodeVertical.upright(code)
    }

    fun units(text: String, vertical: Boolean, paint: TextPaint): List<MangaTextUnit> {
        val clusters = graphemes(text.replace("\r\n", "\n").replace('\r', '\n'))
        val result = mutableListOf<MangaTextUnit>()
        var i = 0
        while (i < clusters.size) {
            val cluster = clusters[i++]
            if (cluster == "\n" || cluster.isBlank()) { result += MangaTextUnit(cluster); continue }
            val code = cluster.codePointAt(0)
            val word = Character.isLetterOrDigit(code) && code < 0x2e80 && (!vertical || rotated(cluster))
            if (word) {
                var run = cluster
                while (i < clusters.size && clusters[i] != "\n") {
                    val next = clusters[i].codePointAt(0)
                    if (Character.isLetterOrDigit(next) && next < 0x2e80 && (!vertical || rotated(clusters[i]))) run += clusters[i++]
                    else if (clusters[i] == "." && i + 1 < clusters.size && run.last().isDigit() && clusters[i + 1].first().isDigit()) run += clusters[i++]
                    else break
                }
                val combined = vertical && run.length <= 2 && run.all { it in '0'..'9' }
                result += MangaTextUnit(run, direction = if (!vertical) MangaGlyphDirection.UPRIGHT
                    else if (combined) MangaGlyphDirection.COMBINED else MangaGlyphDirection.SIDEWAYS)
            } else {
                val form = cluster.singleOrNull()?.let { verticalForms[it]?.toString() }
                val transformed = vertical && form != null && paint.hasGlyph(form)
                result += MangaTextUnit(cluster, if (transformed) checkNotNull(form) else cluster,
                    if (vertical && !transformed && (rotated(cluster) || form != null)) MangaGlyphDirection.SIDEWAYS else MangaGlyphDirection.UPRIGHT)
            }
        }
        return result
    }

    fun legalBreak(units: List<MangaTextUnit>, start: Int, end: Int): Boolean {
        val first = units.subList(start, end).firstOrNull { !it.text.isBlank() }?.text ?: return false
        val last = units.subList(start, end).lastOrNull { !it.text.isBlank() }?.text ?: return false
        if (first.first() in noStart || last.last() in noEnd) return false
        // A number and its unit stay together (including common CJK units).
        if (end < units.size && last.last().isDigit() && units[end].text.firstOrNull()?.let {
                it.isLetter() || it in "%℃°元年月日时分秒米公里厘米毫米千克公斤克吨个岁倍万亿"
            } == true) return false
        return true
    }
}
