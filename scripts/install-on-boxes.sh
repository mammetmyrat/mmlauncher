#!/usr/bin/env bash
# For sellers: install MM Launcher on one or more boxes over ADB before sale.
#
#   scripts/install-on-boxes.sh mm-launcher.apk 192.168.1.50 192.168.1.51
#   scripts/install-on-boxes.sh mm-launcher.apk               # uses the devices adb already sees
#   Options: --no-home   install only, do not try to set the home screen
#
# Needs adb (Android platform-tools) and ADB/network debugging enabled on each box.
set -uo pipefail

NO_HOME=0
ARGS=()
for a in "$@"; do
  if [ "$a" = "--no-home" ]; then NO_HOME=1; else ARGS+=("$a"); fi
done
APK="${ARGS[0]:-}"; TARGETS=("${ARGS[@]:1}")
[ -n "$APK" ] && [ -f "$APK" ] || { echo "Usage: $0 <apk> [--no-home] [box-ip ...]" >&2; exit 2; }
command -v adb >/dev/null || { echo "adb not found. Install Android platform-tools." >&2; exit 2; }

PKG=com.mmlauncher.tv
ACT=$PKG/.MainActivity
FAILED=0

do_box() {
  local serial="$1"
  echo "== $serial"
  local out
  out="$(adb -s "$serial" install -r "$APK" 2>&1)"
  if ! printf '%s\n' "$out" | grep -q "^Success"; then
    echo "   install FAILED: $(printf '%s\n' "$out" | grep -E 'Failure|error' | head -1)"
    FAILED=$((FAILED+1)); return
  fi
  echo "   installed"
  [ "$NO_HOME" = 1 ] && return
  # Results differ per Android version / vendor, so every step is best effort.
  adb -s "$serial" shell cmd package set-home-activity "$ACT" >/dev/null 2>&1
  # The command may print "success" and still change nothing (Google TV), so check the real result.
  local home
  home="$(adb -s "$serial" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | tail -1)"
  if printf '%s' "$home" | grep -q "^$PKG/"; then
    echo "   verified: MM Launcher is the home screen"
  else
    echo "   NOT home yet (home is: ${home:-unknown}). Manual step: press Home, choose MM Launcher > Always,"
    echo "   or see the Google TV section of the download page."
  fi
  adb -s "$serial" shell am start -n "$ACT" >/dev/null 2>&1 && echo "   launched"
}

if [ ${#TARGETS[@]} -eq 0 ]; then
  while read -r serial state; do
    [ "$state" = "device" ] && do_box "$serial"
  done < <(adb devices | tail -n +2)
else
  for ip in "${TARGETS[@]}"; do
    adb connect "$ip:5555" >/dev/null 2>&1
    do_box "$ip:5555"
  done
fi
[ "$FAILED" -eq 0 ] && echo "Done." || { echo "$FAILED box(es) failed."; exit 1; }
