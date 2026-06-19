[README.md](https://github.com/user-attachments/files/29152150/README.md)
# FirePlex

FirePlex is an unofficial Android TV media client for browsing and playing content from a personal Plex Media Server. It is designed for televisions and remote controls, with a compact media interface, cached library browsing, and one built-in Media3 player.

## Current Release

- Version name: `1.3-paged-room-cache`
- Version code: `7`
- Android package: `com.fireflicker.fireplex2`
- Minimum Android version: Android 8.0 (`API 26`)
- Target Android version: Android 14 (`API 34`)
- Build output: one universal debug APK

## Main Features

- Connects to Plex using the `plex.tv/link` PIN flow.
- Validates a player/friendly name through the FirePlex web-panel API.
- Remembers the Plex login and player name between launches.
- Rechecks the player account so expired or disabled accounts can be refused.
- Displays movie and TV libraries, posters, backdrops, metadata, seasons, and episodes.
- Includes Recently Added Movies, Recently Added TV, Continue Watching, favourites, and global search.
- Provides separate VOD and Series browsing with category navigation.
- Saves library data locally so the home screen can open from cache.
- Loads Plex libraries in 80-item pages and automatically requests the next page near the grid's end.
- Stores only the active full category in Room for bounded offline fallback.
- Refreshes content through the Update Contents screen.
- Lazily loads and caches artwork with Coil.
- Uses one built-in AndroidX Media3/EXO player.
- Supports Direct Play, Direct Stream, Plex Transcode, and automatic fallback.
- Supports HLS, DASH, RTSP, and common local/remote media containers supported by Media3.
- Displays Plex subtitle tracks and supports OpenSubtitles search and selection.
- Includes custom TV playback controls for play/pause, seeking, speed, subtitles, and restart.
- Uses D-pad focus navigation and staged Back-button handling for Android TV remotes.
- Includes device profiles for Fire TV, Google TV, fast Wi-Fi, slow Wi-Fi, and mobile data.
- Includes player settings, visible-category controls, account refresh, cache clearing, and speed test tools.
- Supplies Android TV launcher artwork, application icon, and banner.

## Project Structure

```text
app/src/main/java/
  MainActivity.kt                 Main navigation and application UI
  PlayerScreen.kt                 Media3 player and subtitle controls
  DeviceProfiles.kt               Playback presets for different devices
  data/
    AppAuthRepository.kt          Web-panel account validation
    OpenSubtitlesRepository.kt    OpenSubtitles integration
    PlayerPlaybackSettings.kt     Stored player preferences
    PlexRepository.kt             Plex API, login, caching, and playback URLs
    PlexXmlParser.kt              Plex XML response parsing
```

## Building

The repository contains a GitHub Actions workflow. Open **Actions**, run **Build APK**, and download the `FirePlex-Lite-debug-apks` artifact after the job succeeds.

For full setup and local build instructions, see [BUILD_NOTES.md](BUILD_NOTES.md).

## Security

Do not publish private API keys, OpenSubtitles credentials, Plex tokens, or customer information. Secrets committed to Git remain in repository history even after the visible file is changed.

Keys placed in `BuildConfig` or another build-time variable are hidden from source control but can still be extracted from the compiled APK. Master credentials should remain on a trusted server. The app should receive short-lived, limited tokens whenever possible.

Rotate any credential that has previously been committed before making the repository public.

## Permissions

FirePlex requests only network access and network-state access. It does not request microphone, photo, video, or general storage permissions.

## Disclaimer

FirePlex is an independent project and is not affiliated with or endorsed by Plex, Inc. A Plex account and access to a Plex Media Server are required. Users are responsible for accessing only media and services they are authorised to use.
