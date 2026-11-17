# Building a signed release

1. Create your own signing key ONCE and keep it (and its passwords) in a safe place, backed up.
   Every future update must be signed with the same key, or Android refuses to install it.

       keytool -genkeypair -v -keystore ../mm-launcher-release.jks -alias mm-launcher \
         -keyalg RSA -keysize 4096 -validity 36500

2. Copy `keystore.properties.example` to `keystore.properties` and fill it in (it is git-ignored).
3. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
4. Build:

       ./gradlew assembleRelease

   Result: `app/build/outputs/apk/release/app-release.apk` (signed, R8-shrunk, about 0.4 MB).
5. Check the signature:  `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`
6. Publish: see `docs/README.md` (copy APK, update `sha256` and `update.json`).

Notes
- `targetSdk` is 34. Do NOT lower it: on Google TV with Google Play Protect, an app that targets an old SDK and
  declares permissions such as WRITE_SECURE_SETTINGS is blocked at install ("Unsafe app blocked").
  The file manager therefore needs "All files access" on Android 11+ (see `Dump.GRANT_FILES`).
- Fonts: Roboto (Apache License 2.0) is bundled in `res/font/roboto_static.ttf`.
