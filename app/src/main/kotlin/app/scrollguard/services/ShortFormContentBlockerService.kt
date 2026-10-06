/* Copyright 2025 Atick Faisal. Modifications copyright 2026 Scroll Guard contributors.
 * Licensed under the Apache License, Version 2.0. */
package app.scrollguard.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Build
import android.util.DisplayMetrics
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
import app.scrollguard.services.detectors.YouTubeShortsDetector
import app.scrollguard.utils.PackageConstants
import app.scrollguard.utils.UserPreferencesProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import java.util.ArrayDeque
import java.util.Locale

/** Package-level enforcement for Instagram; only positively identified safe screens are usable. */
@SuppressLint("AccessibilityPolicy")
class ShortFormContentBlockerService : AccessibilityService() {
    private val preferences by lazy { UserPreferencesProvider(applicationContext) }
    private val blockStats by lazy { BlockStatsStore(applicationContext) }
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val youtube = YouTubeShortsDetector()
    private val shield by lazy { InstagramLockOverlay(this, ::navigateInstagram, ::leaveInstagram) }
    private var enabledPackages = PackageConstants.DEFAULT_ENABLED_PACKAGES.toSet()
    private var instagramMode = InstagramProtectionMode.FEED_LOCK
    private var lastHomeActionAt = -1_000L
    private var lastYouTubeActionAt = -1_000L
    private var instagramEntryHintAt = -10_000L
    private var lastInstagramWindowId = -1
    private var recordedLockEpisode = false
    private var monitoredInstagram = false

    private val checkInstagram = object : Runnable {
        override fun run() {
            handler.removeCallbacks(this)
            enforceInstagram()
            // Poll even on allowed screens: silent Home transitions must not open a bypass.
            if (monitoredInstagram) handler.postDelayed(this, 400L)
        }
    }

    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED or AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            // Other-app window changes remove the shield on Home/Recents. Their content is
            // never traversed. Filtering only selected apps misses those transitions.
            packageNames = null
            notificationTimeout = 50L
        }
        ProtectionRuntime.connected(true)
        combine(preferences.getTrackedPackages(), preferences.getInstagramProtectionMode()) { packages, mode ->
            packages to mode
        }.onEach { (packages, mode) ->
            enabledPackages = packages.toSet()
            instagramMode = mode
            ProtectionRuntime.configured(mode, PackageConstants.INSTAGRAM_PACKAGE in enabledPackages)
            checkInstagram.run()
        }.catch { Timber.e(it, "Could not read blocker preferences") }.launchIn(scope)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        ProtectionRuntime.connected(true)
        val packageName = event.packageName?.toString()
        val windowChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        if (packageName == PackageConstants.INSTAGRAM_PACKAGE &&
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            instagramEntryHintAt = SystemClock.uptimeMillis()
            lastInstagramWindowId = event.windowId
        }
        if (windowChange || packageName == PackageConstants.INSTAGRAM_PACKAGE) {
            checkInstagram.run()
        }
        if (packageName !in enabledPackages) return
        if (packageName == PackageConstants.TIKTOK_PACKAGE) {
            val foreground = foregroundApplication()
            if (foreground?.root?.packageName?.toString() == packageName ||
                (foreground == null && windowChange)) {
                exitBlockedApp(packageName, "TikTok blocked completely")
            }
        } else if (packageName == PackageConstants.YOUTUBE_PACKAGE) {
            val root = foregroundApplication()?.root ?: return
            if (root.packageName?.toString() != packageName) return
            val result = youtube.detect(event, root, resources)
            DetectionDiagnostics.report(result)
            if (result.shouldBlock && SystemClock.uptimeMillis() - lastYouTubeActionAt >= 800L) {
                lastYouTubeActionAt = SystemClock.uptimeMillis()
                val success = performGlobalAction(GLOBAL_ACTION_BACK)
                DetectionDiagnostics.reportActionStatus(packageName, BlockAction.BACK,
                    if (success) DetectionActionStatus.PERFORMED else DetectionActionStatus.FAILED)
                if (success) blockStats.record()
            }
        }
    }

    private data class ForegroundApplication(val root: AccessibilityNodeInfo, val bounds: MediaBounds)

    private fun foregroundApplication(): ForegroundApplication? {
        val activeRoot = rootInActiveWindow
        val activePackage = activeRoot?.packageName?.toString()
        // Notification shade, system dialogs and other apps must stay usable.
        if (activeRoot != null && activePackage != null && activePackage != applicationContext.packageName &&
            activePackage != PackageConstants.INSTAGRAM_PACKAGE) {
            return ForegroundApplication(activeRoot, boundsOf(activeRoot))
        }
        val applicationWindow = windows.asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && (it.isFocused || it.isActive) }
            .sortedByDescending { it.isFocused }
            .mapNotNull { window -> window.root?.let { window to it } }
            .firstOrNull()
        if (applicationWindow != null) {
            val rect = Rect()
            applicationWindow.first.getBoundsInScreen(rect)
            val bounds = MediaBounds(rect.left, rect.top, rect.right, rect.bottom)
                .takeIf { it.width > 0 && it.height > 0 } ?: boundsOf(applicationWindow.second)
            return ForegroundApplication(applicationWindow.second, bounds)
        }
        return activeRoot?.takeIf { it.packageName?.toString() != applicationContext.packageName }
            ?.let { ForegroundApplication(it, boundsOf(it)) }
    }

    private fun boundsOf(root: AccessibilityNodeInfo): MediaBounds {
        val rect = Rect()
        root.getBoundsInScreen(rect)
        return MediaBounds(rect.left, rect.top, rect.right, rect.bottom)
            .takeIf { it.width > 0 && it.height > 0 }
            ?: displayBounds()
    }

    @Suppress("DEPRECATION")
    private fun displayBounds(): MediaBounds {
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return manager.currentWindowMetrics.bounds.let { MediaBounds(it.left, it.top, it.right, it.bottom) }
        }
        val metrics = DisplayMetrics()
        manager.defaultDisplay.getRealMetrics(metrics)
        return MediaBounds(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    private fun enforceInstagram() {
        if (PackageConstants.INSTAGRAM_PACKAGE !in enabledPackages ||
            (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked) {
            clearInstagram()
            return
        }
        val foreground = foregroundApplication()
        if (foreground != null && foreground.root.packageName?.toString() != PackageConstants.INSTAGRAM_PACKAGE) {
            instagramEntryHintAt = -10_000L
            clearInstagram()
            return
        }
        if (foreground == null) {
            // A cold launch can briefly have no tree. Lock it immediately while the interface
            // loads, so startup latency cannot expose Home or kick the user out of messages.
            if (monitoredInstagram || SystemClock.uptimeMillis() - instagramEntryHintAt < 5_000L || shield.isShowing) {
                monitoredInstagram = true
                ProtectionRuntime.instagramCheck("Loading interface — feed locked")
                val attached = shield.show(InstagramLockPanel(displayBounds(),
                    instagramMode == InstagramProtectionMode.APP_LOCK, false, false))
                if (!attached || instagramMode == InstagramProtectionMode.APP_LOCK ||
                    SystemClock.uptimeMillis() - instagramEntryHintAt >= 5_000L) {
                    exitBlockedApp(PackageConstants.INSTAGRAM_PACKAGE, "Interface unavailable; Instagram blocked")
                }
            } else clearInstagram()
            return
        }
        monitoredInstagram = true
        lastInstagramWindowId = foreground.root.windowId
        val tree = runCatching { AccessibilityTreeSnapshot.from(foreground.root) }.getOrNull()
        val screen = InstagramFeedPolicy.evaluate(instagramMode, tree, foreground.bounds)
        ProtectionRuntime.instagramCheck(screen.description)
        val result = DetectionResult(PackageConstants.INSTAGRAM_PACKAGE,
            if (screen.allowed) 0 else 1, 1, listOf(screen.description),
            tree?.diagnosticIdentifiers().orEmpty(), BlockAction.LOCK_FEED)
        DetectionDiagnostics.report(result)
        if (screen.allowed) {
            shield.hide()
            recordedLockEpisode = false
            return
        }
        val shown = shield.show(InstagramLockPanel(foreground.bounds,
            instagramMode == InstagramProtectionMode.APP_LOCK,
            findNavigation(foreground.root, InstagramDestination.MESSAGES) != null,
            findNavigation(foreground.root, InstagramDestination.PROFILE) != null))
        if (shown) {
            DetectionDiagnostics.reportActionStatus(result.packageName, BlockAction.LOCK_FEED,
                DetectionActionStatus.TOUCH_BLOCKED)
            if (!recordedLockEpisode) {
                blockStats.record()
                recordedLockEpisode = true
            }
        } else {
            ProtectionRuntime.instagramCheck("Shield unavailable — closing Instagram")
            DetectionDiagnostics.reportActionStatus(result.packageName, BlockAction.LOCK_FEED,
                DetectionActionStatus.FAILED)
        }
        if (!shown || instagramMode == InstagramProtectionMode.APP_LOCK) {
            exitBlockedApp(result.packageName, screen.description)
        }
    }

    private fun clearInstagram() {
        monitoredInstagram = false
        recordedLockEpisode = false
        shield.hide()
        handler.removeCallbacks(checkInstagram)
    }

    private fun exitBlockedApp(packageName: String, reason: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastHomeActionAt < 500L) return
        lastHomeActionAt = now
        Timber.d("Leaving blocked app %s: %s", packageName, reason)
        val result = DetectionResult(packageName, 1, 1, listOf(reason), action = BlockAction.HOME)
        DetectionDiagnostics.report(result)
        val success = performGlobalAction(GLOBAL_ACTION_HOME)
        DetectionDiagnostics.reportActionStatus(packageName, BlockAction.HOME,
            if (success) DetectionActionStatus.PERFORMED else DetectionActionStatus.FAILED)
        if (success && !recordedLockEpisode) {
            blockStats.record()
            if (packageName == PackageConstants.INSTAGRAM_PACKAGE) recordedLockEpisode = true
        }
    }

    private fun leaveInstagram() {
        exitBlockedApp(PackageConstants.INSTAGRAM_PACKAGE, "Left the locked feed")
        handler.postDelayed(checkInstagram, 100L)
    }

    private fun navigateInstagram(destination: InstagramDestination) {
        val root = foregroundApplication()?.root
        if (instagramMode != InstagramProtectionMode.FEED_LOCK || root == null ||
            root.packageName?.toString() != PackageConstants.INSTAGRAM_PACKAGE ||
            root.windowId != lastInstagramWindowId) return
        val target = findNavigation(root, destination)
        val clicked = target?.let {
            runCatching { it.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
        } ?: false
        Timber.d("Instagram navigation %s accepted: %s", destination, clicked)
        if (!clicked) shield.navigationFailed()
        // Never uncover the feed just because a navigation command was accepted.
        handler.postDelayed(checkInstagram, 100L)
    }

    private fun findNavigation(root: AccessibilityNodeInfo, destination: InstagramDestination): AccessibilityNodeInfo? {
        val ids = when (destination) {
            InstagramDestination.MESSAGES -> setOf("direct_tab", "action_bar_inbox_button",
                "action_bar_direct_button", "inbox_button", "messenger_button")
            InstagramDestination.PROFILE -> setOf("profile_tab", "profile_tab_icon_view")
        }
        val labels = if (destination == InstagramDestination.MESSAGES) {
            setOf("messages", "direct", "messenger")
        } else setOf("profile", "your profile")
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited++ < 1200) {
            val node = queue.removeFirst()
            val id = node.viewIdResourceName.orEmpty().substringAfterLast('/')
            val label = node.contentDescription?.toString()?.lowercase(Locale.ROOT).orEmpty()
            if (node.isVisibleToUser && (id in ids || label in labels)) {
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
        ProtectionRuntime.connected(false)
        clearInstagram()
    }

    override fun onDestroy() {
        ProtectionRuntime.connected(false)
        clearInstagram()
        scope.cancel()
        super.onDestroy()
    }
}
