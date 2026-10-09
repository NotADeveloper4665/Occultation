# Occultation

**The Eclipse Android client**, based on [Artemide](https://github.com/derflacco/moonlight-android).

Occultation brings Eclipse's Syzygy integration and streaming controls to Android
while retaining Artemide's mobile decoder work and Moonlight compatibility.

This is the **project foundation**, version `0.1.0-dev`. Eclipse feature ports
are planned; Syzygy passkey pairing and recording are not wired into the app yet.

- [Feature port and validation plan](docs/FEATURE-PORT.md)
- [Pinned upstream baseline](docs/ARTEMIDE-UPSTREAM.md)
- [Original Artemide README](docs/ARTEMIDE-README.md)

## Development builds

In GitHub Actions, run **Occultation Android**. Download the APK artifacts from
the successful run. The universal development APK supports ARMv7, ARM64, x86
and x86-64 and installs alongside Artemide, Artemis and Moonlight.

For local development, use Java 17, Android SDK 36 and NDK `27.0.12077973`:

```sh
git submodule update --init --recursive
./gradlew :app:testNonRoot_gameDebugUnitTest :app:lintNonRoot_gameDebug :app:assembleNonRoot_gameDebug
```

Development builds use debug signing. Production APKs require a stable private
signing key; a debug artifact must not be presented as a production release.

## Credits and license

Based on Artemide by DerFlacco, Artemis by ClassicOldSong, and Moonlight Android.
Existing GPL license and upstream copyright notices remain in effect.
