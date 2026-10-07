/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard.services

import app.scrollguard.models.InstagramProtectionMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

data class InstagramObservation(
    val timestampMillis: Long,
    val windowId: Int,
    val bounds: String,
    val rootState: String,
    val screen: String,
    val structuralNodes: List<String>,
)

data class ProtectionRuntimeState(
    val connected: Boolean = false,
    val lastInstagramCheck: String? = null,
    val preferencesApplied: Boolean = false,
    val instagramMode: InstagramProtectionMode = InstagramProtectionMode.FEED_LOCK,
    val instagramEnabled: Boolean = false,
    val foregroundPackage: String = "Unknown",
    val foregroundWindowId: Int = -1,
    val rootState: String = "Not checked",
    val screen: String = "Not checked",
    val overlay: String = "Removed",
    val checkedAtMillis: Long = 0,
    val lastAction: String = "None",
    val actionAtMillis: Long = 0,
    val lastInstagram: InstagramObservation? = null,
    val transitions: List<String> = emptyList(),
    val device: String = "",
    val overlayAttachmentCount: Long = 0,
)

/** Local structural metadata only. A historical check never asserts current enforcement. */
object ProtectionRuntime {
    private val mutableState = MutableStateFlow(ProtectionRuntimeState())
    val state = mutableState.asStateFlow()
    fun connected(value: Boolean) {
        mutableState.update { it.copy(connected = value,
            preferencesApplied = if (value) it.preferencesApplied else false,
            overlay = if (value) it.overlay else "Removed: service disconnected",
            screen = if (value) it.screen else "Service disconnected") }
    }
    fun device(description: String) { mutableState.update { it.copy(device = description) } }
    fun configured(mode: InstagramProtectionMode, enabled: Boolean) {
        mutableState.update { it.copy(preferencesApplied = true, instagramMode = mode, instagramEnabled = enabled) }
    }
    fun preferencesFailed() { mutableState.update { it.copy(preferencesApplied = false, screen = "Preferences could not load") } }
    fun checked(packageName: String?, windowId: Int, root: String, screen: String, overlay: String,
        observation: InstagramObservation? = null) {
        val now = System.currentTimeMillis()
        mutableState.update { previous ->
            val transition = "$now package=${packageName ?: "Unknown"} window=$windowId root=$root screen=$screen overlay=$overlay"
            val changed = previous.foregroundPackage != (packageName ?: "Unknown") || previous.screen != screen ||
                previous.overlay != overlay || previous.rootState != root
            previous.copy(foregroundPackage = packageName ?: "Unknown", foregroundWindowId = windowId,
                rootState = root, screen = screen, overlay = overlay, checkedAtMillis = now,
                lastInstagramCheck = observation?.screen ?: previous.lastInstagramCheck,
                lastInstagram = observation ?: previous.lastInstagram,
                transitions = if (changed) (previous.transitions + transition).takeLast(16) else previous.transitions)
        }
    }
    fun action(description: String) {
        Timber.d("Protection action: %s", description)
        val now = System.currentTimeMillis()
        mutableState.update { it.copy(lastAction = description, actionAtMillis = now,
            transitions = (it.transitions + "$now action=$description").takeLast(16)) }
    }
    fun overlayAttached() { mutableState.update { it.copy(overlayAttachmentCount = it.overlayAttachmentCount + 1) } }
    fun report(): String = state.value.let { s -> buildString {
        appendLine("Scroll Guard ${app.scrollguard.BuildConfig.VERSION_NAME} (${app.scrollguard.BuildConfig.VERSION_CODE})")
        appendLine("${s.device}; report at ${System.currentTimeMillis()} (Unix milliseconds)")
        appendLine("Service connected=${s.connected}; preferences applied=${s.preferencesApplied}")
        appendLine("Instagram enabled=${s.instagramEnabled}; mode=${s.instagramMode}")
        appendLine("Current foreground=${s.foregroundPackage}; window=${s.foregroundWindowId}; checked=${s.checkedAtMillis}")
        appendLine("Current root=${s.rootState}; classification=${s.screen}; overlay=${s.overlay}")
        appendLine("Shield attachment requests in this process=${s.overlayAttachmentCount}")
        appendLine("Last action=${s.lastAction}; at=${s.actionAtMillis}")
        appendLine("Attachment is observed locally. Touch enforcement on this phone is not measured by this report.")
        s.lastInstagram?.let {
            appendLine("Last Instagram sample (historical): at=${it.timestampMillis} window=${it.windowId} bounds=${it.bounds}")
            appendLine("Root=${it.rootState}; classification=${it.screen}")
            it.structuralNodes.forEach { row -> appendLine(row) }
        }
        appendLine("Recent transitions:")
        s.transitions.forEach { appendLine(it) }
        appendLine("No message text, labels, captions, usernames or keyboard content included. In memory until copied.")
    } }
}
