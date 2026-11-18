# Architecture

Clean Architecture with **MVI** in one Gradle module. No Hilt/Room/coroutines/Compose: every
library costs RAM and APK size on weak boxes. Dependencies are wired by hand.

```
presentation  ->  domain  <-  data
   (Android UI)   (pure Kotlin)   (Android + I/O)
```

Dependencies point inwards: `domain` knows nothing about Android; `data` implements the
interfaces that `domain` defines; `presentation` talks only to `domain` (use cases + models).

## Packages (`com.mmlauncher.tv`)

| Package | Contents |
|---|---|
| `core` | `MviStore` (state + effects), `Scheduler` (`AndroidScheduler`, test-only `ImmediateScheduler`) |
| `domain.model` | `AppInfo`, `RamInfo`, `HomeSource`, `Language`, `Fetch`, ... (no Android types) |
| `domain.repository` | Contracts: `AppsRepository`, `PreferencesRepository`, `SystemRepository`, `DiagnosticsRepository`, `UpdateRepository`, `FilesRepository`, `SystemScreens`, ... |
| `domain.usecase` | One small class per action: `LoadHomeApps`, `ManageFavorites`, `ResolveAutostart`, `GetRamUsage`, `ListDirectory`, ... |
| `data` | Implementations: SharedPreferences, PackageManager, `dumpsys` parsers, `LocalAdb`, HTTP update, `java.io.File` |
| `di` | `AppContainer`: the composition root. Creates repositories, use cases and one store per Activity |
| `presentation.<feature>` | `*Contract.kt` (State / Intent / Effect), `*Store.kt`, the Activity (a "dumb" View) |
| `presentation.tools` | Tools menu and its four screens: device info, network, app manager, logs |
| `presentation.common` | Shared UI: adapters, `Fonts`, `LocaleHelper`, `Mouse`, `Msg` (string ids for the View) |

## MVI in this app

```
View --dispatch(Intent)--> Store --use cases--> data
 ^                          |
 |<---- State (immutable) --+     one-shot Effects: toast, navigate, dialog
```

* **State**: a single immutable `data class` per screen; the View only renders it.
* **Intent**: everything the user (or the system) does, as a sealed interface.
* **Effect**: things that must happen once and must not replay on rotation (navigation,
  toasts, dialogs). Queued until the View is bound.
* The Store has no `Context` and no string lookups: messages are `Msg(@StringRes, args)`.
* `Activity` = View: `bind` in `onStart`, `unbind` in `onStop`, `clear` in `onDestroy`.
  It also owns platform-only work (permission screens, receivers, bitmaps).

Threading: state changes happen on the main thread. `io {}` = small pool for local work,
`network {}` = its own thread for slow I/O (HTTP, local ADB that may wait for user approval),
so one slow task can never stall other screens.

## Adding a screen
1. Models/contracts needed in `domain` (+ use cases).
2. Implement the contract in `data`, wire it in `AppContainer`.
3. `XContract.kt` + `XStore.kt` + `XActivity.kt` in `presentation/x`; register the Activity in the manifest.
4. Unit-test the store with fakes (see `StoresTest`).

## Tests (`app/src/test`, plain JVM, no emulator)
`./gradlew testDebugUnitTest`. Use cases and stores run against in-memory fakes
(`Fakes.kt`) with `ImmediateScheduler`, so countdowns and timers are tested without waiting.
Parsers (`dumpsys` output, `update.json`) are pure functions with their own tests.
