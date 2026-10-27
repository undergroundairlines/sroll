# Isolated HushGram evaluation

This experiment evaluates upstream HushGram v0.0.16 (`1cf4d5628c6194ebccad015c4d33e022a77ed8e6`) without changing the Scroll Guard application. It runs only on a disposable GitHub Actions emulator, with a fresh unsigned-in installation and networking disabled during Android execution. Never point it at a personal phone.

The input is Instagram 452.1.0.53.64 (385811006), pinned by SHA-256 and verified with Android SDK `apksigner` against Meta's certificate before patching. The Morphe CLI and HushGram patch bundle are also pinned. An ephemeral lab key signs the modified APK; it is unrelated to either Scroll Guard signing identity. Neither Instagram APKs nor private keys are uploaded. Uploaded evidence contains only anonymous emulator UI, build metadata, logs and verdicts.

The available input is a **base APK missing its required vendor density split**. Both original and directly patched versions were actually rejected by Android with `INSTALL_FAILED_MISSING_SPLIT`. The current native-hook experiment wraps that verified base alone and runs the pinned upstream's unmodified ordinary merger before patching. This normalizes the split manifest; it does not supply the missing resources. `scope.json` explicitly records this limitation. Any successful result is therefore a base-only lab result, not proof that a complete client is ready to distribute or use. A second mirror advertised 452.1 but supplied 440.0; that file was rejected and is not used.

115 patches are selected from the released 117-entry catalog. `Install next to Instagram` is excluded to preserve the tested target package; `Change version code` is excluded so native tests remain bound to the actual vendor version code. Patching success alone does not establish any feature's runtime behavior.

`add_native_cases.py` modifies only upstream's test instrumentation. That evaluation delta is GPL-3.0-only, following the upstream runner's licence, with the upstream authors credited in the file. The original runner is fetched from its pinned repository, retaining its notices and licence. No HushGram production code is incorporated into Scroll Guard.

The additional native cases have deliberately limited claims:

* `reel-input` loads the installed patched Instagram's actual ViewPager2 and HushGram extension. It registers one pager, invokes the actual patched input setter 50 times and reads its boolean field. It checks an unregistered pager and restores input with the switch off. The field mapping comes from the pinned vendor DEX, whose setter stores its boolean parameter in `ViewPager2.A09`; the public getter is stripped in this build. This tests the injected setter, not physical swipes on a signed-in Reel.
* `home-filters` calls the actual installed extension with explicitly synthetic objects for four suggested-Reel unit types plus `MEDIA`. It checks switch-off retention, switch-on filtering and the separate whole-Home filter. These objects are not captured Instagram feed data. This does not prove that real server feed items reach these helpers, or that Stories and chats stay usable.

Upstream's positive and negative identity controls remain enabled. Existing presence/visual receipt cases are additional tests of privacy hooks, not evidence for Reel blocking. Every verdict identifies the exact installed APK, signer, version, injected build identity and isolated Android user. Read each case and `featuresPassed`; a successful install or activity launch is insufficient.

The source-test job preserves strict dependency verification. It adds only individually publisher-verified checksums absent from upstream metadata for Linux aapt2 and test BOM metadata. Fixture-dependent upstream tests can skip without the vendor's multi-build fixture set; actual counts including skips are recorded.

Signed-in Home filtering, repeated physical swipes, opening a shared Reel, sending photos, messaging, Story viewing and Nothing OS behavior remain outside this anonymous lab until exercised in an actual signed-in session. Do not turn these limits into a claim of real-phone compatibility.

