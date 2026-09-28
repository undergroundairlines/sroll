/*
 * Copyright 2025 Atick Faisal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.scrollguard.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.graphics.Path
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.scrollguard.models.BlockAction
import app.scrollguard.models.DetectionActionStatus
import app.scrollguard.services.detectors.InstagramReelsDetector
import app.scrollguard.services.detectors.ShortFormContentDetector
import app.scrollguard.services.detectors.TikTokDetector
import app.scrollguard.services.detectors.YouTubeShortsDetector
import app.scrollguard.utils.UserPreferencesProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.ArrayDeque
import java.util.Locale

/**
 * Accessibility service that detects and blocks short-form content across supported apps.
 *
 * This service monitors only the applications selected by the user. It performs Back for detected
 * Shorts/Reels and Home when the user opens TikTok.
 */
@SuppressLint("AccessibilityPolicy")
class ShortFormContentBlockerService : AccessibilityService() {

    private val lastActionTimestamps = ConcurrentHashMap<String, Long>()
    private val actionCooldownMillis = 1500L
    private val userPreferencesProvider by lazy { UserPreferencesProvider(applicationContext) }

    @Volatile
    private var enabledPackages: Set<String> = emptySet()

    private val job = SupervisorJob()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + job)

    private val detectors: Map<String, ShortFormContentDetector> by lazy {
        mapOf(
            "com.google.android.youtube" to YouTubeShortsDetector(),
            "com.instagram.android" to InstagramReelsDetector(),
            "com.zhiliaoapp.musically" to TikTokDetector(),
        )
    }

    override fun onServiceConnected() {
        Timber.d("ShortFormContentBlockerService connected")

        userPreferencesProvider.getTrackedPackages()
            .onEach { packages ->
                Timber.d("Tracked packages updated: ${packages.joinToString()}")
                enabledPackages = packages.toSet()
                val info = serviceInfo.apply {
                    eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_SCROLLED
                    feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                    flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    // An empty array has differed across Android versions. Listening only to our
                    // own package guarantees that disabling every toggle means monitoring nothing.
                    packageNames = if (packages.isEmpty()) {
                        arrayOf(applicationContext.packageName)
                    } else {
                        packages.toTypedArray()
                    }
                    notificationTimeout = 100
                }
                serviceInfo = info
            }
            .catch { error ->
                Timber.e(error, "Error fetching tracked packages")
            }
            .launchIn(coroutineScope)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName !in enabledPackages) return

        if (
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            Timber.v(
                "Accessibility event: type=${event.eventType}, " +
                    "className=${event.className}, package=$packageName",
            )

            // Get the appropriate detector for this package
            val detector = detectors[packageName]
            if (detector == null) {
                Timber.v("No detector found for package: $packageName")
                return
            }

            val windowRoot = rootInActiveWindow
            val result = detector.detect(event, windowRoot, resources)
            DetectionDiagnostics.report(result)
            if (result.shouldBlock) {
                Timber.i("[$packageName] Short-form content detected!")
                val key = "${packageName}_content_detected"
                if (shouldPerformAction(key)) {
                    handleShortFormContentDetected(packageName, result.action, windowRoot, event)
                } else {
                    Timber.d("[$packageName] Action skipped due to cooldown")
                    DetectionDiagnostics.reportActionStatus(
                        packageName,
                        result.action,
                        DetectionActionStatus.COOLDOWN,
                    )
                }
            }
        }
    }

    override fun onInterrupt() {
        Timber.w("ShortFormContentBlockerService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("ShortFormContentBlockerService destroyed")
        job.cancel()
    }

    /** Performs the detector's requested navigation or Home-feed scroll action. */
    private fun handleShortFormContentDetected(
        packageName: String,
        action: BlockAction,
        windowRoot: AccessibilityNodeInfo?,
        event: AccessibilityEvent,
    ) {
        if (action == BlockAction.SKIP_REEL) {
            Timber.i("[$packageName] Scrolling past in-feed Reel")
            skipFeedReel(packageName, windowRoot, event)
            return
        }
        val globalAction = when (action) {
            BlockAction.BACK -> GLOBAL_ACTION_BACK
            BlockAction.HOME -> GLOBAL_ACTION_HOME
            BlockAction.SKIP_REEL -> return
        }
        Timber.i("[$packageName] Handling detection - performing $action action")
        val success = performGlobalAction(globalAction)
        DetectionDiagnostics.reportActionStatus(
            packageName,
            action,
            if (success) DetectionActionStatus.PERFORMED else DetectionActionStatus.FAILED,
        )
        if (success) {
            Timber.d("[$packageName] $action action performed successfully")
        } else {
            Timber.w("[$packageName] $action action failed")
        }
    }

    /** Scroll the actual Home feed when Instagram exposes it, then fall back to a swipe. */
    private fun skipFeedReel(
        packageName: String,
        windowRoot: AccessibilityNodeInfo?,
        event: AccessibilityEvent,
    ) {
        val source = runCatching { event.source }.getOrNull()
        if (scrollHomeFeed(windowRoot, source)) {
            DetectionDiagnostics.reportActionStatus(
                packageName,
                BlockAction.SKIP_REEL,
                DetectionActionStatus.FEED_SCROLL_SENT,
            )
            return
        }

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val x = metrics.widthPixels / 2f
        val path = Path().apply {
            moveTo(x, metrics.heightPixels * 0.78f)
            lineTo(x, metrics.heightPixels * 0.30f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 220L))
            .build()
        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    DetectionDiagnostics.reportActionStatus(
                        packageName,
                        BlockAction.SKIP_REEL,
                        DetectionActionStatus.PERFORMED,
                    )
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    DetectionDiagnostics.reportActionStatus(
                        packageName,
                        BlockAction.SKIP_REEL,
                        DetectionActionStatus.FAILED,
                    )
                }
            },
            null,
        )
        if (!dispatched) {
            DetectionDiagnostics.reportActionStatus(
                packageName,
                BlockAction.SKIP_REEL,
                DetectionActionStatus.FAILED,
            )
        }
    }

    private fun scrollHomeFeed(vararg roots: AccessibilityNodeInfo?): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        roots.filterNotNull()
            .filter { it.packageName?.toString() == "com.instagram.android" }
            .forEach(queue::addLast)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < 350) {
            val node = queue.removeFirst()
            if (node.isVisibleToUser &&
                node.viewIdResourceName.orEmpty().contains("row_feed_profile_header")
            ) {
                var parent = node.parent
                while (parent != null) {
                    val id = parent.viewIdResourceName.orEmpty().lowercase(Locale.ROOT)
                    val className = parent.className?.toString().orEmpty().lowercase(Locale.ROOT)
                    val feedList = id.contains("feed") || id.endsWith("/list") ||
                        className.contains("recyclerview") || className.contains("listview")
                    val mediaPager = id.contains("clip") || id.contains("reel") ||
                        id.contains("story") || id.contains("pager")
                    if (parent.isScrollable && feedList && !mediaPager) {
                        val bounds = android.graphics.Rect()
                        parent.getBoundsInScreen(bounds)
                        if (bounds.height() > bounds.width() &&
                            runCatching {
                                parent.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                            }.getOrDefault(false)
                        ) {
                            return true
                        }
                    }
                    parent = parent.parent
                }
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return false
    }

    /**
     * Checks if an action should be performed based on cooldown period.
     *
     * Prevents repeated actions from being performed too frequently by enforcing
     * a cooldown period of 1.5 seconds between actions.
     *
     * @param key Unique identifier for the action
     * @return true if the action should be performed, false if still in cooldown
     */
    private fun shouldPerformAction(key: String): Boolean {
        val now = SystemClock.uptimeMillis()
        val last = lastActionTimestamps[key] ?: 0L
        val timeSinceLastAction = now - last
        if (timeSinceLastAction < actionCooldownMillis) {
            Timber.v("Action '$key' cooldown active: ${timeSinceLastAction}ms since last action")
            return false
        }
        lastActionTimestamps[key] = now
        Timber.d("Action '$key' allowed (cooldown: ${actionCooldownMillis}ms)")
        return true
    }
}
