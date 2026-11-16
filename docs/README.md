# Publishing MM Launcher on GitHub Pages

1. Create a GitHub repository (for example `mmlauncher`) and push this project.
2. Settings -> Pages -> deploy from branch, folder `/docs`.
3. Your site will be `https://mammetmyrat.github.io/mmlauncher/`.
4. Put `Config.DEFAULT_UPDATE_URL` (app/src/main/java/com/mmlauncher/tv/Config.kt) to
   `https://mammetmyrat.github.io/mmlauncher/update.json`, then rebuild.

## Releasing a version
1. Bump `versionCode` / `versionName` in `app/build.gradle.kts`.
2. Build a SIGNED release APK with your own key (keep the key and its password safe: every
   update must be signed with the same key or Android refuses to install it).
3. Copy the APK to `docs/` as `mm-launcher.apk` (download button) and `mm-launcher-<version>.apk`.
4. `shasum -a 256 docs/mm-launcher-<version>.apk` -> paste into `docs/update.json` as `sha256`.
5. Update `versionCode`, `versionName`, `apkUrl`, `notes` in `docs/update.json`, commit, push.

`update.json` is the only file the launcher polls. The `chains` block lets you ship fixed
intents for system settings screens (see `settings_chains.json`) without a new APK: raise
`chains.version` whenever you change that file.

The APK URL must be https. A missing or wrong `sha256` makes the launcher refuse the install.
