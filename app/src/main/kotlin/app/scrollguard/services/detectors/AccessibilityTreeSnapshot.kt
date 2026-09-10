/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.services.detectors

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
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
)

internal class AccessibilityTreeSnapshot private constructor(
    private val nodes: List<NodeSignal>,
    val truncated: Boolean,
) {
    fun hasId(vararg fragments: String): Boolean = nodes.any { node ->
        fragments.any { fragment -> node.id.contains(fragment.normalized()) }
    }

    fun hasSelectedId(vararg fragments: String): Boolean = nodes.any { node ->
        node.selected && fragments.any { fragment -> node.id.contains(fragment.normalized()) }
    }

    fun hasLabel(vararg fragments: String): Boolean = nodes.any { node ->
        node.visible && fragments.any { fragment -> node.label.contains(fragment.normalized()) }
    }

    fun hasSelectedLabel(vararg fragments: String): Boolean = nodes.any { node ->
        node.selected && fragments.any { fragment -> node.label.contains(fragment.normalized()) }
    }

    fun labelGroupCount(vararg groups: Set<String>): Int = groups.count { group ->
        nodes.any { node ->
            node.visible && group.any { token -> node.label.contains(token.normalized()) }
        }
    }

    fun hasTallScrollableNode(): Boolean = nodes.any { node ->
        node.visible && node.scrollable && node.height > node.width
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
        private const val MAX_NODES = 350

        fun from(root: AccessibilityNodeInfo): AccessibilityTreeSnapshot {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            val signals = mutableListOf<NodeSignal>()
            queue.add(root)

            while (queue.isNotEmpty() && signals.size < MAX_NODES) {
                val node = queue.removeFirst()
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
