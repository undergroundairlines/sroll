/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard

import android.app.UiAutomation
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.scrollguard.models.InstagramProtectionMode
import app.scrollguard.services.ProtectionRuntime
import app.scrollguard.utils.StrictModePolicy
import app.scrollguard.utils.UserPreferencesProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Exercises the real service, system overlay, touch dispatch and lifecycle on an emulator.
 * The synthetic UI proves enforcement mechanics; it is not a test of Instagram's private UI. */
@RunWith(AndroidJUnit4::class)
class InstagramEnforcementTest {
    @get:Rule val testName = TestName()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var device: UiDevice
    private val preferences by lazy { UserPreferencesProvider(instrumentation.targetContext) }
    private val guardTitle = By.res("app.scrollguard", "guard_title")
    private val sharedBack = By.res("app.scrollguard", "guard_shared_back")

    @Before fun setup() {
        // UI Automator otherwise suppresses the very service these tests need to exercise.
        Configurator.getInstance().setUiAutomationFlags(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        device = UiDevice.getInstance(instrumentation)
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).let { automation ->
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
        }
        device.wakeUp()
        // CI fixture has no credential. Reset an insecure keyguard left by a failed
        // sleep/wake case so one failure cannot invalidate the rest of the suite.
        device.executeShellCommand("wm dismiss-keyguard")
        device.pressHome()
        runBlocking {
            preferences.cancelStrictModeUnlock()
            preferences.requestStrictModeUnlock(StrictModePolicy.STRICT_MODE_TARGET, 1L)
            preferences.completeStrictModeUnlockIfExpired(System.currentTimeMillis())
            preferences.requestInstagramProtectionMode(InstagramProtectionMode.FEED_LOCK, System.currentTimeMillis())
            preferences.setTrackedPackages(listOf("com.instagram.android"))
        }
        device.executeShellCommand("settings put secure enabled_accessibility_services app.scrollguard/app.scrollguard.services.ShortFormContentBlockerService")
        device.executeShellCommand("settings put secure accessibility_enabled 1")
        awaitCondition {
            val state = ProtectionRuntime.state.value
            state.connected && state.preferencesApplied && state.instagramEnabled &&
                state.instagramMode == InstagramProtectionMode.FEED_LOCK
        }
    }

    @After fun leaveFixture() {
        val directory = instrumentation.targetContext.getExternalFilesDir(null)
        device.takeScreenshot(File(directory, "${testName.methodName}.png"))
        device.dumpWindowHierarchy(File(directory, "${testName.methodName}.xml"))
        File(directory, "${testName.methodName}.txt").writeText(ProtectionRuntime.report())
        // Gradle uninstalls the target after instrumentation. Preserve emulator screenshots
        // outside its app directory before that cleanup, using the test runner's shell access.
        device.executeShellCommand("mkdir -p /sdcard/Download/scrollguard-verification")
        // executeShellCommand does not expand a wildcard; copy explicit files.
        for (extension in listOf("png", "xml", "txt")) {
            val fileName = "${testName.methodName}.$extension"
            device.executeShellCommand("cp /sdcard/Android/data/app.scrollguard/files/$fileName /sdcard/Download/scrollguard-verification/$fileName")
        }
        device.pressHome()
        device.setOrientationNatural()
        device.unfreezeRotation()
    }

    private fun open(screen: String) {
        device.executeShellCommand("am start -W -n com.instagram.android/.MainActivity --es screen $screen")
    }

    private fun awaitLock() {
        val appeared = device.wait(Until.hasObject(guardTitle), 15_000L)
        assertTrue("Touchable lock must appear: ${ProtectionRuntime.report()}; package=${device.currentPackageName}", appeared)
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8_000L
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50L)
        assertTrue("Condition timed out: ${ProtectionRuntime.report()}", condition())
    }

    private fun assertUnlocked() {
        val gone = device.wait(Until.gone(guardTitle), 8_000L)
        assertTrue("Allowed destination must be confirmed: ${ProtectionRuntime.report()}", gone)
        // A single absent frame does not prove the watchdog leaves a conversation usable.
        repeat(8) {
            SystemClock.sleep(100L)
            assertFalse("Allowed destination was reblocked: ${ProtectionRuntime.state.value}", device.hasObject(guardTitle))
        }
    }

    private fun repeatFeedTouches() {
        repeat(8) {
            // Start inside the feed; 4/5 landed exactly on the fixture's bottom tabs.
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 5,
                device.displayWidth / 2, device.displayHeight * 2 / 5, 15)
            // Landscape can scroll the shield's own controls into this position.
            // Avoid invoking purpose buttons while measuring underlying feed taps.
            if (device.displayHeight > device.displayWidth) {
                val x = device.displayWidth / 3
                val y = device.displayHeight * 2 / 5
                val overControl = device.findObjects(By.pkg("app.scrollguard").clazz("android.widget.Button"))
                    .any { it.visibleBounds.contains(x, y) }
                if (!overControl) device.click(x, y)
            }
            device.click(1, device.displayHeight * 2 / 5)
            device.click(device.displayWidth - 2, device.displayHeight * 2 / 5)
        }
    }

    private fun typeAndSendMessage() {
        val editor = requireNotNull(device.wait(Until.findObject(
            By.res("com.instagram.android", "row_thread_composer_edittext")), 8_000L))
        editor.click()
        awaitCondition {
            instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                .windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        }
        assertUnlocked()
        editor.text = "Synthetic acceptance message"
        // Back first dismisses the keyboard, leaving the conversation in place.
        device.pressBack()
        assertUnlocked()
        device.findObject(By.res("com.instagram.android", "row_thread_composer_button_send")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Sent: 1")), 8_000L))
    }

    private fun fixtureStatus(): String {
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        return automation.windows.asSequence().mapNotNull { it.root }
            .filter { it.packageName?.toString() == "com.instagram.android" }
            .flatMap { it.findAccessibilityNodeInfosByViewId("com.instagram.android:id/fixture_status").asSequence() }
            .first().text.toString()
    }

    private fun focusedApplicationPackage(): String? =
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            .windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused }
            ?.root?.packageName?.toString()

    @Test fun swipesAndTapsCannotReachHomeEvenWithoutReelIds() {
        open("home")
        awaitLock()
        assertTrue(device.takeScreenshot(File(instrumentation.targetContext.getExternalFilesDir(null), "feed-lock.png")))
        assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
        repeatFeedTouches()
        assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
        awaitLock()
    }

    @Test fun completelyUnknownInterfaceIsAlsoLocked() {
        open("unknown")
        awaitLock()
        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 5,
            device.displayWidth / 2, device.displayHeight * 2 / 5, 20)
        assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
    }

    @Test fun messagesAndProfilesOpenButReturningHomeRelocks() {
        open("home")
        awaitLock()
        device.findObject(By.res("app.scrollguard", "guard_messages")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Fixture messages")), 8_000L))
        assertTrue(device.wait(Until.gone(guardTitle), 8_000L))
        val contact = device.wait(Until.findObject(By.text("Open contact profile")), 8_000L)
        requireNotNull(contact) { "Contact profile button must be present" }.click()
        assertTrue(device.wait(Until.hasObject(By.text("Fixture profile")), 8_000L))
        assertTrue(device.wait(Until.gone(guardTitle), 8_000L))
        device.findObject(By.res("com.instagram.android", "feed_tab")).click()
        awaitLock()
    }

    @Test fun openingConversationFromInboxStaysUsableWithKeyboard() {
        open("home")
        awaitLock()
        device.findObject(By.res("app.scrollguard", "guard_messages")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Fixture messages")), 8_000L))
        assertUnlocked()
        device.findObject(By.text("Open conversation")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Fixture conversation")), 8_000L))
        assertUnlocked()
        typeAndSendMessage()
        device.findObject(By.res("com.instagram.android", "feed_tab")).click()
        awaitLock()
        repeatFeedTouches()
        assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
    }

    @Test fun scrollingMessagesWithInlineReelAndIncompleteHistoryNeverAttachesShield() = runBlocking {
        open("conversation_scrolling")
        assertTrue(device.wait(Until.hasObject(By.text("Fixture conversation")), 8_000L))
        assertUnlocked()
        assertTrue(device.hasObject(By.res("com.instagram.android", "clips_video_container")))
        val attachmentCount = ProtectionRuntime.state.value.overlayAttachmentCount
        val startedAt = System.currentTimeMillis()
        val observedIncomplete = AtomicBoolean(false)
        val observer = launch(Dispatchers.Default) {
            ProtectionRuntime.state.collect { state ->
                if (state.checkedAtMillis >= startedAt && state.foregroundPackage == "com.instagram.android" &&
                    state.rootState.startsWith("Incomplete")) observedIncomplete.set(true)
            }
        }
        try {
            repeat(8) { index ->
                val history = requireNotNull(device.findObject(By.res("com.instagram.android", "message_list"))).visibleBounds
                val upper = history.top + history.height() / 4
                val lower = history.top + history.height() * 3 / 4
                device.swipe(history.centerX(), if (index % 2 == 0) lower else upper,
                    history.centerX(), if (index % 2 == 0) upper else lower, 20)
                assertFalse("Message scrolling flashed the shield: ${ProtectionRuntime.report()}", device.hasObject(guardTitle))
                assertEquals("No attachment attempt is permitted during confirmed message scrolling: ${ProtectionRuntime.report()}",
                    attachmentCount, ProtectionRuntime.state.value.overlayAttachmentCount)
            }
            val scrollStatus = device.findObject(By.res("com.instagram.android", "fixture_message_scroll_status")).text
            assertFalse("Gestures must actually change message history: $scrollStatus", scrollStatus.endsWith("incomplete changes: 0"))
            awaitCondition { observedIncomplete.get() }
            typeAndSendMessage()
            assertEquals("Typing must not attach a shield: ${ProtectionRuntime.report()}",
                attachmentCount, ProtectionRuntime.state.value.overlayAttachmentCount)
        } finally {
            observer.cancel()
        }
        device.findObject(By.res("com.instagram.android", "feed_tab")).click()
        awaitLock()
        repeatFeedTouches()
        assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
        open("reels")
        awaitLock()
        awaitCondition { ProtectionRuntime.state.value.screen == "Reels locked" }
        repeatFeedTouches()
        assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
    }

    @Test fun foregroundStoryWithRetainedHomeNodesStaysUsableThenHomeRelocks() {
        for (mode in listOf(InstagramProtectionMode.FEED_LOCK, InstagramProtectionMode.SOCIAL)) {
            runBlocking { preferences.requestInstagramProtectionMode(mode, System.currentTimeMillis()) }
            open("story_retained_home")
            assertTrue(device.wait(Until.hasObject(By.text("Fixture story")), 8_000L))
            assertUnlocked()
            val structuralNodes = ProtectionRuntime.state.value.lastInstagram?.structuralNodes.orEmpty()
            assertTrue("Regression must retain visible background Home structure: ${ProtectionRuntime.report()}",
                structuralNodes.any { it.contains("feed_recycler_view") && it.contains("visible=true") })
            val attachmentCount = ProtectionRuntime.state.value.overlayAttachmentCount
            repeat(3) { device.findObject(By.text("Next Story")).click() }
            assertTrue(device.wait(Until.hasObject(By.text("Story: 4")), 8_000L))
            assertUnlocked()
            assertEquals("Story progress/title metadata must not trigger a Reel shield: ${ProtectionRuntime.report()}",
                attachmentCount, ProtectionRuntime.state.value.overlayAttachmentCount)
            device.findObject(By.text("Return Home")).click()
            if (mode == InstagramProtectionMode.FEED_LOCK) {
                awaitLock()
                repeatFeedTouches()
                assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
            } else {
                assertUnlocked()
                open("home_reel")
                awaitLock()
            }
        }
    }

    @Test fun windowEventBurstsStillAttachTouchableShield() {
        open("event_storm")
        awaitLock()
        repeatFeedTouches()
        assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
        awaitLock()
    }

    @Test fun acceptedNavigationClickDoesNotUncoverUnchangedScreen() {
        open("navigation_noop")
        awaitLock()
        device.findObject(By.res("app.scrollguard", "guard_messages")).click()
        SystemClock.sleep(800L)
        awaitLock()
        repeatFeedTouches()
        assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
    }

    @Test fun selectedProfileTabAndHiddenProfileNodesNeverUnlockFeed() {
        for (screen in listOf("selected_profile", "stale_profile", "cached_profile")) {
            open(screen)
            awaitLock()
            repeatFeedTouches()
            assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
        }
    }

    @Test fun emptyAndTruncatedInterfacesRemainLocked() {
        for (screen in listOf("empty_tree", "truncated")) {
            open(screen)
            awaitLock()
            repeatFeedTouches()
            awaitLock()
        }
    }

    @Test fun delayedReturnHomeRelocksEvenWhenFixtureSuppressesChildEvents() {
        open("messages")
        assertTrue(device.wait(Until.hasObject(By.text("Fixture messages")), 8_000L))
        assertUnlocked()
        device.findObject(By.text("Silent return Home")).click()
        awaitLock()
        repeatFeedTouches()
        assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
    }

    @Test fun reelsReachedFromConversationAreBlockedInFeedLockMode() {
        open("conversation")
        assertTrue(device.wait(Until.hasObject(By.text("Fixture conversation")), 8_000L))
        assertUnlocked()
        device.findObject(By.text("Open shared Reel")).click()
        awaitLock()
        repeatFeedTouches()
        assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
        assertFalse(device.hasObject(sharedBack))
    }

    @Test fun profileReelsAndExploreStayBlocked() {
        for (mode in listOf(InstagramProtectionMode.FEED_LOCK, InstagramProtectionMode.SOCIAL)) {
            runBlocking { preferences.requestInstagramProtectionMode(mode, System.currentTimeMillis()) }
            open("profile")
            assertTrue(device.wait(Until.hasObject(By.text("Fixture profile")), 8_000L))
            assertUnlocked()
            device.findObject(By.text("Open profile Reel")).click()
            awaitLock()
            repeatFeedTouches()
            assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
            open("explore")
            awaitLock()
            repeatFeedTouches()
            assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
        }
    }

    @Test fun socialModeAllowsOrdinaryPostsButBlocksEmbeddedReelsAndUnknownUi() {
        runBlocking { preferences.requestInstagramProtectionMode(InstagramProtectionMode.SOCIAL, System.currentTimeMillis()) }
        open("home")
        assertTrue(device.wait(Until.hasObject(By.res("com.instagram.android", "fixture_status")), 8_000L))
        assertUnlocked()
        val feed = requireNotNull(device.findObject(By.res("com.instagram.android", "feed_recycler_view"))).visibleBounds
        device.swipe(feed.centerX(), feed.top + feed.height() * 3 / 4,
            feed.centerX(), feed.top + feed.height() / 4, 20)
        awaitCondition { fixtureStatus() != "Scroll: 0; clicks: 0" }
        for (screen in listOf("home_reel", "unknown", "selected_profile")) {
            open(screen)
            awaitLock()
            repeatFeedTouches()
            assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
        }
    }

    @Test fun socialModeSharedReelCannotSwipeIntoRecommendationFeed() {
        runBlocking { preferences.requestInstagramProtectionMode(InstagramProtectionMode.SOCIAL, System.currentTimeMillis()) }
        open("conversation")
        assertTrue(device.wait(Until.hasObject(By.text("Fixture conversation")), 8_000L))
        assertUnlocked()
        device.findObject(By.res("com.instagram.android", "direct_shared_reel")).click()
        assertTrue("Shared viewer must be protected by its own touch shield", device.wait(Until.hasObject(sharedBack), 8_000L))
        repeatFeedTouches()
        assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
        device.findObject(sharedBack).click()
        assertTrue(device.wait(Until.hasObject(By.text("Fixture conversation")), 8_000L))
        assertTrue(device.wait(Until.gone(sharedBack), 8_000L))
        assertUnlocked()
        typeAndSendMessage()
    }

    @Test fun socialModeViewerOpenedWithoutMessageClickIsLocked() {
        runBlocking { preferences.requestInstagramProtectionMode(InstagramProtectionMode.SOCIAL, System.currentTimeMillis()) }
        open("shared_reel")
        awaitLock()
        assertFalse(device.hasObject(sharedBack))
        repeatFeedTouches()
        assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
    }

    @Test fun immediateMessageViewerNeedsExplicitWatchWhenClickSourceIsDestroyed() {
        runBlocking { preferences.requestInstagramProtectionMode(InstagramProtectionMode.SOCIAL, System.currentTimeMillis()) }
        open("conversation_immediate")
        assertTrue(device.wait(Until.hasObject(By.text("Fixture conversation")), 8_000L))
        assertUnlocked()
        device.findObject(By.res("com.instagram.android", "fixture_shared_reel_no_source")).click()
        awaitLock()
        assertFalse("Missing positive click source must not automatically allow viewing", device.hasObject(sharedBack))
        assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
        val watch = requireNotNull(device.wait(Until.findObject(By.res("app.scrollguard", "guard_watch_shared")), 8_000L))
        watch.click()
        assertTrue(device.wait(Until.hasObject(sharedBack), 8_000L))
        repeatFeedTouches()
        assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
        device.findObject(sharedBack).click()
        assertTrue(device.wait(Until.hasObject(By.text("Fixture conversation")), 8_000L))
        assertTrue(device.wait(Until.gone(sharedBack), 8_000L))
        assertUnlocked()
        typeAndSendMessage()
    }

    @Test fun conversationProfileClickNeverGrantsSharedReelAccess() {
        runBlocking { preferences.requestInstagramProtectionMode(InstagramProtectionMode.SOCIAL, System.currentTimeMillis()) }
        open("conversation")
        assertTrue(device.wait(Until.hasObject(By.text("Fixture conversation")), 8_000L))
        assertUnlocked()
        device.findObject(By.text("Open contact profile")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Fixture profile")), 8_000L))
        // Navigate promptly: a blanket grace period for every DM click would admit this Reel.
        device.findObject(By.text("Open profile Reel")).click()
        awaitLock()
        assertFalse(device.hasObject(sharedBack))
        repeatFeedTouches()
        assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
    }

    @Test fun ownProfileCanOpenThroughShield() {
        open("home")
        awaitLock()
        device.findObject(By.res("app.scrollguard", "guard_profile")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Fixture profile")), 8_000L))
        assertTrue(device.wait(Until.gone(guardTitle), 8_000L))
    }

    @Test fun storyIsAllowedAndHomeStaysLocked() {
        open("story")
        assertTrue(device.wait(Until.hasObject(By.text("Fixture story")), 8_000L))
        assertTrue(device.wait(Until.gone(guardTitle), 8_000L))
        device.findObject(By.res("com.instagram.android", "feed_tab")).click()
        awaitLock()
    }

    @Test fun openingStoriesThroughFeedLockWaitsForConfirmedNativeStory() {
        open("home")
        awaitLock()
        val stories = requireNotNull(device.wait(Until.findObject(By.res("app.scrollguard", "guard_stories")), 8_000L))
        assertTrue("Native top-row Story avatar must make the shield route available", stories.isEnabled)
        stories.click()
        assertTrue(device.wait(Until.hasObject(By.text("Fixture story")), 8_000L))
        assertUnlocked()
        awaitCondition { ProtectionRuntime.state.value.screen == "Story allowed" }
        device.findObject(By.text("Next Story")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Story: 2")), 8_000L))
        device.findObject(By.res("com.instagram.android", "feed_tab")).click()
        awaitLock()
        repeatFeedTouches()
        assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
    }

    @Test fun leavingInstagramRemovesShieldFromLauncher() {
        open("home")
        awaitLock()
        device.findObject(By.res("app.scrollguard", "guard_leave")).click()
        assertTrue(device.wait(Until.gone(guardTitle), 8_000L))
        awaitCondition { focusedApplicationPackage()?.let { it != "com.instagram.android" } == true }
    }

    @Test fun shadeOtherAppAndRecentsNeverKeepInstagramShield() {
        open("home")
        awaitLock()
        assertTrue(device.openNotification())
        assertTrue(device.wait(Until.gone(guardTitle), 8_000L))
        device.pressBack()
        awaitLock()
        device.executeShellCommand("am start -W -a android.settings.SETTINGS")
        assertTrue(device.wait(Until.gone(guardTitle), 8_000L))
        device.pressBack()
        awaitLock()
        device.pressRecentApps()
        assertTrue(device.wait(Until.gone(guardTitle), 8_000L))
        device.click(device.displayWidth / 2, device.displayHeight / 2)
        awaitLock()
        assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
    }

    @Test fun coldLaunchRotationAndServiceReconnectRestoreProtection() {
        device.pressHome()
        device.executeShellCommand("am force-stop com.instagram.android")
        open("home")
        awaitLock()
        device.setOrientationLeft()
        awaitLock()
        repeatFeedTouches()
        assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
        device.setOrientationNatural()
        awaitLock()
        device.executeShellCommand("settings put secure enabled_accessibility_services ''")
        awaitCondition { !ProtectionRuntime.state.value.connected }
        assertTrue(device.wait(Until.gone(guardTitle), 8_000L))
        device.executeShellCommand("settings put secure enabled_accessibility_services app.scrollguard/app.scrollguard.services.ShortFormContentBlockerService")
        awaitCondition { ProtectionRuntime.state.value.connected && ProtectionRuntime.state.value.preferencesApplied }
        awaitLock()
        repeatFeedTouches()
        assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
    }

    @Test fun screenOffRemovesShieldAndWakeRestoresProtection() {
        open("home")
        awaitLock()
        device.sleep()
        assertTrue(device.wait(Until.gone(guardTitle), 8_000L))
        device.wakeUp()
        // Emulator has no personal credential; this never disables a real phone's lock.
        device.executeShellCommand("wm dismiss-keyguard")
        awaitLock()
        repeatFeedTouches()
        assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
    }

    @Test fun rapidConversationHomeTransitionsAlwaysFinishLocked() {
        repeat(5) {
            open("conversation")
            assertTrue(device.wait(Until.hasObject(By.text("Fixture conversation")), 8_000L))
            assertTrue(device.wait(Until.gone(guardTitle), 8_000L))
            device.findObject(By.res("com.instagram.android", "feed_tab")).click()
            awaitLock()
        }
        repeatFeedTouches()
        assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
    }

    @Test fun turningInstagramOffRemovesShield() {
        open("home")
        awaitLock()
        runBlocking { preferences.setTrackedPackages(emptyList()) }
        assertTrue(device.wait(Until.gone(guardTitle), 8_000L))
        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 5,
            device.displayWidth / 2, device.displayHeight * 2 / 5, 20)
        awaitCondition { fixtureStatus() != "Scroll: 0; clicks: 0" }
    }

    @Test fun wholeAppModeExitsMessagesProfilesStoriesAndReels() {
        runBlocking {
            preferences.requestInstagramProtectionMode(InstagramProtectionMode.APP_LOCK, System.currentTimeMillis())
        }
        for (screen in listOf("messages", "conversation", "profile", "story", "shared_reel", "explore")) {
            open(screen)
            awaitCondition {
                focusedApplicationPackage()?.let { it != "com.instagram.android" } == true && !device.hasObject(guardTitle)
            }
        }
    }

    @Test fun strictModeCannotBeBypassedByChangingProtectionMode() = runBlocking {
        preferences.requestInstagramProtectionMode(InstagramProtectionMode.APP_LOCK, 1_000L)
        preferences.enableStrictMode()
        preferences.requestInstagramProtectionMode(InstagramProtectionMode.FEED_LOCK, 1_000L)
        assertEquals(InstagramProtectionMode.APP_LOCK, preferences.getInstagramProtectionMode().first())
        val settings = preferences.getStrictModeSettings().first()
        assertEquals(StrictModePolicy.INSTAGRAM_FEED_TARGET, settings.pendingTarget)
        assertFalse(preferences.completeStrictModeUnlockIfExpired(settings.unlockAtMillis - 1L))
        assertEquals(InstagramProtectionMode.APP_LOCK, preferences.getInstagramProtectionMode().first())
        assertTrue(preferences.completeStrictModeUnlockIfExpired(settings.unlockAtMillis))
        assertEquals(InstagramProtectionMode.FEED_LOCK, preferences.getInstagramProtectionMode().first())
        assertEquals(listOf("com.instagram.android"), preferences.getTrackedPackages().first())
    }

    @Test fun socialWeakeningDeadlinePersistsAndKeepsInstagramEnabled() = runBlocking {
        preferences.enableStrictMode()
        val requestedAt = System.currentTimeMillis()
        preferences.requestInstagramProtectionMode(InstagramProtectionMode.SOCIAL, requestedAt)
        val pending = preferences.getStrictModeSettings().first()
        assertEquals(StrictModePolicy.INSTAGRAM_SOCIAL_TARGET, pending.pendingTarget)
        assertEquals(requestedAt + StrictModePolicy.UNLOCK_DELAY_MILLIS, pending.unlockAtMillis)
        val reopenedPreferences = UserPreferencesProvider(instrumentation.targetContext)
        assertEquals(pending, reopenedPreferences.getStrictModeSettings().first())
        reopenedPreferences.requestInstagramProtectionMode(InstagramProtectionMode.SOCIAL, requestedAt + 60_000L)
        assertEquals(pending.unlockAtMillis, reopenedPreferences.getStrictModeSettings().first().unlockAtMillis)
        assertFalse(reopenedPreferences.completeStrictModeUnlockIfExpired(pending.unlockAtMillis - 1L))
        assertEquals(InstagramProtectionMode.FEED_LOCK, reopenedPreferences.getInstagramProtectionMode().first())
        assertEquals(listOf("com.instagram.android"), reopenedPreferences.getTrackedPackages().first())
        assertTrue(reopenedPreferences.completeStrictModeUnlockIfExpired(pending.unlockAtMillis))
        assertEquals(InstagramProtectionMode.SOCIAL, reopenedPreferences.getInstagramProtectionMode().first())
        assertEquals(listOf("com.instagram.android"), reopenedPreferences.getTrackedPackages().first())
    }

    @Test fun publicPreferenceRemovalCannotBypassStrictDeadline() = runBlocking {
        preferences.enableStrictMode()
        preferences.setTrackedPackages(emptyList())
        assertEquals(listOf("com.instagram.android"), preferences.getTrackedPackages().first())
        val pending = preferences.getStrictModeSettings().first()
        assertEquals("com.instagram.android", pending.pendingTarget)
        assertTrue(pending.unlockAtMillis > System.currentTimeMillis())
        val reopenedPreferences = UserPreferencesProvider(instrumentation.targetContext)
        reopenedPreferences.setTrackedPackages(emptyList())
        assertEquals(pending, reopenedPreferences.getStrictModeSettings().first())
        assertFalse(reopenedPreferences.completeStrictModeUnlockIfExpired(pending.unlockAtMillis - 1L))
        assertEquals(listOf("com.instagram.android"), reopenedPreferences.getTrackedPackages().first())
        assertTrue(reopenedPreferences.completeStrictModeUnlockIfExpired(pending.unlockAtMillis))
        assertEquals(emptyList<String>(), reopenedPreferences.getTrackedPackages().first())
        assertTrue(reopenedPreferences.getStrictModeSettings().first().enabled)
    }
}
