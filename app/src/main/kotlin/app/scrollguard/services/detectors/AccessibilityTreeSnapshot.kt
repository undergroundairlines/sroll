/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.services.detectors

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import app.scrollguard.models.MediaBounds
import java.util.ArrayDeque
import java.util.Locale

internal data class NodeSignal(
    val id: String,
    val label: String,
    val selected: Boolean,
    val scrollable: Boolean,
    val visible: Boolean,
    val width: Int,
    val height: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

internal class AccessibilityTreeSnapshot internal constructor(
    private val nodes: List<NodeSignal>,
    val truncated: Boolean,
) {
    fun hasId(vararg fragments: String): Boolean = nodes.any { node ->
        fragments.any { fragment -> node.id.contains(fragment.normalized()) }
    }

    fun hasVisibleId(vararg fragments: String): Boolean = nodes.any { node ->
        node.visible && fragments.any { fragment -> node.id.contains(fragment.normalized()) }
    }

    fun hasSelectedId(vararg fragments: String): Boolean = nodes.any { node ->
        node.visible && node.selected && fragments.any { fragment ->
            node.id.contains(fragment.normalized())
        }
    }

    fun hasLabel(vararg fragments: String): Boolean = nodes.any { node ->
        node.visible && fragments.any { fragment -> node.label.contains(fragment.normalized()) }
    }

    fun hasSelectedLabel(vararg fragments: String): Boolean = nodes.any { node ->
        node.visible && node.selected && fragments.any { fragment ->
            node.label.contains(fragment.normalized())
        }
    }

    fun labelGroupCount(vararg groups: Set<String>): Int = groups.count { group ->
        nodes.any { node ->
            node.visible && group.any { token -> node.label.contains(token.normalized()) }
        }
    }

    fun hasTallScrollableNode(): Boolean = nodes.any { node ->
        node.visible && node.scrollable && node.height > node.width
    }

    /** Only an on-screen Reel video surface may be covered; toolbar and navigation are clipped. */
    fun homeReelMediaBounds(screenWidth: Int, screenHeight: Int): MediaBounds? {
        val reelMedia = listOf("clips_video_container", "clips_media_component", "clips_single_media_component")
        val navigationTop = nodes.filter { node ->
            node.visible && node.top > screenHeight / 2 &&
                node.id.substringAfterLast('/') in setOf("feed_tab", "home_tab", "clips_tab", "reels_tab",
                    "direct_tab", "search_tab", "profile_tab") && node.height > 0
        }.minOfOrNull { it.top } ?: screenHeight
        val toolbarBottom = nodes.filter { node ->
            node.visible && node.bottom < screenHeight / 3 && node.height > 0 &&
                node.id.substringAfterLast('/') in setOf("action_bar", "action_bar_container")
        }.maxOfOrNull { it.bottom } ?: 0
        val viewport = MediaBounds(0, toolbarBottom, screenWidth, navigationTop)
        val candidate = nodes.asSequence()
            .filter { it.visible && it.id.substringAfterLast('/') in reelMedia }
            .mapNotNull { node ->
                val clipped = MediaBounds(node.left, node.top, node.right, node.bottom).intersect(viewport)
                clipped?.takeIf { it.width >= screenWidth * 0.55f && it.height >= screenHeight * 0.14f }
                    ?.let { reelMedia.indexOf(node.id.substringAfterLast('/')) to it }
            }
            // Prefer the video surface to a broad Reel container that also includes navigation.
            .sortedWith(compareBy<Pair<Int, MediaBounds>> { it.first }
                .thenByDescending { it.second.width.toLong() * it.second.height })
            .firstOrNull()
            ?: return null
        return candidate.second
    }

    /** Resource IDs are developer-defined UI names and do not contain captions or usernames. */
    fun diagnosticIdentifiers(): List<String> {
        val ids = nodes.asSequence()
            .map { it.id.substringAfterLast('/') }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        val usefulTokens = listOf("reel", "clip", "short", "tab", "player", "progress")
        return (ids.filter { id -> usefulTokens.any { token -> id.contains(token) } } + ids)
            .distinct()
            .take(24)
    }

    companion object {
        private const val MAX_NODES = 1200

        fun from(vararg roots: AccessibilityNodeInfo): AccessibilityTreeSnapshot {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            val signals = mutableListOf<NodeSignal>()
            val visited = HashSet<AccessibilityNodeInfo>()
            roots.forEach(queue::addLast)

            while (queue.isNotEmpty() && signals.size < MAX_NODES) {
                val node = queue.removeFirst()
                if (!visited.add(node)) continue
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val label = sequenceOf(node.contentDescription, node.text)
                    .filterNotNull()
                    .joinToString(" ")
                    .normalized()

                signals += NodeSignal(
                    id = node.viewIdResourceName.orEmpty().normalized(),
                    label = label,
                    selected = node.isSelected,
                    scrollable = node.isScrollable,
                    visible = node.isVisibleToUser,
                    width = bounds.width(),
                    height = bounds.height(),
                    left = bounds.left,
                    top = bounds.top,
                    right = bounds.right,
                    bottom = bounds.bottom,
                )

                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let(queue::addLast)
                }
            }

            return AccessibilityTreeSnapshot(
                nodes = signals,
                truncated = queue.isNotEmpty(),
            )
        }
    }
}

private fun String.normalized(): String = lowercase(Locale.ROOT)
