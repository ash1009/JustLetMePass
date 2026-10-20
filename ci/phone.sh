#!/usr/bin/env bash
# Runs the app on an emulator, presses Play in Listen, and saves screenshots and any crash log.
set -u
PKG=app.justletmepassthis
OUT=phone
mkdir -p "$OUT"
shot() { sleep "${2:-2}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "saved $1"; }
start() { adb shell am force-stop "$PKG"; adb shell am start -W -n "$PKG/app.jlpt.ui.MainActivity" --ez skip_intro true "$@" > /dev/null 2>&1; }
tap() { python3 ci/ui.py tap "$@" >> "$OUT/taps.txt" 2>&1 || true; }

adb logcat -c || true
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard || true

start --es route listen
shot 01_listen 4
tap "Play"
shot 02_after_play 6
python3 ci/ui.py texts > "$OUT/02_texts.txt" 2>&1 || true
tap "Pause"
shot 03_paused 3
tap "Play"
shot 04_resumed 4
adb shell cmd statusbar expand-notifications; shot 05_shade 2; adb shell cmd statusbar collapse
adb shell input keyevent KEYCODE_SLEEP; sleep 10; adb shell input keyevent KEYCODE_WAKEUP
shot 06_after_screen_off 3

start --es route story
shot 10_story 4
tap "Play"
shot 11_story_play 5
tap "Pause"
shot 12_story_paused 2
tap "Play"
shot 13_story_resumed 3

adb logcat -d -b crash > "$OUT/crash.txt" || true
adb logcat -d | grep -E "AndroidRuntime|FATAL|jlpt|justletmepass|PlaylistService|TextToSpeech" | tail -300 > "$OUT/log.txt" || true
echo done
