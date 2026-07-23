package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationDictionaryPolicyTest {

    @Test
    fun `normalization ignores whitespace case and honorific period`() {
        assertEquals("mr jack", TranslationDictionaryPolicy.normalizeOriginal("  Mr.   Jack "))
        assertEquals("mr jack", TranslationDictionaryPolicy.normalizeOriginal("mr jack"))
    }

    @Test
    fun `existing translation wins normalized conflict`() {
        val existing = listOf(DictPair("Jack", "杰克"))

        val merged = TranslationDictionaryPolicy.mergeDiscoveredPairs(
            existing,
            listOf(DictPair("jack", "杰克逊")),
        )

        assertEquals(existing, merged)
    }

    @Test
    fun `base name replaces an existing honorific form`() {
        val merged = TranslationDictionaryPolicy.mergeDiscoveredPairs(
            listOf(DictPair("Dr. Smith", "史密斯博士")),
            listOf(DictPair("Smith", "史密斯")),
        )

        assertEquals(listOf(DictPair("Smith", "史密斯")), merged)
    }

    @Test
    fun `honorific form is ignored when base name exists`() {
        val existing = listOf(DictPair("Jack", "杰克"))
        val merged = TranslationDictionaryPolicy.mergeDiscoveredPairs(
            existing,
            listOf(DictPair("Mr. Jack", "杰克先生")),
        )
        assertEquals(existing, merged)
    }

    @Test
    fun `ordinary containing terms are both preserved`() {
        val merged = TranslationDictionaryPolicy.mergeDiscoveredPairs(
            listOf(DictPair("York", "约克")),
            listOf(DictPair("New York", "纽约")),
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun `proper noun validation rejects lowercase common terms`() {
        assertTrue(TranslationDictionaryPolicy.isValidNewOriginal("Jack"))
        assertTrue(TranslationDictionaryPolicy.isValidNewOriginal("Mr. Jack"))
        assertTrue(TranslationDictionaryPolicy.isValidNewOriginal("New York"))
        assertTrue(TranslationDictionaryPolicy.isValidNewOriginal("iPhone"))
        assertTrue(TranslationDictionaryPolicy.isValidNewOriginal("NASA"))
        assertTrue(TranslationDictionaryPolicy.isValidNewOriginal("The A"))
        assertFalse(TranslationDictionaryPolicy.isValidNewOriginal("apple"))
        assertFalse(TranslationDictionaryPolicy.isValidNewOriginal("beautiful house"))
        assertFalse(TranslationDictionaryPolicy.isValidNewOriginal("A"))
        assertFalse(TranslationDictionaryPolicy.isValidNewOriginal("123"))
        assertFalse(TranslationDictionaryPolicy.isValidNewOriginal("..."))
    }

    @Test
    fun `relevance uses word boundaries and filters generic split tokens`() {
        val pairs = listOf(
            DictPair("Ann", "安"),
            DictPair("Mr. Jack", "杰克"),
            DictPair("New York", "纽约"),
            DictPair("The A", "A"),
        )

        val relevant = TranslationDictionaryPolicy.selectRelevantPairs(
            pairs,
            "Anna met Jack in York. The answer was a surprise.",
        )

        assertEquals(listOf("Mr. Jack", "New York"), relevant.map { it.original })
    }
}
