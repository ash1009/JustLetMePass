#!/usr/bin/env bash
# Runs the app on an emulator, presses Play in Listen, and saves screenshots and any crash log.
set -u
PKG=app.justletmepassthis
OUT=phone
mkdir -p "$OUT"
shot() { sleep "${2:-2}"; anr; adb exec-out screencap -p > "$OUT/$1.png"; echo "saved $1"; }
start() { adb shell am force-stop "$PKG"; adb shell am start -W -n "$PKG/app.jlpt.ui.MainActivity" --ez skip_intro true "$@" > /dev/null 2>&1; }
# A slow emulator sometimes shows "isn't responding" for another app: press Wait so it doesn't cover ours.
anr() { for _ in 1 2 3; do if python3 ci/ui.py has "Wait"; then python3 ci/ui.py tap "Wait" || true; sleep 2; else return 0; fi; done; }
tap() { anr; python3 ci/ui.py tap "$@" >> "$OUT/taps.txt" 2>&1 || true; }

adb logcat -c || true
# Put the old version's data under the new app, to check the update keeps progress.
if adb shell ls /data/local/tmp/olddata.tar > /dev/null 2>&1; then
  adb shell am start -W -n "$PKG/app.jlpt.ui.MainActivity" > /dev/null 2>&1; sleep 3; adb shell am force-stop "$PKG"
  adb shell "su 0 tar -C /data/data/$PKG -xf /data/local/tmp/olddata.tar && su 0 chown -R \$(su 0 stat -c %u /data/data/$PKG):\$(su 0 stat -c %g /data/data/$PKG) /data/data/$PKG" && echo "old data restored" >> "$OUT/upgrade.txt"
fi
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard || true

sleep 20
anr
start
shot 00_today_after_update 5
start --es route plan
shot 00b_plan 4
start --es route study
shot 00c_study 4
python3 ci/ui.py texts > "$OUT/00c_study.txt" 2>&1 || true
# Answer one question: tap past new items, then tap an option and look at the bottom buttons.
SIZE=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); W=${SIZE%x*}; H=${SIZE#*x}
for _ in 1 2 3 4 5 6; do if python3 ci/ui.py has "Got it"; then tap "Got it"; sleep 1.5; else break; fi; done
sleep 1; adb shell input tap $((W / 2)) $((H * 55 / 100))
shot 00d_answered 2
tap "Play sound"; sleep 3
python3 ci/ui.py texts > "$OUT/00d_answered.txt" 2>&1 || true
if python3 ci/ui.py has "Misclick"; then tap "Misclick"; shot 00e_after_misclick 2; fi
start --es route listen
shot 01_listen 6
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

# Swipes: tabs, then from one reading to the next.
start
shot 14_today 3
adb shell input swipe 900 1300 150 1300 200
shot 15_swiped_to_learn 2
start --es route reading
shot 16_reading 3
python3 ci/ui.py texts > "$OUT/16_texts.txt" 2>&1 || true
adb shell input swipe 900 1300 150 1300 200
shot 17_next_reading 2
python3 ci/ui.py texts > "$OUT/17_texts.txt" 2>&1 || true

# Back keeps the scroll place: scroll the word list, open a word, go back.
start --es route words
shot 18a_words_top 3
for _ in 1 2 3; do adb shell input swipe 540 1800 540 500 300; sleep 1; done
python3 ci/ui.py texts > "$OUT/18b_before.txt" 2>&1 || true
adb shell input tap 540 1200
shot 18c_word_open 2
adb shell input keyevent KEYCODE_BACK
shot 18d_after_back 2
python3 ci/ui.py texts > "$OUT/18e_after_back.txt" 2>&1 || true

start --es route kanji1
shot 19_kanji_notes 3
adb shell input swipe 540 1800 540 600 300
shot 19b_kanji_notes_lower 2

# Mock test: a full paper, each timed part, a listening question played once.
start --es route test
shot 20_mock_start 4
tap "Start"
shot 21_part1_intro 2
tap "Begin"
shot 22_vocab_q1 2
python3 ci/ui.py texts > "$OUT/22_texts.txt" 2>&1 || true
for _ in $(seq 1 27); do tap "Skip"; done
shot 23_vocab_last 1
tap "End this part"
tap "Begin"
shot 24_grammar_q1 2
for _ in $(seq 1 16); do tap "Skip"; done
shot 25_star 1
for _ in $(seq 1 4); do tap "Skip"; done
shot 26_text_grammar 1
for _ in $(seq 1 8); do tap "Skip"; done
tap "End this part"
tap "Begin"
shot 27_listening 2
tap "Play"
shot 28_listening_playing 4
for _ in $(seq 1 27); do tap "Skip"; done
tap "Finish"
shot 29_results 3
python3 ci/ui.py texts > "$OUT/29_texts.txt" 2>&1 || true

adb logcat -d -b crash > "$OUT/crash.txt" || true
adb logcat -d | grep -E "AndroidRuntime|FATAL|jlpt|justletmepass|PlaylistService|TextToSpeech|JlptVoice|MediaPlayer" | tail -300 > "$OUT/log.txt" || true
echo done
