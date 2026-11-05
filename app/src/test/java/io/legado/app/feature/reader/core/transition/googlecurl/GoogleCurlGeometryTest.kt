package io.legado.app.feature.reader.core.transition.googlecurl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleCurlGeometryTest {
    @Test fun flatStartAndOffscreenFinishAcrossTheSupportedRadiusRange() {
        val point = FloatArray(2)
        for (radius in listOf(.005f, .08f, .3f)) {
            val geometry = GoogleCurlGeometry(radius)
            geometry.update(0f)
            for (u in listOf(0f, .25f, .5f, .75f, 1f)) {
                assertEquals(255, geometry.project(u, .25f, 400f, 800f, point, 0))
                assertEquals(400 * u, point[0], .001f)
                assertEquals(200f, point[1], .001f)
            }
            geometry.update(1f)
            geometry.project(1f, .5f, 400f, 800f, point, 0)
            assertTrue("last edge must leave the viewport", point[0] <= .001f)
        }
    }

    @Test fun edgeTravelsContinuouslyLeftAndAllVerticesRemainFinite() {
        val point = FloatArray(2)
        for (radius in listOf(.005f, .08f, .3f)) {
            val geometry = GoogleCurlGeometry(radius)
            var previous = 401f
            for (step in 0..100) {
                geometry.update(step / 100f)
                geometry.project(1f, .5f, 400f, 800f, point, 0)
                assertTrue(point[0] < previous)
                previous = point[0]
                for (column in 0..64) {
                    val brightness = geometry.project(column / 64f, .75f, 400f, 800f, point, 0)
                    assertTrue(point.all { it.isFinite() })
                    assertTrue(brightness in 0..255)
                }
            }
        }
    }

    @Test fun waveHasPerspectiveAndLightingWithTheSameProjectionForEveryRow() {
        val geometry = GoogleCurlGeometry(.08f)
        val point = FloatArray(4)
        geometry.update(.5f)
        val topBrightness = geometry.project(.5f, .25f, 400f, 800f, point, 0)
        val bottomBrightness = geometry.project(.5f, .75f, 400f, 800f, point, 2)
        assertEquals(point[0], point[2], 0f)
        assertEquals(topBrightness, bottomBrightness)
        assertTrue(topBrightness < 255)
        assertTrue(point[1] < 200f)
        assertEquals(800f, point[1] + point[3], .001f)
        geometry.update(0f)
        assertEquals(255, geometry.project(.5f, .25f, 400f, 800f, point, 0))
        assertEquals(200f, point[0], .001f)
        assertEquals(200f, point[1], .001f)
    }

    @Test fun progressClampsToTheEndpoints() {
        val geometry = GoogleCurlGeometry(.08f)
        val point = FloatArray(2)
        geometry.update(-2f)
        geometry.project(1f, .5f, 400f, 800f, point, 0)
        assertEquals(400f, point[0], .001f)
        geometry.update(2f)
        geometry.project(1f, .5f, 400f, 800f, point, 0)
        assertTrue(point[0] <= .001f)
    }
}
