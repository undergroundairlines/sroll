/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard

import android.app.UiAutomation
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
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

/** Exercises the real service, system overlay, touch dispatch and lifecycle on an emulator.
 * The synthetic UI proves enforcement mechanics; it is not a test of Instagram's private UI. */
@RunWith(AndroidJUnit4::class)
class InstagramEnforcementTest {
    @get:Rule val testName = TestName()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var device: UiDevice
    private val preferences by lazy { UserPreferencesProvider(instrumentation.targetContext) }
    private val guardTitle = By.res("app.scrollguard", "guard_title")

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
        // Gradle uninstalls the target after instrumentation. Preserve emulator screenshots
        // outside its app directory before that cleanup, using the test runner's shell access.
        device.executeShellCommand("mkdir -p /sdcard/Download/scrollguard-verification")
        device.executeShellCommand("cp /sdcard/Android/data/app.scrollguard/files/* /sdcard/Download/scrollguard-verification/")
        device.pressHome()
        device.setOrientationNatural()
        device.unfreezeRotation()
    }

    private fun open(screen: String) {
        device.executeShellCommand("am start -W -n com.instagram.android/.MainActivity --es screen $screen")
    }

    private fun awaitLock() {
        assertTrue("Touchable lock must appear: ${ProtectionRuntime.state.value}; package=${device.currentPackageName}",
            device.wait(Until.hasObject(guardTitle), 15_000L))
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8_000L
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50L)
        assertTrue(condition())
    }

    private fun fixtureStatus(): String {
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        return automation.windows.asSequence().mapNotNull { it.root }
            .filter { it.packageName?.toString() == "com.instagram.android" }
            .flatMap { it.findAccessibilityNodeInfosByViewId("com.instagram.android:id/fixture_status").asSequence() }
            .first().text.toString()
    }

    @Test fun swipesAndTapsCannotReachHomeEvenWithoutReelIds() {
        open("home")
        awaitLock()
        assertTrue(device.takeScreenshot(File(instrumentation.targetContext.getExternalFilesDir(null), "feed-lock.png")))
        assertEquals("Scroll: 0; clicks: 0", fixtureStatus())
        repeat(6) {
            device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5,
                device.displayWidth / 2, device.displayHeight / 5, 20)
        }
        device.click(device.displayWidth / 2, device.displayHeight / 5)
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

    @Test fun leavingInstagramRemovesShieldFromLauncher() {
        open("home")
        awaitLock()
        device.findObject(By.res("app.scrollguard", "guard_leave")).click()
        assertTrue(device.wait(Until.gone(guardTitle), 8_000L))
        assertFalse(device.currentPackageName == "com.instagram.android")
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

    @Test fun wholeAppModeExitsEvenMessages() {
        runBlocking {
            preferences.requestInstagramProtectionMode(InstagramProtectionMode.APP_LOCK, System.currentTimeMillis())
        }
        open("messages")
        awaitCondition { device.currentPackageName != "com.instagram.android" && !device.hasObject(guardTitle) }
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
}
