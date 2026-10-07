#!/usr/bin/env bash
# Verify the actual deliverable after any stage that can rebuild or replace it.
set -euo pipefail

apk="${1:-app/build/outputs/apk/debug/app-debug.apk}"
stage="${2:-final}"
expected_certificate="5f9a5eccde1fc15e4c60cc8ce3de4eab317fac4770c7795cebc49200840ad0cf"
expected_package="app.scrollguard"
expected_version_code="402"
expected_version_name="0.4.2"
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"

if [[ ! -f "$apk" ]]; then
    echo "::error::Installable Scroll Guard APK is missing: $apk"
    exit 1
fi
if [[ -z "$sdk" || ! -d "$sdk/build-tools" ]]; then
    echo "::error::Android SDK build-tools are needed to verify APK identity."
    exit 1
fi
build_tools="$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
report_directory="app/build/reports/delivery"
mkdir -p "$report_directory"
if ! certificates="$("$build_tools/apksigner" verify --verbose --print-certs "$apk" 2>&1)"; then
    printf '%s\n' "$certificates" | tee "$report_directory/$stage-apksigner.txt"
    echo "::error::The final APK signature does not verify."
    exit 1
fi
# These are public certificates and signature metadata; no private signing material.
printf '%s\n' "$certificates" > "$report_directory/$stage-apksigner.txt"
# SDK 37 prints "V2 Signer:" where older tools print "Signer #1".
# One signer can be listed for several schemes; every certificate must match the installed key.
signer_digests="$(printf '%s\n' "$certificates" | tr -d '\r' |
    sed -n -E 's/^(Signer #[0-9]+|V[234] Signer(:| #[0-9]+:)) certificate SHA-256 digest: ([0-9A-Fa-f]+)[[:space:]]*$/\3/p' |
    tr '[:upper:]' '[:lower:]' | sort -u)"
signer_count="$(printf '%s\n' "$signer_digests" | awk 'NF { count++ } END { print count+0 }')"
certificate="$signer_digests"
if [[ "$certificate" != "$expected_certificate" || "$signer_count" != "1" ]]; then
    printf '%s\n' "$certificates"
    echo "Parsed signer certificate count: $signer_count; expected certificate SHA-256: $expected_certificate"
    echo "::error::APK signing certificate does not match installed Scroll Guard 0.4.0. Refusing an incompatible update."
    exit 1
fi

badging="$("$build_tools/aapt" dump badging "$apk")"
identity="$(printf '%s\n' "$badging" | sed -n '1p')"
package="$(printf '%s\n' "$identity" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
version_code="$(printf '%s\n' "$identity" | sed -n "s/.*versionCode='\([^']*\)'.*/\1/p")"
version_name="$(printf '%s\n' "$identity" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")"
if [[ "$package" != "$expected_package" || "$version_code" != "$expected_version_code" || "$version_name" != "$expected_version_name" ]]; then
    echo "::error::Unexpected APK identity: $identity"
    exit 1
fi

apk_sha256="$(sha256sum "$apk" | cut -d ' ' -f 1)"
cat > "$report_directory/$stage.txt" <<EOF
Stage: $stage
Package: $package
Version: $version_name ($version_code)
Signing certificate SHA-256: $certificate
APK SHA-256: $apk_sha256
APK: $apk
EOF
cat "$report_directory/$stage.txt"
