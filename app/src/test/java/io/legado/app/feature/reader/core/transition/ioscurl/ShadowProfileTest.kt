package io.legado.app.feature.reader.core.transition.ioscurl

import org.junit.Assert.*
import org.junit.Test
import io.legado.app.feature.reader.core.transition.ioscurl.mesh.*
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlValues

class ShadowProfileTest {
    @Test
    fun softerPenumbraLeavesBothSheetFacesAndUvsUntouched() {
        val mesh = CurlMesh()
        var changed = 0
        for (time in listOf(.2, .5, .8)) for (angle in listOf(-.6, 0.0, .6)) {
            val input = CurlMeshInput(CurlValues(time, 35.0, angle), 320.0, 458.0)
            val reference = mesh.build(input)
            val softer = mesh.build(input.copy(shadowSoftness = 1.35))
            assertEquals(reference.batches.size, softer.batches.size)
            reference.batches.zip(softer.batches).forEach { (before, after) ->
                assertEquals(before.mode, after.mode)
                assertEquals(before.count, after.count)
                assertArrayEquals(before.indices, after.indices)
                if (before.mode != 0) assertArrayEquals(before.vertices, after.vertices)
                else if (!before.vertices.contentEquals(after.vertices)) changed++
            }
        }
        assertTrue("Softness must change the shadow fringe", changed > 0)
    }
}
