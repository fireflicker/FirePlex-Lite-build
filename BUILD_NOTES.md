[BUILD_NOTES.md](https://github.com/user-attachments/files/29152638/BUILD_NOTES.md)
# FirePlex Build Notes

These notes apply to FirePlex `1.4-server-efficiency` (`versionCode 8`).

## Build Requirements

- JDK 17
- Android SDK Platform 35
- Android Build Tools 35.0.0
- Gradle 8.11.1
- Android Gradle Plugin 8.8.0
- Kotlin 2.1.0

The app compiles with SDK 35, targets SDK 34, and supports devices from API 26.

## GitHub Actions Build

1. Upload the complete project to the repository root.
2. Confirm these files are at the repository root:
   - `settings.gradle.kts`
   - `build.gradle.kts`
   - `gradle.properties`
   - `app/build.gradle.kts`
   - `.github/workflows/blank.yml`
3. Open the repository's **Actions** tab.
4. Select **Build APK**.
5. Choose **Run workflow**.
6. Download the `FirePlex-Lite-debug-apks` artifact after the build succeeds.

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The workflow creates one universal APK. ABI split APKs are not enabled.

## Local Build

From the project root, run:

```powershell
gradle :app:assembleDebug --no-daemon --stacktrace
```

If a Gradle wrapper is added later, prefer:

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --stacktrace
```

## Application Configuration

Important values are in `app/build.gradle.kts`:

```kotlin
applicationId = "com.fireflicker.fireplex2"
minSdk = 26
targetSdk = 34
versionCode = 8
versionName = "1.4-server-efficiency"
```

Increase `versionCode` for every distributed update. Change `versionName` to the user-facing release number.

## Authentication Configuration

`AppAuthRepository.kt` performs the second-stage player-name validation against the FirePlex web-panel API. The API URL may be public, but its master key must not be committed.

For a public repository:

1. Rotate every key that has previously appeared in source control.
2. Store build values in GitHub Actions Secrets.
3. Inject values during the build instead of writing them directly in Kotlin files.
4. Remove old secrets from Git history.
5. Prefer a server-issued short-lived device token over a permanent key compiled into the APK.

Suggested GitHub secret names:

```text
FIREPLEX_API_KEY
OPENSUBTITLES_API_KEY
OPENSUBTITLES_USERNAME
OPENSUBTITLES_PASSWORD
```

Do not add real credential values to this document.

## Plex Connection

The app uses Plex's PIN linking flow and discovers connections made available to the linked account. It does not require a hard-coded home IP address. Remote playback depends on the Plex server being remotely reachable and correctly advertised by Plex.

Stored app data includes the Plex token, selected server, player name, visible categories, playback settings, favourites, and cached media lists. Signing out or clearing application data removes the local session.

## Playback

- Embedded player: AndroidX Media3/EXO 1.8.0
- Modes: Auto, Direct Play, Direct Stream, and Plex Transcode
- Transcode fallback: H.264 video with compatible audio through Plex HLS
- Supported subtitle sources: Plex tracks and OpenSubtitles
- Player state is reported back to Plex while playback is active
- Playback pauses when the application moves to the background

The final codec support still depends on the Android TV device's hardware decoders. Plex Transcode is the compatibility fallback for unsupported video or audio formats.

## Content Cache

The application stores library and recent-content data through Android DataStore. Cached content allows the lobby and home screen to appear before a full server refresh. Use **Update Contents** to refresh saved media and **Clear Cache** to remove it.

Artwork is loaded with Coil and cached as it becomes visible. The app does not download every poster during startup.

## Android TV Resources

- Launcher icon: `app/src/main/res/mipmap-*/ic_launcher.png`
- Round icon: `app/src/main/res/mipmap-*/ic_launcher_round.png`
- TV banner: `app/src/main/res/drawable-nodpi/app_banner.png`
- App background: `app/src/main/res/drawable/fireplex_bg.png`

Android resource filenames must contain only lowercase letters, numbers, and underscores. Images must be placed in `drawable`, `drawable-nodpi`, or a `mipmap-*` directory, not `res/values`.

## Troubleshooting

### Repository does not contain a Gradle build

The workflow is running from the wrong directory, or `settings.gradle.kts` was uploaded inside `app` instead of the repository root.

### Kotlin compilation error

Read the first line beginning with `e:` in the build output. The final `Compilation error` message is only a summary.

### Resource merger error

Check the filename and directory. A PNG placed in `res/values` fails because that directory accepts XML value resources only.

### Plex links but no server is reachable

Confirm Plex Remote Access works, the account has access to the server, and the server advertises a reachable secure connection.

### Video has no sound or fails to start

Try Auto or Plex Transcode. The device may not support the source audio/video codec through Direct Play.

### GitHub reports a Node deprecation warning

This warning comes from a GitHub Action runtime and is not normally the Android build failure. Look farther down the log for the first Gradle or Kotlin error.

## Release Checklist

1. Increase `versionCode` and update `versionName`.
2. Confirm no credentials or Plex tokens are committed.
3. Build the APK through GitHub Actions.
4. Install over the previous version and verify saved-login migration.
5. Test PIN linking, account validation, cached startup, VOD, Series, seasons, episodes, subtitles, D-pad navigation, and Back handling.
6. Test Direct Play and Plex Transcode on at least one Google TV or Fire TV device.
7. Publish a signed release APK rather than the debug APK for production distribution.
