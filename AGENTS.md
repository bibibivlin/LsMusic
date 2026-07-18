# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Android app (`:app`) written in Kotlin with Jetpack Compose and Material 3 Expressive. Production code is under `app/src/main/java/com/linxyi/lsmusic/`:

- `dlna/`: jUPnP discovery, DIDL-Lite browsing, renderer compatibility, and AVTransport commands.
- `listenbrainz/`: ListenBrainz HTTP requests, playback-session tracking, upload rules, and MusicBrainz ID extraction.
- `ui/`: Compose screens, `LsMusicViewModel`, preferences, and theme files.
- `playback/`: local Media3 and remote Android media-session services.
- `res/` and `AndroidManifest.xml`: resources, permissions, service declarations, and app configuration.

Unit tests live in `app/src/test/`; device/instrumented tests live in `app/src/androidTest/`. Dependency versions belong in `gradle/libs.versions.toml`.

## Build, Test, and Development Commands

Run commands from the repository root:

```bash
./gradlew :app:assembleDebug    # Build the debug APK
./gradlew :app:testDebugUnitTest # Run JVM unit tests
./gradlew :app:connectedDebugAndroidTest # Run tests on a connected device
./gradlew :app:lintDebug        # Run Android lint
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Validate DLNA behavior on a physical device and a non-isolated Wi-Fi network; emulators are not reliable for SSDP multicast discovery. ListenBrainz integration tests require internet access and a dedicated test account/token; avoid adding listens to a user's real history during routine verification.

## Coding Style & Naming Conventions

Use Kotlin's standard four-space indentation and idiomatic Kotlin: immutable `val` where possible, expression bodies for small functions, and trailing commas in multiline declarations. Use PascalCase for classes and composables (`NowPlayingScreen`), camelCase for functions and properties (`refreshPlaybackProgress`), and UPPER_SNAKE_CASE only for constants.

Keep UI state in `LsMusicViewModel`; keep DLNA protocol details in `DlnaController` and ListenBrainz protocol details in `ListenBrainzClient`. Preserve server-provided DIDL metadata when changing renderer compatibility or MusicBrainz extraction code.

Never log, hard-code, or include ListenBrainz user tokens in fixtures. Tokens must remain in the dedicated `ls_music_secrets` preferences file, which is excluded from cloud backup and device transfer. Validate a non-empty token with `/1/validate-token` before replacing the saved value; validation failures must preserve the previous token and distinguish invalid credentials from connectivity failures.

Playback reporting must behave consistently for local Media3 and remote DLNA playback. Send `playing_now` once per playback generation, count only time actually spent in the playing state, and submit a permanent listen only after playback ends when either the configured duration or percentage threshold is met. Run `:app:lintDebug` before handing off changes.

## Testing Guidelines

Use JUnit4 for deterministic logic tests. Name tests as behavior statements, for example `parseTimeMs_supportsDlnaFractionalDuration`. Add unit tests for parsing, queue behavior, playback/reporting state transitions, threshold evaluation, MusicBrainz metadata extraction, and token-validation error classification. Unit tests must not call the live ListenBrainz service. Reserve `androidTest` for Android framework or UI integration. No coverage threshold is configured—cover changed behavior proportionately.

## Commit & Pull Request Guidelines

The current history only contains `Initial commit`, so no established commit format exists. Use concise imperative subjects, preferably scoped, such as `dlna: support AK Connect SOAP playback`. Keep commits focused.

Pull requests should explain user-visible behavior, identify affected playback paths (local or remote), list verification commands, and include screenshots for UI changes. For DLNA fixes, record the tested server/renderer family and any compatibility fallback. For ListenBrainz changes, describe `playing_now` and permanent-listen behavior, threshold semantics, metadata fallback, token validation, and whether a test account was used.
