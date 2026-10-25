/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard.services

import app.scrollguard.models.MediaBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShieldGeometryTest {
    private val viewport = MediaBounds(0, 24, 720, 1560)

    @Test fun noKeyboardKeepsTheEntireAppCovered() {
        assertEquals(listOf(viewport), ShieldGeometry.regions(viewport, null))
        assertCoverage(viewport, null)
    }

    @Test fun dockedKeyboardLeavesOnlyTheTopShield() {
        val keyboard = MediaBounds(0, 900, 720, 1600)
        assertEquals(listOf(MediaBounds(0, 24, 720, 900)), ShieldGeometry.regions(viewport, keyboard))
        assertCoverage(viewport, keyboard)
    }

    @Test fun floatingKeyboardLeavesFourDisjointShieldRegions() {
        val keyboard = MediaBounds(180, 700, 540, 1150)
        assertEquals(listOf(
            MediaBounds(0, 24, 720, 700),
            MediaBounds(0, 700, 180, 1150),
            MediaBounds(540, 700, 720, 1150),
            MediaBounds(0, 1150, 720, 1560),
        ), ShieldGeometry.regions(viewport, keyboard))
        assertCoverage(viewport, keyboard)
    }

    @Test fun narrowKeyboardDoesNotExposeFeedBesideOrBelowIt() {
        val keyboard = MediaBounds(300, 900, 420, 1200)
        val regions = ShieldGeometry.regions(viewport, keyboard)
        assertEquals(4, regions.size)
        assertTrue(regions.any { contains(it, 20, 1000) })
        assertTrue(regions.any { contains(it, 700, 1000) })
        assertTrue(regions.any { contains(it, 350, 1400) })
        assertFalse(regions.any { contains(it, 350, 1000) })
        assertCoverage(viewport, keyboard)
    }

    @Test fun keyboardPartiallyOutsideAppIsClippedBeforePartitioning() {
        for (keyboard in listOf(
            MediaBounds(-100, 700, 300, 1700),
            MediaBounds(400, -100, 900, 600),
            MediaBounds(-100, 600, 900, 1000),
        )) assertCoverage(viewport, keyboard)
    }

    @Test fun keyboardOutsideAppDoesNotRemoveAnyShield() {
        for (keyboard in listOf(
            MediaBounds(800, 700, 1000, 1200),
            MediaBounds(0, 1560, 720, 1800),
            MediaBounds(0, -500, 720, 24),
        )) {
            assertEquals(listOf(viewport), ShieldGeometry.regions(viewport, keyboard))
            assertCoverage(viewport, keyboard)
        }
    }

    @Test fun keyboardCoveringWholeAppNeedsNoShieldOverIt() {
        for (keyboard in listOf(viewport, MediaBounds(-100, 0, 900, 1700))) {
            assertTrue(ShieldGeometry.regions(viewport, keyboard).isEmpty())
            assertCoverage(viewport, keyboard)
        }
    }

    @Test fun emptyKeyboardDoesNotCreateATouchGap() {
        for (keyboard in listOf(MediaBounds(200, 600, 200, 1000), MediaBounds(200, 600, 400, 600))) {
            assertEquals(listOf(viewport), ShieldGeometry.regions(viewport, keyboard))
            assertCoverage(viewport, keyboard)
        }
    }

    @Test fun invalidViewportProducesNoInvalidWindowRegions() {
        assertTrue(ShieldGeometry.regions(MediaBounds(0, 0, 0, 100), null).isEmpty())
        assertTrue(ShieldGeometry.regions(MediaBounds(0, 100, 100, 50), null).isEmpty())
    }

    @Test fun rotatedAndOffsetAppBoundsArePartitionedInScreenCoordinates() {
        val landscape = MediaBounds(36, 0, 1560, 720)
        assertCoverage(landscape, MediaBounds(36, 420, 1560, 720))
        assertCoverage(landscape, MediaBounds(1000, 200, 1500, 650))
        assertEquals(MediaBounds(36, 0, 1560, 420),
            ShieldGeometry.regions(landscape, MediaBounds(36, 420, 1560, 720)).single())
    }

    @Test fun everyPixelOutsideFloatingKeyboardIsCoveredExactlyOnce() {
        // Exhaustive small geometry catches edge and one-pixel gaps without Android APIs.
        val small = MediaBounds(2, 3, 16, 18)
        for (keyboard in listOf(MediaBounds(5, 8, 12, 14), MediaBounds(1, 10, 9, 20),
            MediaBounds(8, 2, 18, 9), MediaBounds(7, 7, 8, 8))) {
            val regions = ShieldGeometry.regions(small, keyboard)
            for (x in small.left until small.right) for (y in small.top until small.bottom) {
                val expected = if (contains(keyboard, x, y)) 0 else 1
                assertEquals("Pixel $x,$y with keyboard $keyboard", expected,
                    regions.count { contains(it, x, y) })
            }
            assertCoverage(small, keyboard)
        }
    }

    private fun contains(bounds: MediaBounds, x: Int, y: Int): Boolean =
        x >= bounds.left && x < bounds.right && y >= bounds.top && y < bounds.bottom

    private fun area(bounds: MediaBounds): Long = bounds.width.toLong() * bounds.height

    private fun assertCoverage(app: MediaBounds, keyboard: MediaBounds?) {
        val regions = ShieldGeometry.regions(app, keyboard)
        val cutout = keyboard?.intersect(app)
        assertTrue(regions.size <= 4)
        assertEquals(area(app) - (cutout?.let(::area) ?: 0L), regions.sumOf(::area))
        regions.forEachIndexed { index, region ->
            assertTrue(region.width > 0 && region.height > 0)
            assertEquals(region, region.intersect(app))
            if (cutout != null) assertEquals(null, region.intersect(cutout))
            regions.drop(index + 1).forEach { other -> assertEquals(null, region.intersect(other)) }
        }
    }
}
