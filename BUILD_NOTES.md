# FirePlex3.0 build notes

## Main fix included
The GitHub Actions workflow now installs a known Gradle version before running the build.
The original project did not include `gradlew`, so calling `gradle assembleDebug` could fail if the runner did not already have Gradle installed.

## GitHub build
1. Upload/commit these files to GitHub.
2. Open the **Actions** tab.
3. Select **Build APK**.
4. Click **Run workflow**.
5. Download the **FirePlex-debug-apk** artifact when the build finishes.

## If the APK builds but app/login still fails
Check the response from:

```powershell
$headers = @{
    "Content-Type" = "application/json"
    "X-API-Key" = "YOUR_API_KEY"
}

$body = @{ username = "testuser" } | ConvertTo-Json

Invoke-RestMethod -Uri "https://plexpin.duckdns.org/api/app/login" -Method POST -Headers $headers -Body $body
```

## Security warning
The API key is currently hard-coded inside `AppAuthRepository.kt`. Anyone who gets the APK can extract it. For a public app, move this check server-side or issue per-device short-lived tokens.
# FirePlex 0.9 TV Lite

- EXO/Media3 is the only embedded player.
- VLC and MPV are optional external apps; their native libraries are not bundled.
- EXO failures retry through Plex Transcode and then show a clear error screen.
- Device profiles: Auto Detect, Fire Stick Lite, Google TV, Fast WiFi, Slow WiFi, and Mobile Data.
- Cached home content opens first and refreshes quietly in the background.
- Artwork remains lazy-loaded by Coil as cards become visible.
- Settings includes Refresh Account.
- Storage/media/microphone permissions are not requested.
- Playback UI is separated into `PlayerScreen.kt`; device presets live in `DeviceProfiles.kt`.
# FirePlex 1.0 Unified Stability Update

- One universal APK; ABI split outputs removed.
- One embedded player: Media3 EXO. Old VLC/MPV preferences are migrated to EXO.
- Fixed continuous player reload caused by changing playback position in a `LaunchedEffect` key.
- Debounced automatic EXO + Plex Transcode fallback.
- Removed false `stopped` timeline reports during player recreation.
- Playback timeline updates reduced from every second to every five seconds.
- Added lifecycle-aware pause/resume when the app backgrounds or returns.
- Removed forced stereo track filtering that could produce missing audio.
- Added Plex client/session identity to HLS transcode requests.
- Transcoding no longer requires a direct file `Part` key.
- Removed automatic parallel full-library cache scans during content updates.
- Limited background library preload to one library at a time.
- Corrected TV episode display titles.
- All screens use a compact density: 82% TV and 90% mobile.
- Detail artwork hero reduced to 250dp.
- Activity locked to landscape to avoid orientation recreation during playback.
- Version: `1.0-unified` (`versionCode 4`).
