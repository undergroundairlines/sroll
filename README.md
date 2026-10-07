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
- Conversations also accept connected message-history and editable-composer structure, including with the keyboard open
- Optional **Allow posts and shared Reels** mode: recognised ordinary Home posts stay usable; known embedded Reels and Explore stay locked
- Shared viewers reached directly from a confirmed conversation can play behind a transparent touch shield; swiping onward and Instagram taps remain blocked
- Lock-screen buttons open native messages, a Story or the user's profile without temporarily unlocking Home
- Unknown/missing screen identities stay blocked; a missing message row does not invalidate an otherwise confirmed chat
- Optional whole-Instagram block, with a package-level Home action and no UI allowlist
- If the touch shield cannot attach, the service leaves Instagram instead of allowing the feed
- Foreground-window watchdog catches silent transitions and removes the shield outside Instagram
- Dashboard reports actual service connection and the last Instagram enforcement check
- Full TikTok blocking with an immediate Home action
- Per-app switches
- Optional Strict Mode with a persistent 30-minute delay before any blocker can be disabled or Instagram protection weakened
- Exact event-based screen-time dashboard with Day, Week, Month and All Time views
- Hourly, daily and monthly usage charts that reconcile with the displayed total
- Per-app detail pages, percentages, screen-on time and pickup counts
- Impact report comparing daily phone and per-app use against the seven days before installation
- Estimated time saved and percentage change since Scroll Guard was enabled
- Private daily archive that preserves all-time history after Android prunes old events
- Small and wide home-screen widgets showing today’s total and percentage change
- Wide widget includes the top three apps with readable time and percentage values
- Current service/preferences, foreground window, complete/missing/incomplete tree, classification, overlay and action diagnostics, with a local copy report
- No account, advertising, analytics, crash reporting, or network permission
- No cloud backup of preferences
- Nothing-inspired monochrome theme with a red status accent

## How detection works

The app uses an Android Accessibility Service after the user explicitly enables it. The YouTube
detector scores several interface signals. Instagram uses a strict allowlist instead:
Home, Reels, Explore and unrecognised screens receive a touchable full-window lock. Only confirmed
messages, profile or Story content removes it. Whole-app mode and TikTok use package-level blocking.
The default still locks the **entire Home feed, including ordinary posts**. Stored modes and switches
are preserved during the update; a weaker mode is never silently selected.

The optional **Allow posts and shared Reels** mode responds to the preference for ordinary posts and
messaging. It allows a recognised Home container unless visible Reel media is identified. It cannot
distinguish friends' posts from recommendations, or reliably catch a Reel whose private identifiers
Instagram omits. It is explicitly labelled **best effort**; use feed lock or whole-app lock for the
stronger fallback. Unknown screen identities remain locked in every mode.

In 0.4.2, message scrolling does not require every message descendant to remain available.
The current history and editable composer must still identify the same chat, but missing/unread
children confined to that history (or the specific inbox list) do not attach the shield.
Gaps at the page/root or disappearance of the current chat anchors still restore protection.
Reel and shared-post previews inside verified message history are message content; explicit Reel
browsing viewers remain protected even if nested under history. There is no timer that blindly
leaves a previous safe screen unlocked.

Stories take priority over retained background Home nodes when a current large Story viewer and
its controls or sibling drawing order identify the foreground surface. Story media, including a
reshared Reel, does not trigger feed blocking. Cached Story content known to be behind Home cannot
unlock Home. **Open Stories** on the lock clicks an accessible native Story avatar; it stays
protected until a Story destination is confirmed. The button is available only when Instagram
exposes a small top-row Story avatar with an English Story navigation label; it does not infer a
destination from usernames or unlock Home when navigation is unavailable. Whole-Instagram mode
intentionally has no exceptions. These resource interpretations
remain private-interface compatibility assumptions, so a phone report is still needed for an
unrecognised Instagram variant.

In this mode a positive shared-media click inside a confirmed conversation can enter the transparent
viewer shield. If Instagram destroys the clicked accessibility source before its event arrives,
**Watch without scrolling** is offered only for a viewer observed directly after a confirmed
conversation in the same window. Choosing it is an explicit viewing request; the shield still consumes
all underlying touches. Profiles, inbox navigation and later unrelated routes cannot grant that
request. **Back to messages** requests Android Back and keeps shielding until the destination is
confirmed. Instagram may autoplay media and audio; this app cannot promise control of that private
player. Feed lock continues to block shared Reels as well.

The service observes window changes from all apps to remove the shield when the user leaves
Instagram. It traverses content only in enabled apps. Diagnostic samples live in process memory
and contain no captions, messages, usernames, or account content.

## Install a test build

1. Download the supplied `scroll-guard-0.4.2.apk` on the phone and install **over** the existing app.
   Do not uninstall or clear data. The package is `app.scrollguard`, versionCode **402**.
2. On recent Android versions, open **App info** for Scroll Guard. If Android blocks the
   accessibility permission for a sideloaded app, open the three-dot menu and choose
   **Allow restricted settings**.
3. Open Scroll Guard, read the disclosure, and enable its accessibility service.
4. Enable YouTube, Instagram, or TikTok from the app.
5. Confirm **Phone protection check** says service connected and preferences applied. If Nothing OS
   stops the service, reopen Android Accessibility settings and re-enable Scroll Guard. Check the
   phone's app battery/background settings if service disconnections recur; this is a device-specific
   diagnostic step, not a claim that battery settings caused the reported conversation failure.

## Detector test

1. Start with Instagram on and **Allow posts and shared Reels** off. Check **Phone protection check**.
2. Open Instagram Home. A "Your feed is locked" screen should appear. Repeated swipes must not
   move the feed. This intentionally blocks ordinary Home posts too.
3. Use **Open messages** or **Open my profile** on the lock. A recognised destination should open.
   If Instagram changes those interfaces, the screen stays locked rather than exposing the feed.
4. Return to Home or open Reels/Explore. The lock should return. Leave Instagram and verify that
   your launcher and other apps remain usable.
5. Enable **Block whole Instagram** to block every Instagram screen, including messages and profiles.
6. In Strict Mode, switching back to feed lock or disabling a blocker waits 30 minutes.
7. Verify normal YouTube videos are usable and Shorts navigate Back; TikTok should navigate Home.
8. Open messages, then an individual conversation. Open the keyboard, type and send a message.
   Back out to Home and verify relocking. Repeat after reopening from Recents and screen sleep.
9. Enable **Allow posts and shared Reels** (after the Strict Mode wait if enabled). Check ordinary
   Home posts and Stories, then open a friend's shared Reel from a conversation. If the opaque lock
   appears, use **Watch without scrolling** when offered. Try repeated swipes and taps: they must
   not advance or interact with the viewer. Use **Back to messages** and send another message.
10. Open the notification shade, keyboard, launcher and another app. No shield may cover them.
    If any step fails, return to Scroll Guard and **Copy local diagnostic report**. No video or
    computer is required. Paste the report when asking for a fix; it contains structural metadata,
    not message text, captions, usernames or passwords. No report is sent automatically.

The opaque feed lock blocks interaction and viewing; the optional shared-Reel shield allows viewing.
Neither shield directly controls Instagram's audio player.
Use whole-app mode if you also need to leave any playback. The Android accessibility permission
must remain enabled; Strict Mode controls the app's switches, not Android's system settings.

## Android enforcement tests

CI runs an expanded instrumented suite on an Android 15 emulator, in addition to unit tests and lint.
They exercise the actual accessibility service and overlay: touch interception, unknown interfaces,
native messages/profile navigation, Stories, return-to-Home relocking, launcher cleanup, disabling
protection, whole-app blocking and the persisted Strict Mode delay.
The previous suite did not open a conversation after the inbox. New regressions exercise a separate
conversation with an editable composer, message history, a real IME, send action and repeated checks
through the watchdog. They also cover conflicting/cached nodes, empty/truncated trees, rapid return,
Recents, shade, rotation, sleep, service reconnection and the weaker-mode persistent delay. No
privacy-safe structural sample from the user's actual failing Instagram screen was available; these
samples are synthetic and must not be presented as real-device compatibility proof.

`guard-test-fixture` is an emulator-only application using the Instagram package name, with a
deliberately generic feed and known safe-screen resource IDs. It is never part of Scroll Guard's APK.
Do not install that fixture on a personal phone with Instagram. These tests verify Android
enforcement mechanics; they do not establish compatibility with every real Instagram UI version.
The shared-media observable-event scenario deliberately delays its synthetic transition; the
separate explicit-watch fallback addresses missing click-source events without uncovering a feed.

Android documents that the active window can change with touch or input focus, and that interactive
windows are available independently of the active root. The service selects current application
windows, treats IME/system windows separately and clips the overlay to application/system-bar bounds.
See the official [AccessibilityService reference](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
and [AccessibilityWindowInfo reference](https://developer.android.com/reference/android/view/accessibility/AccessibilityWindowInfo).
Classification uses application content coordinates independently of keyboard cutouts: Android can
leave its focused chat composer behind the IME while continuing to accept typing. Shield rectangles
cover every app region outside a docked or floating keyboard, with system bars excluded. Cold-start
shield content is built after the minimal touch-catching window has completed its first layout.
Event bursts are coalesced into one queued check so content traversal cannot repeatedly occupy
the same main Looper ahead of shield layout. The independent watchdog still checks every 300 ms;
confirmed window exits remove the shield without a content traversal.
The window/focus APIs are Android contracts; the separate content and keyboard coordinates were
observed in the synthetic Android 15 regression. All Instagram resource-name interpretations are compatibility
assumptions, checked conservatively and exposed in diagnostics.

## History and update compatibility

Existing DataStore keys, archives, widget identities and Strict Mode deadlines remain intact.
All Time starts at the earliest available local archived day, explains history gaps and does not
claim Android's deleted events can be recovered. Day charts use actual local-day hours, including
23/25-hour daylight-saving days. Period comparisons cover equivalent elapsed windows. The impact
report labels time saved as an estimate and suppresses comparisons when boundary history is missing.
Use **Correct start date and time** to correct the saved start; no calendar date is inferred from
the earlier statement about Friday at 2 pm.

CI restores the original cached signing key and pins `SCROLL_GUARD_SIGNING_STORE` outside emulator
setup. It refuses to generate a replacement on cache loss. It checks the **actual final APK** after
build, instrumentation and immediately before phone publication: certificate SHA-256
`5f9a5eccde1fc15e4c60cc8ce3de4eab317fac4770c7795cebc49200840ad0cf`, package and version.
Only successful checks publish the prerelease APK. Private signing material is never uploaded.

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
