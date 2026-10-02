# Scroll Guard

Scroll Guard is a private, offline Android distraction blocker designed for Nothing OS and other
modern Android devices. It blocks YouTube Shorts and Instagram Reels while leaving the rest of
those apps usable, and can block TikTok completely.

This project is based on Atick Faisal's Apache-2.0 licensed
[Shorts Blocker](https://github.com/atick-faisal/Shorts-Blocker). The detector engine, diagnostics,
privacy configuration, visual theme, application identity, and TikTok support have been modified.

## Features

- Multi-signal YouTube Shorts detection
- Multi-signal Instagram Reels detection
- Profile-page exclusion and event-source diagnostics for Instagram Home-feed Reels
- Automatically scrolls past confidently detected Reels in the Instagram Home feed
- Covers visible Home-feed Reel videos while a skip is attempted; swipes pass through the cover
- Home detection does not require a selected Home tab; profiles, Stories and messages are excluded
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

The app uses an Android Accessibility Service after the user explicitly enables it. YouTube and
Instagram detectors score several independent interface signals rather than trusting one fragile
screen element. TikTok is blocked by package name and does not depend on its interface layout.

Only packages enabled by the user are included in the service's event filter. Diagnostic samples
live in process memory and contain no captions, messages, usernames, or account content.

## Install a test build

1. Build and install `app-debug.apk`, or install the supplied APK.
2. On recent Android versions, open **App info** for Scroll Guard. If Android blocks the
   accessibility permission for a sideloaded app, open the three-dot menu and choose
   **Allow restricted settings**.
3. Open Scroll Guard, read the disclosure, and enable its accessibility service.
4. Enable YouTube, Instagram, or TikTok from the app.

## Detector test

1. Clear the **Detector check** panel.
2. Open a normal YouTube or Instagram screen and verify it remains usable.
3. Open a Short or a Reel in the dedicated viewer. Scroll Guard should press Back.
4. Scroll to a Reel in Instagram Home. Its video should be covered and a feed skip attempted.
   A cover hides the picture; audio may continue until the Reel is scrolled away. Android UI
   changes may require detector updates. The cover alone does not pause the Instagram player.
5. Return to Scroll Guard. The panel shows the detector score, threshold, reasons, and safe resource
   IDs from the test window.
   It reports cover acceptance independently from a scroll or swipe request.
6. Open TikTok. Scroll Guard should immediately return to the Home screen.

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
