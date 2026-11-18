# Acceptance status (against section 10 of the specification)

Legend: OK = verified, PARTLY = verified only in emulators, NO = not verified, DROPPED = out of scope.

| # | Criterion | Status | What was actually checked |
|---|---|---|---|
| 1 | Installs and is selectable as home on 3+ boxes incl. Android 7 | NO | Only emulators: Android TV API 36 and a phone image of Android 7.0 (1 GB RAM). No real box tried yet. On the Google TV emulator `cmd package set-home-activity` printed success but did not change the home screen (the install script now verifies the result instead of trusting it); the disable-original-launcher route was not tried. |
| 2 | Every quick-settings button opens a screen or shows a message, never crashes | PARTLY | All 8 buttons open the right screen on the Google TV emulator; missing screens fall back and end in a toast. Vendor firmware (Amlogic/Rockchip/Allwinner) not tried. |
| 3 | Developer mode can be enabled from the hint | PARTLY | Both states checked on the emulator: off shows the hint and opens About; on opens the developer screen. |
| 4 | Remote and mouse work on all screens without losing focus | PARTLY | D-pad on home, menus, settings, wizard, file manager (a wizard focus bug was fixed in stage 8). Mouse click, hover and wheel checked. Air mouse hardware not tried. |
| 5 | Language switches to RU/TK/EN without reboot; Turkmen letters render | PARTLY | Switching and glyphs (ä ç ž ň ö ş ü ý) verified on API 36 and Android 7. Turkmen strings were written by the assistant and need review by a native speaker. |
| 6 | Autostart runs after power-on and can be cancelled | PARTLY | Countdown, launch, cancel and "once per boot" verified by simulating a new boot id. A real reboot was not done. |
| 7 | PIN protects selected apps | DROPPED | Parental control was removed from the first release by decision. |
| 8 | RAM and cold start within targets on a 1 GB device | PARTLY | Android 7.0 emulator with 1 GB: cold start 171-215 ms, 27 MB PSS idle. Release APK about 0.4 MB. Emulators are faster than cheap boxes. |

Other items done beyond the list: file manager, HDMI/USB cards (code only, no hardware), update
check with SHA-256, first-run wizard, privacy policy, seller guide.

## Added after the first review (Developer, RAM, ROM screens)
- Quick row is now: Developer mode (own screen), System settings (one button), Memory (RAM), Storage (ROM), Launcher settings.
- RAM / Storage totals use only public APIs and are verified. **Per-app numbers** use a built-in ADB client to the box's own
  adbd (127.0.0.1:5555, needs ADB over network). Verified only against a test double of adbd (full auth flow, key format
  checked byte for byte against the real `adb pubkey`, `dumpsys meminfo` parsed and listed). **Real adbd (Google TV emulator, Android 16) accepted and parsed the launcher's public key and
  showed its own "Allow USB debugging?" dialog** (system log: `AdbDebuggingManager: Received public key ... mmlauncher@tv`).
  The step after the user's approval (signature check on later connections, key remembered with "Always allow")
  was **not confirmed**: the dialog kept reappearing in that test, which needs a clean re-test
  (approve once with "Always allow", press Refresh once). **Not verified on a real box**; also untested: Android 11+ "wireless debugging" (TLS pairing is not supported).
- On Android 7 `dumpsys diskstats` has no per-app sizes, the screen falls back to installed (APK) size and says so.
- Developer mode toggles need WRITE_SECURE_SETTINGS (verified: grant through the test adbd, then toggle works).
- Play Protect finding: on Google TV an old `targetSdk` plus these permissions is blocked at install. `targetSdk` raised to 34,
  file manager now needs "All files access" on Android 11+ (permission screen may not exist on TV: the app shows the adb command).

## Architecture refactor (Clean Architecture + MVI)
- Code reorganised into `core` / `domain` / `data` / `presentation` / `di` (see ARCHITECTURE.md). Behaviour kept; checked by hand
  on the Google TV emulator: wizard, home, favorites menu, hide, settings, developer, files (roots, open folder, back).
- 40 JVM unit tests (use cases, stores, parsers) pass.
- A shared single-thread executor let a slow ADB request block the file manager's listing; fixed (3-thread `io` pool plus a separate `network` thread).

## Tools section and fixes from the first real TV (Android 13, vendor firmware)
- Files: when "All files access" cannot be granted (no system screen on the TV), the screen now explains and offers
  "Allow via local ADB" instead of closing silently.
- Default home: wizard and Launcher settings can set the role through the system dialog or through local ADB
  (`cmd role add-role-holder`), because "Always" in the home picker is forgotten after reboot on some firmwares.
- Developer screen: always offers the system developer options and the stock Android TV About screen, plus tips for
  firmwares without a "Build number" row. Not guaranteed on every firmware.
- New Tools menu: Device info (Widevine level, codecs, display, HDR), Network & ADB (IPs, port 5555 status),
  App manager (force stop, clear cache/data, disable, save APK, uninstall), Logs (system log via local ADB, own log without it,
  level filter, save to file). 58 JVM unit tests pass. **None of these screens was verified on a device or emulator yet**
  (the emulator hung during this work); check them on the TV. App-manager actions that need adbd depend on the local ADB step.

## Before the first public release
1. Test on at least 3 real boxes (different chips, one with Android 7 or 8). Record the model,
   Android version and which buttons failed; add working intents to `settings_chains.json`.
2. Have a native speaker review the Turkmen strings (`values-tk/strings.xml`) and the Turkmen
   section of the download page.
3. Create the real signing key and publish (see `RELEASE.md`).
4. Check the HDMI and USB cards with a real HDMI source and a USB stick.
