/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard.services

import app.scrollguard.models.MediaBounds

/** Disjoint touch-shield rectangles covering app content without covering the input method. */
internal object ShieldGeometry {
    fun regions(viewport: MediaBounds, keyboard: MediaBounds?): List<MediaBounds> {
        if (viewport.width <= 0 || viewport.height <= 0) return emptyList()
        val cutout = keyboard?.intersect(viewport) ?: return listOf(viewport)
        return listOf(
            MediaBounds(viewport.left, viewport.top, viewport.right, cutout.top),
            MediaBounds(viewport.left, cutout.top, cutout.left, cutout.bottom),
            MediaBounds(cutout.right, cutout.top, viewport.right, cutout.bottom),
            MediaBounds(viewport.left, cutout.bottom, viewport.right, viewport.bottom),
        ).filter { it.width > 0 && it.height > 0 }
    }
}
