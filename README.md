<p align="center">
  <img src="docs/icon.svg" width="120" alt="larp.fm">
</p>

<h1 align="center">larp.fm</h1>

<p align="center">
  a pink little Last.fm scrobbler for Android
</p>

<p align="center">
  <a href="https://github.com/teamomuito/larp.fm/actions/workflows/android.yml"><img src="https://github.com/teamomuito/larp.fm/actions/workflows/android.yml/badge.svg" alt="build"></a>
  <img src="https://img.shields.io/badge/android-8.0%2B-ff4fa3" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/made%20with-kotlin-c2185b" alt="made with Kotlin">
</p>

<br>

larp.fm picks up whatever's playing on your phone (Spotify, YouTube Music, Poweramp, pretty much
any app with a media notification) and scrobbles it to [Last.fm](https://www.last.fm). No
internet? Plays get saved and sent later.

And yes, it larps. Tap LARP on a scrobble and it counts again.

## features

- scrobble after anywhere from 1% to 100% of a song (Last.fm's default is 50%)
- send the album, or just artist + track
- clean up album titles: `Iron Maiden (Remaster) [Special]` becomes `Iron Maiden`
- LARP any recent play up to 5 times
- auto-LARP every song if you're feeling bold
- choose which apps get scrobbled
- pink, obviously

## install

1. Open the [latest build](https://github.com/teamomuito/larp.fm/actions/workflows/android.yml),
   download `larpfm-apk` and unzip it.
2. Install the APK. Your phone will ask you to allow installs from your browser or files app.
3. Tap **Sign in with Last.fm**, hit Allow on the Last.fm page, and you're back in the app. If
   the build doesn't have an API key baked in, it'll ask for one first.
   [Grab one here](https://www.last.fm/api/account/create). It takes a minute, and the name and
   description can be anything.
4. Give it notification access when it asks. That's the only way Android lets an app see what's
   playing. larp.fm doesn't read your notifications.

> [!TIP]
> On Android 13+, if the notification access switch is greyed out, go to
> **App info → ⋮ → Allow restricted settings** and try again. Android does this to every app
> that doesn't come from the Play Store.

Updating? Uninstall the old version first, or Android won't install the new one over it.

## about LARP

Every LARP copy gets its own timestamp, spaced one song apart, like you had it on repeat. That
keeps Last.fm from throwing them out as duplicates. You can't LARP plays older than about two
weeks, because Last.fm ignores anything that old.

Last.fm isn't a fan of fake scrobbles, so larp responsibly.

<details>
<summary><b>building it yourself</b></summary>
<br>

You'll need JDK 17 and the Android SDK.

```sh
./gradlew test assembleRelease
```

The APK ends up in `app/build/outputs/apk/release/`. GitHub Actions builds every push, and
pushing a tag like `v1.0` publishes a release.

These repo secrets are optional:

| secret | what it's for |
| --- | --- |
| `LASTFM_API_KEY`, `LASTFM_API_SECRET` | bakes an API key into the app so nobody has to paste one |
| `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` | signs every build with the same key, so updates install over the old version |

To make a keystore:

```sh
keytool -genkeypair -v -keystore release.keystore -alias larpfm -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.keystore
```

The code is split in two:

- `core/` is plain Kotlin with unit tests. It has the scrobble rules, the LARP logic, the
  album title cleanup and the Last.fm client.
- `app/` is the Android app. It uses Jetpack Compose for the UI, a notification listener that
  follows media sessions, and WorkManager to send scrobbles.

</details>
