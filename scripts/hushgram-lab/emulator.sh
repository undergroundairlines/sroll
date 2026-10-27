#!/usr/bin/env bash
set -euo pipefail
mkdir -p evidence
trap 'adb logcat -d -b crash > evidence/crashes.txt 2>&1 || true' EXIT
adb wait-for-device
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
adb devices | tee evidence/devices.txt
adb shell getprop > evidence/device-properties.txt
adb shell cmd connectivity airplane-mode enable
adb shell svc wifi disable
adb shell svc data disable
adb shell dumpsys connectivity > evidence/connectivity.txt
capture() {
  adb shell uiautomator dump /sdcard/lab-window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/lab-window.xml "evidence/$1.xml" >/dev/null 2>&1 || true
  adb exec-out screencap -p > "evidence/$1.png"
  adb shell dumpsys activity activities > "evidence/$1-activities.txt"
}
# This is a disposable CI emulator, never a personal device.
set +e
adb install --no-streaming lab/stock.apk > evidence/stock-install.txt 2>&1
stock_status=$?
set -e
if [ "$stock_status" = 0 ]; then
  adb shell am start -W -n com.instagram.android/com.instagram.mainactivity.InstagramMainActivity > evidence/stock-launch.txt 2>&1 || true
  sleep 10
  capture stock-logged-out
  adb shell am force-stop com.instagram.android
  adb uninstall com.instagram.android > evidence/stock-uninstall.txt
fi
set +e
adb install --no-streaming lab/patched.apk > evidence/patched-install.txt 2>&1
patched_status=$?
set -e
printf 'stock_install_exit=%s\npatched_install_exit=%s\n' "$stock_status" "$patched_status" > evidence/install-status.txt
if [ "$patched_status" != 0 ]; then
  cat evidence/patched-install.txt
  exit 1
fi
adb shell am start -W -n com.instagram.android/com.instagram.mainactivity.InstagramMainActivity --ez app.hushgram.extension.instagram.OPEN_SETTINGS true > evidence/patched-launch.txt 2>&1 || true
sleep 15
capture patched-settings
adb shell am force-stop com.instagram.android
serial=$(adb get-serialno | tr -d '\r')
model=$(adb shell getprop ro.product.model | tr -d '\r')
avd=$(adb emu avd name | head -n 1 | tr -d '\r')
export HUSHGRAM_DEVICE_LEASE_DIR="$RUNNER_TEMP/hushgram-device-leases"
mkdir -p "$HUSHGRAM_DEVICE_LEASE_DIR"
pwsh -NoProfile -File hushgram/scripts/native-runner.ps1 -Serial "$serial" -ExpectedModel "$model" -ExpectedAvd "$avd" -TargetApk "$PWD/lab/patched.apk" -Report "$PWD/evidence/patch-result.json" -Record "$PWD/evidence/patch-record.json" -Keystore "$PWD/lab/lab.p12" -KeyAlias lab -KeystorePassword android -KeyPassword android -Sdk "$ANDROID_HOME" -Verdict "$PWD/evidence/native-verdict.json" 2>&1 | tee evidence/native-runner.log
python3 - <<'PY'
import json
from pathlib import Path
verdict=json.loads(Path('evidence/native-verdict.json').read_text())
assert verdict['controlsProven'], 'Native identity controls did not hold'
assert verdict['featuresPassed'], 'Read the per-case failures; install success is insufficient'
print('All native cases passed within their documented scopes; signed-in flows remain unverified.')
PY
adb logcat -d -b crash > evidence/crashes.txt


