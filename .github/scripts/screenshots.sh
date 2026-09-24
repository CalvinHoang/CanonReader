#!/usr/bin/env bash
# Installs the debug APK on the running emulator and captures the main screens.
set -u
APK_DIR="$1"
OUT=screenshots
PKG=com.canonreader.app
mkdir -p "$OUT"

shot() { sleep 3; adb exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"; }

# Taps the centre of the first view whose content-desc or text matches $1.
tap() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  local bounds
  bounds=$(adb shell cat /sdcard/ui.xml | tr '>' '\n' \
    | grep -E "(content-desc|text)=\"$1\"" | head -1 \
    | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/')
  if [ -z "$bounds" ]; then echo "no view matching $1"; return 1; fi
  set -- $bounds
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}

adb install -r -g "$APK_DIR"/*.apk
adb shell cmd uimode night no
adb shell am start -W -n "$PKG/.MainActivity"
sleep 15  # first launch unpacks the bundled database
shot 01-feed

# Open the first post in the feed.
adb shell input tap 540 900
shot 02-post
adb shell input swipe 540 1800 540 600 300
shot 03-post-scrolled
adb shell input keyevent KEYCODE_BACK
sleep 2

tap Explore && shot 04-explore
tap Saved && shot 05-saved
tap Feed

adb shell cmd uimode night yes
sleep 4
shot 06-feed-dark
adb shell input tap 540 900
shot 07-post-dark

adb logcat -d > "$OUT/logcat.txt"
exit 0
