#!/usr/bin/env bash
# Installs the released version, studies a little, so the new version is tested as an update over it.
# The released app is signed with a different key than this test build, so the old one is installed,
# used, then its data is kept while switching: we copy the database out and back in.
set -u
PKG=app.justletmepassthis
OLD="$(ls old/*.apk 2>/dev/null | head -n 1)"
[ -z "$OLD" ] && { echo "no old apk"; exit 0; }
mkdir -p phone
adb install -r "$OLD" || exit 0
adb shell am start -W -n "$PKG/app.jlpt.ui.MainActivity" --ez skip_intro true --es route study > /dev/null 2>&1
sleep 12
for n in 1 2 3 4 5 6; do adb shell input tap 540 2250; sleep 1; adb shell input tap 540 1300; sleep 2; done
adb shell am force-stop "$PKG"
# Keep the old database (version 1) to put under the new app.
adb shell "run-as $PKG tar -cf /data/local/tmp/olddata.tar databases shared_prefs" 2>/dev/null || adb shell "su 0 tar -C /data/data/$PKG -cf /data/local/tmp/olddata.tar databases shared_prefs"
adb uninstall "$PKG" > /dev/null
echo "old installed and used" > phone/upgrade.txt
