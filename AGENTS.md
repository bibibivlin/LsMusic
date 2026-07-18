# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Android app (`:app`) written in Kotlin with Jetpack Compose and Material 3 Expressive. Production code is under `app/src/main/java/com/linxyi/lsmusic/`:

- `dlna/`: jUPnP discovery, DIDL-Lite browsing, renderer compatibility, and AVTransport commands.
- `listenbrainz/`: ListenBrainz HTTP requests, playback-session tracking, upload rules, and MusicBrainz ID extraction.
- `ui/`: Compose screens, `LsMusicViewModel`, preferences, theme files, client-side album sorting in `AlbumSorting.kt`, and bounded browse-page/view-state caching in `LibraryBrowseState.kt`.
- `playback/`: local Media3 and remote Android media-session services.
- `res/` and `AndroidManifest.xml`: resources, permissions, service declarations, and app configuration.

Unit tests live in `app/src/test/`; device/instrumented tests live in `app/src/androidTest/`. Dependency versions belong in `gradle/libs.versions.toml`.

## Build, Test, and Development Commands

Run commands from the repository root:

```bash
./gradlew :app:assembleDebug    # Build the debug APK
./gradlew :app:assembleRelease  # Build an R8-optimized unsigned release APK
./gradlew :app:testDebugUnitTest # Run JVM unit tests
./gradlew :app:connectedDebugAndroidTest # Run tests on a connected device
./gradlew :app:lintDebug        # Run Android lint
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Validate DLNA behavior on a physical device and a non-isolated Wi-Fi network; emulators are not reliable for SSDP multicast discovery. ListenBrainz integration tests require internet access and a dedicated test account/token; avoid adding listens to a user's real history during routine verification.

## Coding Style & Naming Conventions

Use Kotlin's standard four-space indentation and idiomatic Kotlin: immutable `val` where possible, expression bodies for small functions, and trailing commas in multiline declarations. Use PascalCase for classes and composables (`NowPlayingScreen`), camelCase for functions and properties (`refreshPlaybackProgress`), and UPPER_SNAKE_CASE only for constants.

Keep UI state in `LsMusicViewModel`; keep DLNA protocol details in `DlnaController` and ListenBrainz protocol details in `ListenBrainzClient`. Preserve server-provided DIDL metadata when changing renderer compatibility or MusicBrainz extraction code.

Album sorting is a media-library interaction, not a settings preference: keep its quick selector beside the album browser. Treat a browse request with null `SortCriteria` as “server default” and preserve the returned container order—do not add implicit folder sorting in `DlnaController`. Read album year and album artist from DIDL container metadata without discarding the original values. Client-side title order is numbers/symbols, English, Han characters using Simplified Chinese pinyin collation, then other scripts. Keep albums with missing year or artist at the end in either direction.

Media-library navigation must preserve the instant-return behavior. Key cached pages by server and object ID, pin the active path, keep other pages in a bounded LRU, invalidate the cache when the server changes, and treat a loaded empty directory differently from a cache miss. A cache hit must not issue a blocking replacement browse request, and asynchronous results must be generation-checked so an older request cannot overwrite the current page. Save each directory's search query, grid/list mode, stable anchor item key, fallback index, and scroll offset. Restore these through the initial `LazyListState`/`LazyGridState`, before the first visible frame; do not restore by visibly scrolling from the top after composition.

Keep album browsing performant for libraries with hundreds of entries. Isolate frequently changing playback state from library state, provide stable lazy-item keys and `contentType`, and avoid per-card subcomposition or constraint measurement. Artwork requests should use the shared Coil loader, decode near the rendered size, use bounded direction-aware prefetch, and cancel work that moves outside the prefetch window. Preserve the album container artwork URI when opening an album. Use a stable thumbnail memory-cache key as the album-detail placeholder, and key painters by media identity and URI so recycled content can never show another album's cover. Do not preload an entire large library or replace a missing cached thumbnail with stale artwork.

Never log, hard-code, or include ListenBrainz user tokens in fixtures. Tokens must remain in the dedicated `ls_music_secrets` preferences file, which is excluded from cloud backup and device transfer. Validate a non-empty token with `/1/validate-token` before replacing the saved value; validation failures must preserve the previous token and distinguish invalid credentials from connectivity failures.

Playback-order behavior must stay consistent for local Media3 and remote DLNA playback. “Play all” starts with the first playable track. Do not treat player initialization, stale transport state, or an unconfirmed repeat callback as a completed track: automatic advancement is allowed only after the current track has actually entered playback (and, for DLNA, shown progress). Repeat cycles through off, repeat-one, and repeat-all. Shuffle must not repeat a randomly selected track until every queued track has played, must admit tracks added during the current shuffle cycle, and must reset its played history whenever shuffle is toggled. Album/playlist “shuffle play” only randomizes the replacement queue and remains independent from the now-playing shuffle toggle. Use distinct glyphs—not color alone—to distinguish sequential/shuffle and all repeat modes.

Playback reporting must behave consistently for local Media3 and remote DLNA playback. Send `playing_now` once per playback generation, count only time actually spent in the playing state, and submit a permanent listen only after playback ends when either the configured duration or percentage threshold is met. Run `:app:lintDebug` before handing off changes.

## Documentation Guidelines

Write `README.md` for application users. Focus on user-visible features, prerequisites, setup, usage steps, expected behavior, and limitations that affect normal use. Describe outcomes in user language—for example, returning to the previous browse position—not internal mechanisms such as cache algorithms, request generations, prefetch-window sizes, image decoding parameters, recomposition boundaries, or other performance implementation details. Keep architecture constraints and optimization rationale in `AGENTS.md`, code documentation, tests, or pull-request notes instead. Retain a short development section only for the commands and artifact information contributors need to get started.

## Testing Guidelines

Use JUnit4 for deterministic logic tests. Name tests as behavior statements, for example `parseTimeMs_supportsDlnaFractionalDuration`. Add unit tests for parsing, queue behavior (including initial play-all selection, repeat boundaries, shuffle exhaustion, shuffle reset, and tracks added during shuffle), album ordering (including both year directions, missing metadata, multilingual title groups, and pinyin), browse-cache hits and empty directories, stale-request rejection, LRU eviction, directional prefetch ranges, playback/reporting state transitions, threshold evaluation, MusicBrainz metadata extraction, and token-validation error classification. Unit tests must not call the live ListenBrainz service. Reserve `androidTest` for Android framework or UI integration. No coverage threshold is configured—cover changed behavior proportionately. Validate media-library scrolling and artwork behavior on a physical device with a library containing hundreds of albums; include cold-cache, warm-cache, fast-fling, album-entry, and back-navigation cases, preferably with an optimized release build.

## Commit & Pull Request Guidelines

The current history only contains `Initial commit`, so no established commit format exists. Use concise imperative subjects, preferably scoped, such as `dlna: support AK Connect SOAP playback`. Keep commits focused.

Pull requests should explain user-visible behavior, identify affected playback paths (local or remote), list verification commands, and include screenshots for UI changes. For media-library sorting changes, include a narrow-phone screenshot and describe server-order preservation plus missing-metadata behavior. For browse or artwork performance changes, describe cache scope and invalidation, first-frame scroll restoration, cold- and warm-cache behavior, and results from a physical-device library with hundreds of albums. For DLNA fixes, record the tested server/renderer family and any compatibility fallback. For ListenBrainz changes, describe `playing_now` and permanent-listen behavior, threshold semantics, metadata fallback, token validation, and whether a test account was used.
