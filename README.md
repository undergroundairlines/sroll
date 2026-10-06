# Scroll Guard

Scroll Guard is a private, offline Android distraction blocker designed for Nothing OS and other
modern Android devices. It blocks YouTube Shorts, locks Instagram's Home feed, Reels and Explore,
and blocks TikTok completely. Instagram feed lock replaces the unreliable per-Reel auto-scroller.

This project is based on Atick Faisal's Apache-2.0 licensed
[Shorts Blocker](https://github.com/atick-faisal/Shorts-Blocker). The detector engine, diagnostics,
privacy configuration, visual theme, application identity, and TikTok support have been modified.

## Features

- Multi-signal YouTube Shorts detection
- Touchable full-window Instagram feed lock: swipes and taps cannot reach the feed behind it
- Home is locked even when it contains ordinary posts or exposes no Reel identifiers
- Messages, profiles and Stories allowed only when their visible interface is positively identified
- Lock-screen buttons open native messages and the user's profile without temporarily unlocking Home
- Unknown, missing and incomplete Instagram interfaces stay blocked
- Optional whole-Instagram block, with a package-level Home action and no UI allowlist
- If the touch shield cannot attach, the service leaves Instagram instead of allowing the feed
- Foreground-window watchdog catches silent transitions and removes the shield outside Instagram
- Dashboard reports actual service connection and the last Instagram enforcement check
- Full TikTok blocking with an immediate Home action
- Per-app switches
- Optional Strict Mode with a persistent 30-minute delay before any blocker can be disabled
- Exact event-based screen-time dashboard with Day, Week, Month and All Time views
- Hourly, daily and monthly usage charts that reconcile with the displayed total
- Per-app detail pages, percentages, screen-on time and pickup counts
- Impact report comparing daily phone and per-app use against the seven days before installation
- Estimated time saved and percentage change since Scroll Guard was enabled
- Private daily archive that preserves all-time history after Android prunes old events
- Small and wide home-screen widgets showing today’s total and percentage change
- Wide widget includes the top three apps with readable time and percentage values
- In-app detector diagnostics using only scores and Android resource IDs
- No account, advertising, analytics, crash reporting, or network permission
- No cloud backup of preferences
- Nothing-inspired monochrome theme with a red status accent

## How detection works

The app uses an Android Accessibility Service after the user explicitly enables it. The YouTube
detector scores several interface signals. Instagram uses a strict allowlist instead:
Home, Reels, Explore and unrecognised screens receive a touchable full-window lock. Only confirmed
messages, profile or Story content removes it. Whole-app mode and TikTok use package-level blocking.

The service observes window changes from all apps to remove the shield when the user leaves
Instagram. It traverses content only in enabled apps. Diagnostic samples live in process memory
and contain no captions, messages, usernames, or account content.

## Install a test build

1. Build and install `app-debug.apk`, or install the supplied APK.
2. On recent Android versions, open **App info** for Scroll Guard. If Android blocks the
   accessibility permission for a sideloaded app, open the three-dot menu and choose
   **Allow restricted settings**.
3. Open Scroll Guard, read the disclosure, and enable its accessibility service.
4. Enable YouTube, Instagram, or TikTok from the app.

## Detector test

1. Clear the **Detector check** panel.
2. Open Instagram Home. A "Your feed is locked" screen should appear. Repeated swipes must not
   move the feed. This intentionally blocks ordinary Home posts too.
3. Use **Open messages** or **Open my profile** on the lock. A recognised destination should open.
   If Instagram changes those interfaces, the screen stays locked rather than exposing the feed.
4. Return to Home or open Reels/Explore. The lock should return. Leave Instagram and verify that
   your launcher and other apps remain usable.
5. Enable **Block whole Instagram** to block every Instagram screen, including messages and profiles.
6. In Strict Mode, switching back to feed lock or disabling a blocker waits 30 minutes.
7. Verify normal YouTube videos are usable and Shorts navigate Back; TikTok should navigate Home.

The shield blocks interaction and viewing; it does not directly control Instagram's audio player.
Use whole-app mode if you also need to leave any playback. The Android accessibility permission
must remain enabled; Strict Mode controls the app's switches, not Android's system settings.

## Android enforcement tests

CI runs nine instrumented tests on an Android 15 emulator, in addition to unit tests and lint.
They exercise the actual accessibility service and overlay: touch interception, unknown interfaces,
native messages/profile navigation, Stories, return-to-Home relocking, launcher cleanup, disabling
protection, whole-app blocking and the persisted Strict Mode delay.

`guard-test-fixture` is an emulator-only application using the Instagram package name, with a
deliberately generic feed and known safe-screen resource IDs. It is never part of Scroll Guard's APK.
Do not install that fixture on a personal phone with Instagram. These tests verify Android
enforcement mechanics; they do not establish compatibility with every real Instagram UI version.

On a disposable configured emulator, run:

```bash
./gradlew :guard-test-fixture:installDebug :app:connectedDebugAndroidTest
```

## Build from source

Open the project in a current Android Studio installation with JDK 17 or newer, then build the
debug APK.
From a configured terminal:

```bash
./gradlew clean test lint assembleDebug
```

The APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

Minimum Android version: Android 7.0 (API 24).

## License

Apache License 2.0. See [LICENSE](LICENSE). Original copyright notices are retained in inherited
files, and modification notices are included in new detector files.
