# Occultation

**The Eclipse Android client**, based on [Artemide](https://github.com/derflacco/moonlight-android).

Occultation brings Eclipse's Syzygy integration and streaming controls to Android
while retaining Artemide's mobile decoder work and Moonlight compatibility.

Version `0.1.3-dev` includes Syzygy connection-key pairing, input permissions and direct Desktop connection.

In **Add Computer**, enter the host address and optionally paste Syzygy's
six-word pairing phrase. Leave the key blank for normal PIN pairing.
For an automatically discovered or previously added host, tap an unpaired host and
choose **Pair with Syzygy phrase**, or select that action from its long-press menu.
Paste the key from `syzygy -s -auto -headless -psk` and press **Proceed**.
The key alone is not a host address; the host must be reachable through its IP,
hostname, or discovery.

Keys are not saved; successfully paired hosts reconnect using their pinned certificate.

Under **Settings → Input permissions**, choose view-only mode or allow keyboard,
mouse/touch/pen and controller input separately. These settings are included in
profiles and take effect when reconnecting. They restrict this client's input;
they do not change permissions granted by the host.

Under **Settings → Host connection**, enable **Connect straight to Desktop** to
start the advertised Desktop app when tapping a paired computer. If Desktop is
missing, ambiguous, or another app is running, the normal app list remains available.
The option is saved with profiles.

Recording, steady-delay buffering, adaptive bitrate and upscaling ports remain planned.

- [Feature port and validation plan](docs/FEATURE-PORT.md)
- [Pinned upstream baseline](docs/ARTEMIDE-UPSTREAM.md)
- [Original Artemide README](docs/ARTEMIDE-README.md)

## Development builds

Download the [universal development APK](https://github.com/NotADeveloper4665/Occultation/releases/download/dev/Occultation-universal.apk)
from the [development release](https://github.com/NotADeveloper4665/Occultation/releases/tag/dev).
Successful builds on the main development branch automatically update these APKs.
In GitHub Actions, **Occultation Android** also uploads the APKs as artifacts.
The universal development APK supports ARMv7, ARM64, x86
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

When Syzygy enables `-tails` or its Network auto-authorization setting and verifies
this connection belongs to its own tailnet, adding or tapping the host pairs
without a phrase or PIN. This requires connecting through the host's Tailscale
address, with the Tailscale VPN active on Android 6 or newer. Older Android
versions use phrase pairing. Full permissions are granted by the host; client input restrictions
still apply. Shared external nodes are excluded. Phrase pairing remains available
for other connections; the old 48-character input is no longer accepted.
