#!/usr/bin/env bash
# Usage: store-shots.sh <apk> <outdir>
# Opens Party Lights on the emulator and captures screenshots for the store listing.
set -x
APK="$1"; OUT="$2"; PKG=com.ash.partylights
mkdir -p "$OUT"

# Find an on-screen element by its text (or content-desc) and tap its centre.
tap_text() {
  for i in 1 2 3 4 5 6; do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1
    xy=$(python3 - "$1" <<'PY'
import re, sys
want = sys.argv[1]
xml = open("ui.xml", encoding="utf-8").read()
for node in re.findall(r"<node [^>]*>", xml):
    t = re.search(r' text="([^"]*)"', node); d = re.search(r' content-desc="([^"]*)"', node)
    if (t and t.group(1) == want) or (d and d.group(1) == want):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        x1, y1, x2, y2 = map(int, b.groups())
        if y2 > y1 and x2 > x1:
            print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
)
    if [ -n "$xy" ]; then adb shell input tap $xy; sleep 1.5; return 0; fi
    adb shell input swipe 540 1700 540 900 400; sleep 1   # scroll down and look again
  done
  echo "could not find: $1"; return 1
}
# Leave the light show and make sure the home screen is back.
back_home() {
  for i in 1 2 3; do
    adb shell input keyevent KEYCODE_BACK; sleep 2
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1
    grep -q 'Party Lights' ui.xml && return 0
  done
  adb shell am force-stop $PKG; adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1; sleep 6
}
scroll_top() { for i in 1 2 3 4 5; do adb shell input swipe 540 700 540 2000 200; done; sleep 1; }
shot() { sleep "${2:-1}"; adb exec-out screencap -p > "$OUT/$1.png"; }

adb wait-for-device
adb shell settings put global window_animation_scale 0
# Clean status bar for screenshots
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 2100
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false

# Skip the one-time "Viewing full screen" notice that covers immersive apps
adb shell settings put secure immersive_mode_confirmations confirmed
adb install -r "$APK"
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1
sleep 8
shot 00-warning
tap_text "I understand"
shot 01-home 2

# Effect screens: pick the effect, start the show, wait for controls to hide.
n=10
for mode in "Disco" "Lasers" "Kaleidoscope" "Tunnel" "Aurora" "Trippy Swirl" "Lava"; do
  scroll_top
  tap_text "$mode" || continue
  scroll_top
  tap_text "START THE PARTY" || continue
  shot "$n-show-controls-${mode// /-}" 2.5
  shot "$n-show-${mode// /-}" 9
  back_home
  n=$((n + 1))
done

# Palette variations on Kaleidoscope and Tunnel
for pair in "Kaleidoscope:Neon" "Tunnel:Fire" "Disco:Ocean" "Aurora:Sunset"; do
  mode=${pair%%:*}; pal=${pair##*:}
  scroll_top; tap_text "$mode"; tap_text "$pal"
  scroll_top; tap_text "START THE PARTY" || continue
  shot "$n-show-$mode-$pal" 9
  back_home
  n=$((n + 1))
done

# LED text banner
scroll_top; tap_text "LED Text"; tap_text "Rainbow"
adb shell uiautomator dump /sdcard/ui.xml >/dev/null; adb pull /sdcard/ui.xml ui.xml >/dev/null
scroll_top
shot 30-home-ledtext 1
scroll_top; tap_text "START THE PARTY" && { shot 31-show-LED-text 9; back_home; }

# Home screen scrolled, to show settings
scroll_top; shot 40-home-top 1
adb shell input swipe 540 1800 540 800 600; shot 41-home-mid 1.5
adb shell input swipe 540 1800 540 800 600; shot 42-home-lower 1.5
adb shell input swipe 540 1800 540 800 600; shot 43-home-bottom 1.5
ls -la "$OUT"
