# HushGram emulator evaluation

This is an executed evaluation of released HushGram v0.0.16, not a claim that it works on the user's Nothing OS phone. HushGram remains a promising architecture for preserving Instagram's social features while reducing recommendations, but the available lab client currently crashes during activity startup. It is not a deliverable phone APK.

## Inputs and provenance

* HushGram source: `1cf4d5628c6194ebccad015c4d33e022a77ed8e6` ([v0.0.16](https://github.com/SysAdminDoc/HushGram/tree/v0.0.16)).
* Released `patches-0.0.16.mpp`: SHA-256 `51b81009ef870dba06cf0feb0af329ee52b5753a7290da6a26839748a9e4a270`, checked against the release checksum.
* Official Morphe Desktop CLI 1.19.1: SHA-256 `a51472252cd63b2eac635dbb5a154ff918bfc15268da89679feb3d760230c573`, checked against its release asset digest.
* Actual vendor input: `com.instagram.android`, version `452.1.0.53.64`, code `385811006`, arm64-v8a. SHA-256 `897b023309e3e2d80a99babe57be0c02f247b58380b2453d4f0682997f9259cd`.
* Meta signer verified cryptographically before patching: SHA-256 `3a10c50c18ba937506c2875e9047be74f8fd8b86fae2ff9feaa50a81fcb4c014`. A mirror's version label was not accepted as identity proof.

The supported input came through Freesoft's ordinary public guest download. It is a genuine signed **base APK**, but the required density split was absent. A second mirror advertised 452.1 but supplied standalone 440.0 and a 453.0 bundle; both were rejected for this experiment. No downloaded APK was executed before signature/identity checking. Neither vendor APKs nor signing keys are committed or uploaded as artifacts.

## What actually ran

The released catalog has 117 patches. The experiment selected 115, leaving out `Install next to Instagram` and `Change version code` so the target retained its package and real version code. All 115 applied; resource rebuilding completed, and the final lab APK was signed and verified using Android SDK tools with a disposable key unrelated to Scroll Guard's keys.

Android 15/API 35 ran in a disposable x86_64 Google APIs emulator. Its actual ABI list included `x86_64,arm64-v8a`, and it reported `libndk_translation.so`. Execution used airplane mode with Wi-Fi and cellular data disabled. No account was supplied, logged in, messaged or accessed. No personal phone was connected.

The original base APK and the directly patched base were both rejected by Android with `INSTALL_FAILED_MISSING_SPLIT`. [Recorded install attempt](https://github.com/undergroundairlines/sroll/actions/runs/40570087343).

For a narrower native-hook experiment, the verified base was wrapped alone and passed through the pinned upstream's unmodified `MergeSplits.java` and Morphe merger. This removed the split manifest requirement but **did not restore the missing density resources**. The resulting patched base-only APK installed. The launch command returned `Status: ok`, yet the captured UI showed the launcher, and the crash buffer recorded an `InflateException` resolving a theme attribute (`0x7f040d38`) during `InstagramMainActivity` startup. Thus activity-launch success was not counted as successful app use. [Base-only run and crash evidence](https://github.com/undergroundairlines/sroll/actions/runs/40571211860).

The resource failure cannot be attributed confidently to HushGram rather than the incomplete/rebuilt input without a complete supported vendor package and a working baseline. Removing the install requirement is not a fix for that missing input. The base-only APK is not offered for installation.

## Native and source test results

The released source tests completed on Java 21 in [the source-test job](https://github.com/undergroundairlines/sroll/actions/runs/40571211860/job/122425585567). I downloaded the resulting XML reports and independently counted them:

| Suite | Reported cases | Passed | Failed/errors | Skipped |
|---|---:|---:|---:|---:|
| Instagram extension runtime | 6,335 | 6,334 | 0 | 1 |
| Patch source tests | 2,129 | 1,677 | 0 | 452 |

The one runtime skip explicitly reports that FFmpeg is not installed. All 452 patch skips explicitly report that `HUSHGRAM_FIXTURE_DIR` is unset. They require the vendor multi-build fixture set. They were not counted as passes. These 8,011 passing source tests do not establish signed-in Instagram behavior.

The [final native run](https://github.com/undergroundairlines/sroll/actions/runs/40573132401) completed all six feature cases. The host verified the binding and positive control; the deliberately absent hook returned the expected failure, proving the negative control. `controlsProven=true` and `featuresPassed=true` are recorded in the [actual native verdict](evidence/hushgram-0.0.16-native-verdict.json). The cases ran in new Android user 10 with network access off; the runner and that user were removed afterward.

| Native case | Result | Scope |
|---|---|---|
| Reel input | Pass | Actual patched ViewPager2 setter rejected 50 re-enable attempts for a manually registered pager; unregistered pager stayed usable; switch-off restored input. |
| Home filters | Pass | Actual installed helpers filtered synthetic objects as expected; `MEDIA` remained; whole-Home filtering was separate. |
| Presence / paused presence | Both passed | Native outgoing-presence/privacy hook behavior; no requests sent. |
| Visual receipt / paused visual receipt | Both passed | Native receipt handling with an empty receipt; no message or media supplied. |
| Client startup/settings UI | **Fail** | Activity crashed with the theme/resource failure described above. |
| Signed-in Home, DMs, photo sending, Stories, physical swipes | **Not tested** | No functioning complete client or signed-in session. |

The [separate evidence judgement](evidence/hushgram-0.0.16-judgement.json) correctly reports `outcome=FAIL`, `settingsUiConfirmed=false`, `instagramCrashObserved=true` and `readyForPhone=false`, despite the native-case passes. The workflow also encountered a stray trailing CR after the completed native checks; line endings were normalized afterward. That shell error is independent of the recorded startup failure and does not invalidate the completed per-case verdicts.

After the tests, I cross-checked the final lab APK's recorded package, version, SHA-256 and SDK-verified signer against the native verdict. Lab APK SHA-256: `9e38bb5ea8bf70995befe82dd82943ea7a7601da649f304eed2e8e86cacf4a56`; disposable lab signer SHA-256: `cb2eab1bae5be0441b5e5fd4c2a9063a29949e4ac3374d73dc26dc9143e1e58f`. This identity is unrelated to either installed Scroll Guard signing identity. Injected build identity: `hg1:ae321e47c7336a8cb7c5b21da20a800688c489489f24ace6da78df5cb0628133`.

The added `reel-input` case uses the installed APK's actual ViewPager2 setter, not a replacement pager implementation. It manually registers one pager with HushGram, tries to enable input 50 times, checks another unregistered pager, then checks switch-off restoration. It reads the actual boolean field mapped from the pinned vendor DEX. This is not 50 physical swipes and does not prove that Instagram's real viewer setup registers its pager correctly.

The added `home-filters` case supplies explicitly synthetic enum-bearing objects to the installed extension's actual helpers. It checks the four suggested-Reel kinds, retention of `MEDIA`, and whole-Home filtering separately. It does not exercise a server feed response, real parser entry points or the Stories tray. Existing upstream presence and visual-receipt cases concern privacy hooks, not doomscrolling prevention.

Initial source-test failures exposed lab environment issues: the upstream Gradle wrapper lacked execute permission; strict verification lacked three Linux/test metadata artifacts; and Android SDK 36/37 Robolectric tests required Java 21. The checksum additions were fetched from official Google Maven/Maven Central and checked against publisher checksums, with strict verification retained. The Java 17 run's 342 failures were all SDK/Java compatibility failures, not established application defects. No tests were silently removed or counted as passes.

## What this means for Scroll Guard

HushGram's native feed filtering and pager hooks are the useful parts for the requested experience. Its suggested-Reel filter removes specific recommendation units while followed-account `MEDIA` posts stay. A separate video filter removes all video posts, which is a different restriction. Stopping Reel finger input can preserve viewing of an intentionally opened shared Reel and its native controls, but Instagram's own programmatic auto-scroll remains a documented limitation in this code. [Feed helper](https://github.com/SysAdminDoc/HushGram/blob/v0.0.16/extensions/instagram/src/main/java/app/hushgram/extension/instagram/reels/FeedReels.java), [pager helper](https://github.com/SysAdminDoc/HushGram/blob/v0.0.16/extensions/instagram/src/main/java/app/hushgram/extension/instagram/reels/ReelScrolling.java).

The native helpers also fail open when settings are not ready, HushGram is paused, or an exception occurs. The suggested-Reel filter retains unknown item types. This is a material difference from Scroll Guard's whole-Home/unknown-screen protection: direct native filtering has more specific information, but does not guarantee rejection of every future server-side Reel format.

These hooks execute inside modified Instagram. Scroll Guard's separate accessibility service cannot set Instagram's internal pager input or remove private feed objects. Reusing those capabilities would mean adopting a patched-client route or a different architecture. The evaluation-only runner delta is kept under GPL-3.0-only, with notices and full licence text in `scripts/hushgram-lab/`; no HushGram production code has been copied into Scroll Guard's application.

A candidate HushGram configuration for later signed-in acceptance would enable suggested-Reel filtering, suggested-post filtering and Stop Reels scrolling, while leaving whole-Home blocking, all-video filtering and hiding the Stories tray off. Programmatic Reel auto-scroll should also remain off. These are candidate settings, not a validated recommendation that this lab build is usable.

The exact missing binary input is a **complete Meta-signed Instagram 452.1.0.53.64 package, build 385811006, including its density split** (or a complete independently verified supported build with matching test bindings). Full messaging, photo attachments, Stories, followed-account Home posts, shared-Reel interaction and repeated real swipes remain unverified without a functioning complete client and signed-in session. A test account can be used later; personal account credentials are not needed for this lab report.

Scroll Guard Fresh's previously delivered APK and signing identity are unchanged by this evaluation. This work adds isolated test tooling and evidence only.
