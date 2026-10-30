package io.legado.app.data.repository.manga

import io.legado.app.domain.gateway.MangaModelSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MangaModelInstallerTest {
    @get:Rule val temporary = TemporaryFolder()
    private val bytes = "official-model".toByteArray()
    private fun spec(): MangaModelSpec {
        val reference = temporary.newFile().apply { writeBytes(bytes) }
        return MangaModelSpec("ocr", "ocr.bin", "https://example.invalid/model", bytes.size.toLong(), modelDigest(reference))
    }
    private fun source(data: ByteArray) = object : MangaModelSource {
        override val name = "ocr.bin"
        override suspend fun transfer(write: (ByteArray, Int) -> Unit) = write(data, data.size)
    }
    @Test fun `valid model publishes atomically and reports complete progress`() = runBlocking {
        val directory = temporary.newFolder()
        val progress = mutableListOf<Int>()
        installMangaModel(directory, spec(), source(bytes), progress::add)
        assertArrayEquals(bytes, directory.resolve("ocr.bin").readBytes())
        assertEquals(100, progress.last())
        assertEquals(listOf("ocr.bin"), directory.list()!!.toList())
    }
    @Test fun `bad digest preserves installed model and removes staging file`() = runBlocking {
        val directory = temporary.newFolder()
        directory.resolve("ocr.bin").writeText("existing")
        val invalid = bytes.copyOf().apply { this[0] = 0 }
        val result = runCatching { installMangaModel(directory, spec(), source(invalid)) {} }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("existing", directory.resolve("ocr.bin").readText())
        assertEquals(1, directory.list()!!.size)
    }
    @Test fun `oversized input rejected before publication`() = runBlocking {
        val directory = temporary.newFolder()
        assertTrue(runCatching { installMangaModel(directory, spec(), source(bytes + bytes)) {} }.isFailure)
        assertTrue(directory.list()!!.isEmpty())
    }
    @Test fun `cancelled import preserves installed model and removes staging file`() = runBlocking {
        val directory = temporary.newFolder()
        directory.resolve("ocr.bin").writeText("existing")
        val cancelled = object : MangaModelSource {
            override val name = "ocr.bin"
            override suspend fun transfer(write: (ByteArray, Int) -> Unit) {
                write(bytes, 2)
                throw CancellationException("cancel import")
            }
        }
        assertTrue(runCatching { installMangaModel(directory, spec(), cancelled) {} }.exceptionOrNull() is CancellationException)
        assertEquals("existing", directory.resolve("ocr.bin").readText())
        assertEquals(1, directory.list()!!.size)
    }
}
