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

## Before the first public release
1. Test on at least 3 real boxes (different chips, one with Android 7 or 8). Record the model,
   Android version and which buttons failed; add working intents to `settings_chains.json`.
2. Have a native speaker review the Turkmen strings (`values-tk/strings.xml`) and the Turkmen
   section of the download page.
3. Create the real signing key and publish (see `RELEASE.md`).
4. Check the HDMI and USB cards with a real HDMI source and a USB stick.
