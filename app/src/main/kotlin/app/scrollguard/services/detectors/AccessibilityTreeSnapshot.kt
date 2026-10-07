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
    val parentIndex: Int = -1,
    val editable: Boolean = false,
    val className: String = "",
)

internal class AccessibilityTreeSnapshot internal constructor(
    private val nodes: List<NodeSignal>,
    val truncated: Boolean,
) {
    val nodeCount: Int get() = nodes.size

    private fun NodeSignal.onScreen(viewport: MediaBounds): Boolean = visible &&
        MediaBounds(left, top, right, bottom).intersect(viewport) != null

    fun hasExactOnScreenId(viewport: MediaBounds, vararg ids: String): Boolean = nodes.any {
        it.onScreen(viewport) && it.id.substringAfterLast('/') in ids
    }

    fun hasExactContentId(viewport: MediaBounds, minHeightFraction: Float, vararg ids: String): Boolean = nodes.any {
        val bounds = MediaBounds(it.left, it.top, it.right, it.bottom).intersect(viewport)
        it.onScreen(viewport) && it.id.substringAfterLast('/') in ids && bounds != null &&
            bounds.width >= viewport.width * 0.55f && bounds.height >= viewport.height * minHeightFraction
    }

    /** Composer + history must belong to one visible content subtree, not cached parallel pages. */
    fun hasConversation(viewport: MediaBounds): Boolean {
        val composers = nodes.withIndex().filter { (_, node) ->
            node.onScreen(viewport) && node.editable && node.id.substringAfterLast('/') in COMPOSER_IDS
        }
        return composers.any { (composerIndex, composer) ->
            nodes.withIndex().any { (historyIndex, history) ->
                val bounds = MediaBounds(history.left, history.top, history.right, history.bottom).intersect(viewport)
                history.onScreen(viewport) && history.id.substringAfterLast('/') in HISTORY_IDS &&
                    bounds != null && bounds.width >= viewport.width * 0.55f && bounds.height >= 48 &&
                    history.top < composer.bottom && sharedContentParent(composerIndex, historyIndex, viewport)
            }
        }
    }

    private fun sharedContentParent(first: Int, second: Int, viewport: MediaBounds): Boolean {
        fun path(index: Int): List<Int> {
            val result = mutableListOf<Int>()
            var current = index
            while (current in nodes.indices && current !in result) {
                result.add(current)
                current = nodes[current].parentIndex
            }
            return result
        }
        val firstPath = path(first)
        val secondPath = path(second)
        val common = firstPath.drop(1).firstOrNull { it in secondPath.drop(1) } ?: return false
        if (nodes[common].parentIndex < 0 || !nodes[common].onScreen(viewport) ||
            nodes[common].className.contains("ViewPager", ignoreCase = true)) return false
        if (firstPath.takeWhile { it != common }.any { !nodes[it].onScreen(viewport) } ||
            secondPath.takeWhile { it != common }.any { !nodes[it].onScreen(viewport) }) return false
        val firstBranch = nodes[firstPath[firstPath.indexOf(common) - 1]]
        val secondBranch = nodes[secondPath[secondPath.indexOf(common) - 1]]
        // Two large sibling pages are not one chat, even below DecorView/content wrappers.
        return !(firstBranch.height >= viewport.height * 0.50f && secondBranch.height >= viewport.height * 0.50f)
    }

    /** No text, labels or account content; enough structure to diagnose from the phone. */
    fun structuralReport(): List<String> = nodes.withIndex().filter { it.value.id.isNotBlank() }
        .take(160).map { (index, n) ->
            "$index parent=${n.parentIndex} ${n.id.substringAfterLast('/')} " +
                "bounds=${n.left},${n.top},${n.right},${n.bottom} visible=${n.visible} " +
                "selected=${n.selected} editable=${n.editable} scrollable=${n.scrollable}"
        }
    fun hasId(vararg fragments: String): Boolean = nodes.any { node ->
        fragments.any { fragment -> node.id.contains(fragment.normalized()) }
    }

    fun hasVisibleId(vararg fragments: String): Boolean = nodes.any { node ->
        node.visible && fragments.any { fragment -> node.id.contains(fragment.normalized()) }
    }

    fun hasOnScreenId(viewport: MediaBounds, vararg fragments: String): Boolean = nodes.any { node ->
        node.visible && fragments.any { node.id.substringAfterLast('/').startsWith(it.normalized()) } &&
            MediaBounds(node.left, node.top, node.right, node.bottom).intersect(viewport) != null
    }

    fun hasVisibleContentId(viewport: MediaBounds, minHeightFraction: Float,
        vararg fragments: String): Boolean = nodes.any { node ->
        val visibleBounds = MediaBounds(node.left, node.top, node.right, node.bottom).intersect(viewport)
        node.visible && visibleBounds != null && visibleBounds.width >= viewport.width * 0.55f &&
            visibleBounds.height >= viewport.height * minHeightFraction &&
            fragments.any { node.id.substringAfterLast('/').startsWith(it.normalized()) }
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
        val COMPOSER_IDS = setOf("row_thread_composer_edittext", "direct_thread_composer_edittext",
            "message_composer_edit_text", "message_composer")
        val HISTORY_IDS = setOf("message_list", "direct_thread_message_list", "direct_thread_message_list_recycler_view",
            "direct_thread_recycler_view", "direct_thread_recyclerview", "direct_thread_list")

        fun from(vararg roots: AccessibilityNodeInfo, includeLabels: Boolean = true): AccessibilityTreeSnapshot {
            val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
            val signals = mutableListOf<NodeSignal>()
            val visited = HashSet<AccessibilityNodeInfo>()
            roots.forEach { queue.addLast(it to -1) }
            var incomplete = false

            while (queue.isNotEmpty() && signals.size < MAX_NODES) {
                val (node, parentIndex) = queue.removeFirst()
                if (!visited.add(node)) continue
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val label = if (includeLabels) sequenceOf(node.contentDescription, node.text)
                    .filterNotNull()
                    .joinToString(" ")
                    .normalized() else ""

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
                    parentIndex = parentIndex,
                    editable = node.isEditable,
                    className = node.className?.toString().orEmpty(),
                )

                for (index in 0 until node.childCount) {
                    val child = node.getChild(index)
                    if (child == null) incomplete = true else queue.addLast(child to signals.lastIndex)
                }
            }

            return AccessibilityTreeSnapshot(
                nodes = signals,
                truncated = incomplete || queue.isNotEmpty(),
            )
        }
    }
}

private fun String.normalized(): String = lowercase(Locale.ROOT)
