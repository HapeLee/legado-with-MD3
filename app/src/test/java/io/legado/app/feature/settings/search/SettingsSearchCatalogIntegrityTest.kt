package io.legado.app.feature.settings.search

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** A missing descriptor crashes a page; a missing anchor makes a search result unreachable. */
class SettingsSearchCatalogIntegrityTest {
    @Test
    fun `fixed search descriptors and page anchors cover each other`() {
        val sourceRoot = sequenceOf(File("src/main/java"), File("app/src/main/java"))
            .first { it.isDirectory }
        val anchorPattern = Regex("""\bSearchableSetting\(\s*"([^"]+)"""")
        val anchors = sourceRoot.walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file -> anchorPattern.findAll(file.readText()).map { it.groupValues[1] } }
            .toSet()
        val descriptors = SettingsSearchCatalog.items.map { it.id }.toSet()

        assertTrue("Search entries without page anchors: ${descriptors - anchors}", anchors.containsAll(descriptors))
        assertTrue("Page anchors without search descriptors: ${anchors - descriptors}", descriptors.containsAll(anchors))
    }
}
