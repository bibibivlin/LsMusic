# L's Music

[简体中文](README.md)

[![Android CI](https://github.com/bibibivlin/LsMusic/actions/workflows/android-ci.yml/badge.svg)](https://github.com/bibibivlin/LsMusic/actions/workflows/android-ci.yml)
[![GitHub Release](https://img.shields.io/github/v/release/bibibivlin/LsMusic)](https://github.com/bibibivlin/LsMusic/releases/latest)
[![MIT License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Android 12+](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)

L's Music is an Android DLNA / UPnP music controller. Browse music libraries on your home network and play music directly on DLNA players, or use this device as the player.

## Features

- Automatically discovers media servers and players on the local network.
- Browse, search, and sort folders, albums, and playlists.
- Restore the previous search, layout, and browse position when returning to the library.
- Play music on a remote DLNA device or on this device.
- Manage the playback queue with shuffle, repeat, track changes, and seeking.
- Supports Android notifications, the lock screen, and Bluetooth media controls.
- Optional online lyrics with line-by-line, word-by-word, and bilingual display.
- Optional ListenBrainz reporting, with offline listens retained and retried when the network returns.
- Supports phones, tablets, and foldables, plus dynamic colors and preset themes.

## Download and installation

1. Open [GitHub Releases](https://github.com/bibibivlin/LsMusic/releases).
2. Download an APK such as `LsMusic-v1.0.0.apk`.
3. Follow Android's prompt to allow installation from the current unknown-source app, then finish the installation.

Android 12 or later is required. The `SHA256SUMS.txt` file in a release can be used to verify the download.

## Getting started

1. Connect your phone, media server, and DLNA player to the same Wi-Fi network.
2. Open L's Music and grant the required local-network and notification permissions.
3. In Settings, scan for and select a music library and a player.
4. Browse the library and tap a song, album, or playlist to start playback.

If no remote player is available, choose this device as the player.

The Settings home page keeps the complete media-library and player selection area. Appearance, Lyrics, and Network open as separate pages with slide and fade transitions. About includes the app version, project home page, privacy information, and open-source notices.

## Exiting the app

Tap Exit at the bottom of the Settings home page. The app stops local playback and playback on the currently controlled DLNA device, then removes its media notification and system media-control connection. If the remote device disconnects, the app warns that it could not confirm the stop before exiting.

When ListenBrainz is enabled with a valid token, exiting clears the app's now-playing state and saves playback that meets the upload threshold as a permanent listen. The app waits up to five seconds for an upload attempt; failed listens are retained and retried later. If saving to local storage fails, playback still stops and the page remains available so you can retry saving before exiting.

## Online lyrics

Online lyrics are off by default. Enable them in Settings > Lyrics, then tap the album artwork on the Now playing page.

The app checks NetEase Cloud Music and QQ Music in the order configured by the user. Options include bilingual display, source labels, font size, and visual effects. If no match is found or the network fails, you can retry later.

Lyrics use public third-party interfaces, so changes on those services may temporarily make lyrics unavailable.

## ListenBrainz

ListenBrainz reporting is off by default. Before using it:

1. Copy a user token from your ListenBrainz account settings.
2. Validate and save it in L's Music under Settings > Network.
3. Enable reporting and, if needed, adjust the minimum playback duration or percentage.

Only playback that meets the configured threshold becomes a permanent listen. Unuploaded listens are stored on the device when offline and can be retried or deleted under Settings > Network > Pending uploads. Tokens are never included in project code, logs, or backups.

## Limitations

- DLNA discovery depends on local-network multicast; guest networks, AP isolation, and some VPNs may prevent devices from discovering one another.
- Emulators usually cannot discover DLNA devices reliably; a physical device is recommended.
- Media metadata, album grouping, and artwork quality depend on the media server.
- Online lyrics do not read lyric files or lyrics embedded in audio files.
- Initial loading of a large library or high-resolution artwork depends on the server and Wi-Fi conditions.

## Privacy

L's Music contains no ads, profiling, or telemetry SDK. Online lyrics and ListenBrainz are opt-in. See the [privacy notice](PRIVACY.md) for details.

## Development

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Without release-signing environment variables, the local release APK is unsigned by default. Validate DLNA behavior and optimized release builds on a physical device and a non-isolated Wi-Fi network.

## Project information

- [MIT License](LICENSE)
- [Contributing](CONTRIBUTING.md)
- [Security reports](SECURITY.md)
- [Changelog](CHANGELOG.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)
