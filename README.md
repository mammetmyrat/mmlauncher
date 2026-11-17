# MM Launcher

A free, light home screen for Android TV and Android boxes (Android 7.0+). Kotlin, plain Views/XML,
no Compose, almost no dependencies: about 0.4 MB release APK, about 30 MB RAM in the idle state
in emulator tests.

Features: all apps and favourites, hide/uninstall menu, quick access to system settings (including
Developer mode), HDMI inputs and USB, built-in file manager, wallpapers, RU / TK / EN interface
(switchable inside the app), RAM and storage screens (per-app numbers through the box's own local ADB), own Developer mode screen, autostart of one app, first-run wizard, optional update
check, remote/mouse/air-mouse support.

## Docs
- `ARCHITECTURE.md`: layers (domain / data / presentation), MVI, how to add a screen
- `RELEASE.md`: signing and building a release
- `scripts/release.sh`: build, sign-check, SHA-256, update `docs/update.json`
- `scripts/install-on-boxes.sh`: seller tool to install on many boxes over ADB
- `docs/`: GitHub Pages site (download page, seller guide, privacy policy)
- `ACCEPTANCE.md`: what is verified and what is not

## Build
    ./gradlew assembleDebug        # debug APK
    ./gradlew assembleRelease      # needs keystore.properties for a signed APK

Package: `com.mmlauncher.tv`. `targetSdk` is 34 (see `RELEASE.md`).
