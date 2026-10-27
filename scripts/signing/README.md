# Fresh signing identity

The user approved app.scrollguard.fresh as a separate installation after loss of the legacy key.
The installed Fresh signing identity is pinned in fresh-certificate.sha256; it is different from
the legacy app.scrollguard certificate. Keep it for all future Fresh updates.

The first verified build saved signing-key.p12.cms with the release and as an encrypted artifact.
This is a CMS envelope encrypted to fresh-recovery-public.pem, which is only a public recovery
certificate, not the app-signing certificate. Its private recovery key stays outside Git under the
runtime user's .android/ScrollGuardFresh directory. The recovered signing keystore is stored there
under keys/<public-fingerprint>/signing-key.p12. Windows-user-encrypted recovery and keystore copies
are also saved privately under Documents/Scroll Guard Signing Backup.

Subsequent CI builds require the same keystore in SCROLL_GUARD_FRESH_SIGNING_KEY_BASE64. The workflow
fails if it is missing or its public certificate differs; it must never generate a replacement.
A local build can use the saved keystore through SCROLL_GUARD_SIGNING_STORE. The debug signing alias
is androiddebugkey. Private key files must stay outside Git and must not be uploaded unencrypted.
The encrypted release backup remains useful after Actions artifacts expire; it requires the saved
private recovery key to decrypt. App identity is checked again after instrumentation and before
publishing the exact APK. No APK may be replaced under an existing release tag with another key.
