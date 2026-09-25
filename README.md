# Scrobbler

An Android app that scrobbles what you play in any music app to [Last.fm](https://www.last.fm).

It watches the media sessions of the apps on your phone (Spotify, YouTube Music, Poweramp, and so on).
It sends each track to Last.fm as "now playing". Once the track has played for half its length or
4 minutes, whichever comes first, it scrobbles it. Tracks of 30 seconds or less don't count. Scrobbles
made while offline are queued and sent when you're back online.

## Install

1. Open the [Actions tab](../../actions/workflows/android.yml), pick the latest successful run and
   download the `scrobbler-apk` artifact. It's a zip containing the APK. If a release exists, you
   can instead download the APK from [Releases](../../releases).
2. Install the APK on your phone. You'll need to allow installs from your browser or file manager.
3. Open Scrobbler and sign in with your Last.fm username and password. Unless the APK was built
   with an API key (see below), you'll also need a free
   [Last.fm API account](https://www.last.fm/api/account/create). Paste its **API key** and
   **shared secret** into the sign-in screen. The application name and description can be
   anything.
4. Tap **Open settings** and turn on notification access for Scrobbler. Android only shows other
   apps' media sessions to apps with notification access. Scrobbler doesn't read or store your
   notifications.

   On Android 13 and later, sideloaded apps can't be given notification access straight away. If
   the switch is greyed out or you see "Restricted setting": open **App info** for Scrobbler, tap
   the **⋮** menu, choose **Allow restricted settings**, then try again.

Apps show up in the **Apps** list the first time they play something. You can switch each one off
there. YouTube, Chrome, Firefox and Netflix start switched off because they mostly play things
that aren't music.

## Build

The Android SDK and JDK 17 are required.

```sh
./gradlew test assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

Every push is built by GitHub Actions. Pushing a tag such as `v1.0` also publishes the APK as a
GitHub release.

### Optional build secrets

Set these as repository secrets for CI. For local builds, set them as environment variables or
in `~/.gradle/gradle.properties`.

| Name | Purpose |
| --- | --- |
| `LASTFM_API_KEY`, `LASTFM_API_SECRET` | Built into the APK, so users don't have to create their own API account. |
| `SIGNING_KEYSTORE_BASE64` (CI) or `SIGNING_STORE_FILE` (local) | Release keystore. Without one, each CI build is signed with a different throwaway key. You then have to uninstall the old version before installing a new one, which signs you out. |
| `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` | Passwords and alias for that keystore. |

To create a keystore and get its base64 for the secret:

```sh
keytool -genkeypair -v -keystore release.keystore -alias scrobbler -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.keystore
```

## Project layout

- `core/`: plain Kotlin with no Android dependencies, unit-tested on the JVM. It holds the
  scrobbling rules and play-time tracking (`PlaybackTracker`), plus the Last.fm API client.
- `app/`: the Android app.
  - `ScrobbleListenerService` is a notification listener that follows every media session.
  - `FlushWorker` sends queued scrobbles with WorkManager.
  - The UI is built with Jetpack Compose.
