package io.legado.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ComposeEngineTest {
    @Test
    fun persistedStylesPreserveCaseInsensitiveMiuixSelection() {
        listOf("miuix", "Miuix", "MIUIX").forEach { value ->
            assertEquals(value, ComposeEngine.Miuix, ThemeResolver.resolveComposeEngine(value))
        }
    }

    @Test
    fun missingOrUnknownStylesKeepTheMaterialFallback() {
        listOf("material", "Material", "", "unknown", " miuix ").forEach { value ->
            assertEquals(value, ComposeEngine.Material, ThemeResolver.resolveComposeEngine(value))
        }
    }

    @Test
    fun persistedMochiStyleIsCaseInsensitive() {
        listOf("mochi", "Mochi", "MOCHI").forEach { value ->
            assertEquals(value, ComposeEngine.Mochi, ThemeResolver.resolveComposeEngine(value))
        }
    }
}
