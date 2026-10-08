/* Copyright 2025 Atick Faisal. Modifications copyright 2026 Scroll Guard contributors.
 * Licensed under the Apache License, Version 2.0. */
package app.scrollguard.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import app.scrollguard.models.BlockAction
import app.scrollguard.models.DetectionActionStatus
import app.scrollguard.models.DetectionResult
import app.scrollguard.models.InstagramProtectionMode
import app.scrollguard.models.MediaBounds
import app.scrollguard.services.detectors.AccessibilityTreeSnapshot
import app.scrollguard.services.detectors.InstagramFeedPolicy
import app.scrollguard.services.detectors.InstagramScreen
import app.scrollguard.services.detectors.YouTubeShortsDetector
import app.scrollguard.utils.PackageConstants
import app.scrollguard.utils.UserPreferencesProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import timber.log.Timber
import java.util.ArrayDeque
import java.util.Locale

@SuppressLint("AccessibilityPolicy")
class ShortFormContentBlockerService : AccessibilityService() {
    private val preferences by lazy { UserPreferencesProvider(applicationContext) }
    private val blockStats by lazy { BlockStatsStore(applicationContext) }
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val youtube = YouTubeShortsDetector()
    private val shield by lazy { InstagramLockOverlay(this, ::navigateInstagram, ::leaveInstagram, ::backToMessages, ::watchShared) }
    private var enabledPackages = emptySet<String>()
    private var instagramMode = InstagramProtectionMode.FEED_LOCK
    private var preferencesApplied = false
    private var lastHomeActionAt = -1_000L
    private var lastYouTubeActionAt = -1_000L
    private var lastInstagramWindowId = -1
    private var recordedLockEpisode = false
    private var lastScreen = InstagramScreen.UNKNOWN
    private var sharedReelClickAt = -10_000L
    private var sharedReelWindow = -1
    private var watchingSharedReel = false
    private var wasConversation = false
    private var conversationCheckedAt = -10_000L
    private var conversationWindow = -1
    private var canWatchShared = false
    private var pendingDestination: InstagramDestination? = null
    private var navigationRequestedAt = 0L
    private var pendingHomePackage: String? = null
    private val windowPackages = mutableMapOf<Int, String>()
    private val storyNavigationLabel = Regex("\\bstor(y|ies)\\b")
    private val storyCreationLabel = Regex("\\b(add|create)\\b|^your story\\b")
    private var lastProtectionCheckAt = -10_000L
    private var eventCheckQueued = false
    private val eventCheck: Runnable = Runnable {
        eventCheckQueued = false
        watchdog.run()
    }

    private fun requestProtectionCheck() {
        if (eventCheckQueued) return
        eventCheckQueued = true
        // Yield to window layout and coalesce bursts. This is not a trailing debounce:
        // new events cannot postpone an already queued check indefinitely.
        handler.postDelayed(eventCheck, (80L - (SystemClock.uptimeMillis() - lastProtectionCheckAt)).coerceAtLeast(0L))
    }

    // Events can be missing. Poll even outside Instagram and while the IME/system UI is active.
    private val watchdog: Runnable = object : Runnable {
        override fun run() {
            handler.removeCallbacks(this)
            handler.removeCallbacks(eventCheck)
            eventCheckQueued = false
            runCatching { enforceInstagram() }.onFailure {
                Timber.e(it, "Protection check failed")
                shield.hide()
                ProtectionRuntime.action("Protection check failed; shield removed")
                val current = runCatching { foregroundApplication() }.getOrNull()
                ProtectionRuntime.checked(current?.packageName, current?.windowId ?: -1,
                    "Check failed", "Protection not confirmed; ${if (instagramMode == InstagramProtectionMode.SOCIAL) "selective mode keeps interface usable" else "request exit if Instagram is confirmed"}", shield.status)
                if (instagramMode != InstagramProtectionMode.SOCIAL && current?.packageName == PackageConstants.INSTAGRAM_PACKAGE)
                    exitBlockedApp(PackageConstants.INSTAGRAM_PACKAGE, "Protection check failed")
            }
            lastProtectionCheckAt = SystemClock.uptimeMillis()
            handler.postDelayed(this, 300L)
        }
    }

    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED or AccessibilityEvent.TYPE_WINDOWS_CHANGED or AccessibilityEvent.TYPE_VIEW_CLICKED
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            packageNames = null
            notificationTimeout = 30L
        }
        ProtectionRuntime.connected(true)
        val instagramVersion = runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(PackageConstants.INSTAGRAM_PACKAGE, 0).versionName
        }.getOrNull() ?: "not installed"
        ProtectionRuntime.device("${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE} API ${Build.VERSION.SDK_INT}; Instagram $instagramVersion")
        combine(preferences.getTrackedPackages(), preferences.getInstagramProtectionMode()) { packages, mode -> packages to mode }
            .onEach { (packages, mode) ->
                enabledPackages = packages.toSet()
                instagramMode = mode
                preferencesApplied = true
                watchingSharedReel = false
                ProtectionRuntime.configured(mode, PackageConstants.INSTAGRAM_PACKAGE in enabledPackages)
                watchdog.run()
            }.retryWhen { failure, _ ->
                // A transient read error must not discard an already applied configuration.
                // Keep enforcing that configuration while retrying, without changing stored data.
                ProtectionRuntime.preferencesFailed()
                Timber.e(failure, "Could not read blocker preferences; retrying")
                delay(1000L)
                true
            }.launchIn(scope)
        watchdog.run()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString()
        if (packageName == PackageConstants.INSTAGRAM_PACKAGE && event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
            instagramMode == InstagramProtectionMode.SOCIAL && lastScreen == InstagramScreen.MESSAGES) {
            val source = event.source
            if (source != null && messageContentClick(source)) {
                sharedReelClickAt = SystemClock.uptimeMillis()
                sharedReelWindow = lastInstagramWindowId
            }
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && packageName != null &&
            packageName != applicationContext.packageName && packageName != "com.android.systemui") {
            // An event hint is usable only with a CURRENT application window, never a missing window.
            val matches = runCatching { windows.any { it.id == event.windowId && it.type == AccessibilityWindowInfo.TYPE_APPLICATION } }.getOrDefault(false)
            if (matches) windowPackages[event.windowId] = packageName
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            // Remove shields promptly on confirmed exits without traversing a content tree.
            runCatching { foregroundApplication() }.getOrNull()?.let { current ->
                if (current.packageName != null &&
                    (current.packageName != PackageConstants.INSTAGRAM_PACKAGE || current.systemControls)) clearInstagram()
            }
        }
        requestProtectionCheck()
        if (packageName == PackageConstants.TIKTOK_PACKAGE && packageName in enabledPackages) {
            if (foregroundApplication()?.packageName == packageName) exitBlockedApp(packageName, "TikTok blocked completely")
        } else if (packageName == PackageConstants.YOUTUBE_PACKAGE && packageName in enabledPackages) {
            val foreground = foregroundApplication() ?: return
            val root = foreground.root ?: return
            if (foreground.packageName != packageName) return
            val result = youtube.detect(event, root, resources)
            DetectionDiagnostics.report(result)
            if (result.shouldBlock && SystemClock.uptimeMillis() - lastYouTubeActionAt >= 800L) {
                lastYouTubeActionAt = SystemClock.uptimeMillis()
                val accepted = performGlobalAction(GLOBAL_ACTION_BACK)
                DetectionDiagnostics.reportActionStatus(packageName, BlockAction.BACK,
                    if (accepted) DetectionActionStatus.PERFORMED else DetectionActionStatus.FAILED)
                if (accepted) blockStats.record()
            }
        }
    }

    private data class ForegroundApplication(val packageName: String?, val windowId: Int,
        val root: AccessibilityNodeInfo?, val bounds: MediaBounds, val systemControls: Boolean = false,
        val contentBounds: MediaBounds = bounds, val keyboardBounds: MediaBounds? = null)

    private fun foregroundApplication(): ForegroundApplication? {
        val visibleWindows = windows
        val applications = visibleWindows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val application = applications.firstOrNull { it.isFocused } ?: applications.firstOrNull { it.isActive }
            ?: applications.firstOrNull()
        val system = visibleWindows.firstOrNull {
            it.type == AccessibilityWindowInfo.TYPE_SYSTEM && (it.isActive || it.isFocused) &&
                (application == null || it.layer > application.layer)
        }
        if (system != null) return ForegroundApplication(system.root?.packageName?.toString() ?: "Android system",
            system.id, null, safeDisplayBounds(), true)
        if (application != null) {
            val root = application.root
            val packageName = root?.packageName?.toString() ?: windowPackages[application.id]
            if (root?.packageName != null) windowPackages[application.id] = root.packageName.toString()
            windowPackages.keys.retainAll(applications.map { it.id }.toSet())
            val rect = Rect().also(application::getBoundsInScreen)
            val bounds = MediaBounds(rect.left, rect.top, rect.right, rect.bottom).intersect(safeDisplayBounds()) ?: return null
            // Keep classification coordinates intact and subtract the IME from touch shielding only.
            val keyboardBounds = visibleWindows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }?.let { ime ->
                val keyboard = Rect().also(ime::getBoundsInScreen)
                MediaBounds(keyboard.left, keyboard.top, keyboard.right, keyboard.bottom).intersect(bounds)
            }
            return ForegroundApplication(packageName, application.id, root, bounds, contentBounds = bounds, keyboardBounds = keyboardBounds)
        }
        // The active root can be our shield or the keyboard; neither confirms Instagram foreground.
        val root = rootInActiveWindow ?: return null
        val packageName = root.packageName?.toString() ?: return null
        if (packageName == applicationContext.packageName) return null
        if (packageName == "com.android.systemui") return ForegroundApplication(packageName, root.windowId, null, safeDisplayBounds(), true)
        if (root.window?.type != AccessibilityWindowInfo.TYPE_APPLICATION) return null
        val rect = Rect().also(root::getBoundsInScreen)
        val bounds = MediaBounds(rect.left, rect.top, rect.right, rect.bottom).intersect(safeDisplayBounds()) ?: return null
        val keyboardBounds = visibleWindows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }?.let { ime ->
            val keyboard = Rect().also(ime::getBoundsInScreen)
            MediaBounds(keyboard.left, keyboard.top, keyboard.right, keyboard.bottom).intersect(bounds)
        }
        return ForegroundApplication(packageName, root.windowId, root, bounds, keyboardBounds = keyboardBounds)
    }

    @Suppress("DEPRECATION")
    private fun safeDisplayBounds(): MediaBounds {
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = manager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return metrics.bounds.let { MediaBounds(it.left + insets.left, it.top + insets.top, it.right - insets.right, it.bottom - insets.bottom) }
        }
        val metrics = DisplayMetrics()
        manager.defaultDisplay.getMetrics(metrics)
        val status = resources.getIdentifier("status_bar_height", "dimen", "android")
        return MediaBounds(0, if (status > 0) resources.getDimensionPixelSize(status) else 0, metrics.widthPixels, metrics.heightPixels)
    }

    private fun enforceInstagram() {
        val foreground = foregroundApplication()
        val phoneLocked = (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked ||
            !(getSystemService(POWER_SERVICE) as PowerManager).isInteractive
        pendingHomePackage?.let { blockedPackage ->
            if (foreground?.packageName != null && foreground.packageName != blockedPackage && !foreground.systemControls && !phoneLocked) {
                ProtectionRuntime.action("Confirmed $blockedPackage is no longer foreground after HOME request")
                if (!recordedLockEpisode) blockStats.record()
                pendingHomePackage = null
            }
        }
        if (!preferencesApplied || PackageConstants.INSTAGRAM_PACKAGE !in enabledPackages || phoneLocked ||
            foreground?.packageName != PackageConstants.INSTAGRAM_PACKAGE || foreground.systemControls) {
            clearInstagram()
            val reason = when {
                !preferencesApplied -> "Waiting for preferences"
                phoneLocked -> "Phone locked"
                PackageConstants.INSTAGRAM_PACKAGE !in enabledPackages -> "Instagram switch off"
                foreground == null -> "Foreground unavailable; no shield attached"
                else -> "Outside Instagram"
            }
            ProtectionRuntime.checked(foreground?.packageName, foreground?.windowId ?: -1,
                if (foreground?.root == null) "Missing" else "Available (not traversed)", reason, shield.status)
            if (foreground?.packageName == PackageConstants.TIKTOK_PACKAGE && foreground.packageName in enabledPackages)
                exitBlockedApp(PackageConstants.TIKTOK_PACKAGE, "TikTok blocked completely")
            return
        }
        lastInstagramWindowId = foreground.windowId
        val tree = foreground.root?.let { runCatching { AccessibilityTreeSnapshot.from(it, includeLabels = false) }.getOrNull() }
        // IME occlusion changes touch shielding, not the application's content coordinates.
        // Android can keep a focused chat composer below the keyboard and still accept typing.
        val screen = InstagramFeedPolicy.evaluate(instagramMode, tree, foreground.contentBounds)
        val now = SystemClock.uptimeMillis()
        pendingDestination?.let { destination ->
            val confirmed = (destination == InstagramDestination.MESSAGES && screen == InstagramScreen.MESSAGES) ||
                (destination == InstagramDestination.PROFILE && screen == InstagramScreen.PROFILE) ||
                (destination == InstagramDestination.STORIES && screen == InstagramScreen.STORY)
            if (confirmed || now - navigationRequestedAt > 3000L) {
                ProtectionRuntime.action("Navigate $destination: ${if (confirmed) "destination confirmed in current window" else "destination not confirmed; protection retained"}")
                if (!confirmed) shield.navigationFailed()
                pendingDestination = null
            }
        }
        if (screen == InstagramScreen.REELS && lastScreen == InstagramScreen.MESSAGES && wasConversation &&
            conversationWindow == foreground.windowId && now - conversationCheckedAt in 0..1500L &&
            instagramMode == InstagramProtectionMode.SOCIAL) canWatchShared = true
        if (screen != InstagramScreen.REELS || instagramMode != InstagramProtectionMode.SOCIAL ||
            conversationWindow != foreground.windowId) canWatchShared = false
        wasConversation = screen == InstagramScreen.MESSAGES && tree?.hasConversation(foreground.contentBounds) == true
        if (wasConversation) { conversationCheckedAt = now; conversationWindow = foreground.windowId }
        if (screen != InstagramScreen.REELS) watchingSharedReel = false
        if (screen == InstagramScreen.REELS && instagramMode == InstagramProtectionMode.SOCIAL &&
            sharedReelWindow == foreground.windowId && now - sharedReelClickAt in 0..1500L) {
            watchingSharedReel = true
            sharedReelClickAt = -10_000L
        }
        if (instagramMode != InstagramProtectionMode.SOCIAL || sharedReelWindow != foreground.windowId) watchingSharedReel = false
        val description = if (watchingSharedReel) "Shared Reel: viewing allowed, all Instagram gestures shielded" else screen.description
        lastScreen = screen
        val rootState = when { tree == null -> "Missing"; tree.truncated -> "Incomplete (${tree.nodeCount} nodes)"; else -> "Complete (${tree.nodeCount} nodes)" }
        val result = DetectionResult(PackageConstants.INSTAGRAM_PACKAGE, if (screen.allowed) 0 else 1, 1,
            listOf(description, InstagramFeedPolicy.reason(screen, tree, foreground.contentBounds), "Root $rootState"),
            tree?.diagnosticIdentifiers().orEmpty(), BlockAction.LOCK_FEED)
        DetectionDiagnostics.report(result)
        if (screen.allowed) {
            shield.hide()
            recordedLockEpisode = false
        } else {
            val root = foreground.root
            val attached = shield.show(InstagramLockPanel(foreground.bounds, instagramMode == InstagramProtectionMode.APP_LOCK,
                root != null && findNavigation(root, InstagramDestination.MESSAGES) != null,
                root != null && findNavigation(root, InstagramDestination.PROFILE) != null,
                watchingSharedReel, instagramMode == InstagramProtectionMode.SOCIAL, canWatchShared, foreground.keyboardBounds,
                canOpenStories = root != null && findNavigation(root, InstagramDestination.STORIES) != null))
            DetectionDiagnostics.reportActionStatus(result.packageName, BlockAction.LOCK_FEED,
                if (!attached) DetectionActionStatus.FAILED else if (shield.isAttached) DetectionActionStatus.TOUCH_BLOCKED else DetectionActionStatus.READY)
            if (attached && shield.isAttached && !recordedLockEpisode && !watchingSharedReel) { blockStats.record(); recordedLockEpisode = true }
            if (!attached || instagramMode == InstagramProtectionMode.APP_LOCK)
                exitBlockedApp(result.packageName, if (attached) description else "Shield failed to attach")
        }
        ProtectionRuntime.checked(foreground.packageName, foreground.windowId, rootState, description, shield.status,
            InstagramObservation(System.currentTimeMillis(), foreground.windowId,
                "content=${foreground.contentBounds}; shield regions=${ShieldGeometry.regions(foreground.bounds, foreground.keyboardBounds)}", rootState,
                "$description; ${InstagramFeedPolicy.reason(screen, tree, foreground.contentBounds)}", tree?.structuralReport().orEmpty()))
    }

    private fun clearInstagram() {
        recordedLockEpisode = false
        watchingSharedReel = false
        wasConversation = false
        canWatchShared = false
        conversationWindow = -1
        sharedReelClickAt = -10_000L
        lastScreen = InstagramScreen.UNKNOWN
        pendingDestination = null
        shield.hide()
    }
    private fun exitBlockedApp(packageName: String, reason: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastHomeActionAt < 500L) return
        lastHomeActionAt = now
        val accepted = performGlobalAction(GLOBAL_ACTION_HOME)
        if (accepted) pendingHomePackage = packageName
        ProtectionRuntime.action("HOME requested for $packageName: accepted=$accepted; $reason; destination awaits confirmation")
        val result = DetectionResult(packageName, 1, 1, listOf(reason), action = BlockAction.HOME)
        DetectionDiagnostics.report(result)
        DetectionDiagnostics.reportActionStatus(packageName, BlockAction.HOME,
            if (accepted) DetectionActionStatus.PERFORMED else DetectionActionStatus.FAILED)
    }
    private fun leaveInstagram() {
        exitBlockedApp(PackageConstants.INSTAGRAM_PACKAGE, "Left the locked feed")
        handler.postDelayed(watchdog, 100L)
    }
    private fun backToMessages() {
        if (foregroundApplication()?.packageName != PackageConstants.INSTAGRAM_PACKAGE) return
        val accepted = performGlobalAction(GLOBAL_ACTION_BACK)
        watchingSharedReel = false
        ProtectionRuntime.action("Shared viewer BACK requested: accepted=$accepted; destination awaits confirmation")
        handler.postDelayed(watchdog, 100L)
    }
    private fun watchShared() {
        val foreground = foregroundApplication() ?: return
        if (!canWatchShared || instagramMode != InstagramProtectionMode.SOCIAL ||
            foreground.packageName != PackageConstants.INSTAGRAM_PACKAGE || foreground.windowId != conversationWindow) return
        val tree = foreground.root?.let { runCatching { AccessibilityTreeSnapshot.from(it, includeLabels = false) }.getOrNull() }
        if (InstagramFeedPolicy.evaluate(instagramMode, tree, foreground.contentBounds) != InstagramScreen.REELS) return
        sharedReelWindow = foreground.windowId
        watchingSharedReel = true
        ProtectionRuntime.action("Viewer opened directly from confirmed conversation: watch requested; Instagram gestures remain shielded")
        watchdog.run()
    }
    private fun navigateInstagram(destination: InstagramDestination) {
        val foreground = foregroundApplication() ?: return
        val root = foreground.root ?: return
        if (instagramMode == InstagramProtectionMode.APP_LOCK || foreground.packageName != PackageConstants.INSTAGRAM_PACKAGE ||
            foreground.windowId != lastInstagramWindowId) return
        val target = findNavigation(root, destination)
        val accepted = target?.let { runCatching { it.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false) } ?: false
        ProtectionRuntime.action("Navigate $destination: click accepted=$accepted; destination awaits confirmation")
        if (accepted) { pendingDestination = destination; navigationRequestedAt = SystemClock.uptimeMillis() }
        if (!accepted) shield.navigationFailed()
        handler.postDelayed(watchdog, 100L)
    }
    private fun messageContentClick(source: AccessibilityNodeInfo): Boolean {
        if (source.isEditable || source.windowId != lastInstagramWindowId || !source.isVisibleToUser) return false
        var node: AccessibilityNodeInfo? = source
        var mediaRow = false
        repeat(12) {
            val current = node ?: return false
            val id = current.viewIdResourceName.orEmpty().substringAfterLast('/')
            if (id == "direct_shared_reel") mediaRow = true
            if (id in AccessibilityTreeSnapshot.HISTORY_IDS) return mediaRow
            node = current.parent
        }
        return false
    }
    private fun findNavigation(root: AccessibilityNodeInfo, destination: InstagramDestination): AccessibilityNodeInfo? {
        val ids = when (destination) {
            InstagramDestination.MESSAGES -> setOf("direct_tab", "action_bar_inbox_button", "action_bar_direct_button", "inbox_button", "messenger_button")
            InstagramDestination.PROFILE -> setOf("profile_tab", "profile_tab_icon_view")
            InstagramDestination.STORIES -> emptySet()
        }
        val labels = if (destination == InstagramDestination.MESSAGES) setOf("messages", "direct", "messenger") else setOf("profile", "your profile")
        val viewport = safeDisplayBounds()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited++ < 1200) {
            val node = queue.removeFirst()
            val id = node.viewIdResourceName.orEmpty().substringAfterLast('/')
            val label = node.contentDescription?.toString()?.lowercase(Locale.ROOT).orEmpty()
            // A top-row Story avatar is a navigation hint only; a successful click never grants access.
            val storyAvatar = if (destination == InstagramDestination.STORIES) {
                val area = Rect().also(node::getBoundsInScreen)
                storyNavigationLabel.containsMatchIn(label) &&
                    !storyCreationLabel.containsMatchIn(label) &&
                    area.width() in 16..(viewport.width / 2) && area.height() in 16..(viewport.height / 4) &&
                    area.top >= viewport.top && area.bottom <= viewport.top + viewport.height * 0.35f
            } else false
            if (node.isVisibleToUser && (storyAvatar || (destination != InstagramDestination.STORIES && (id in ids || label in labels)))) {
                var candidate: AccessibilityNodeInfo? = node
                repeat(4) {
                    val current = candidate
                    if (current?.isClickable == true) return current
                    candidate = current?.parent
                }
            }
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
        }
        return null
    }
    override fun onInterrupt() {
        // Feedback interruption is not disconnection. Recheck rather than stop protection.
        shield.hide()
        watchdog.run()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        clearInstagram()
        ProtectionRuntime.connected(false)
        scope.cancel()
        super.onDestroy()
    }
}
