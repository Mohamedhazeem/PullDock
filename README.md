# PullDock

**PullDock is a native Android app for managing authorized media downloads and BitTorrent transfers in one queue.**

It combines a clean Material 3 interface with a modular Kotlin architecture, durable download state, media extraction, torrent transfers, a library, and playback.

> PullDock is for content you own or have permission to download. Respect copyright, provider terms, and applicable law.

## Download

Grab the latest signed APK from **[GitHub Releases](https://github.com/Mohamedhazeem/PullDock/releases/latest)** — no Play Store needed.

| APK | Use it for |
| --- | --- |
| `pulldock-arm64-v8a-release.apk` | Most phones (recommended) |
| `pulldock-universal-release.apk` | Any device (larger download) |
| `pulldock-x86_64-release.apk` | Emulators / x86_64 devices |

**Install:** open the [latest release](https://github.com/Mohamedhazeem/PullDock/releases/latest) on your phone → download the `arm64` (or `universal`) APK → tap it → allow “install unknown apps” for your browser → open PullDock.

Requires **Android 8.0+ (API 26+)**.

## Features

- **One queue for media and torrents** — Home shows every transfer with progress, speed, ETA, pause/resume, retry, and removal.
- **Multiple input types** — Add an HTTP(S) media URL, magnet link, `.torrent` file, or share a link from another app.
- **Media extraction** — Resolve public media pages through yt-dlp, choose a format, and download. Sites yt-dlp can't extract fall back to a generic HTML sniffer (direct `.mp4`/`.m3u8`) and an on-device HLS downloader, with FFmpegKit remuxing to `.mp4`.
- **Video with audio** — Video-only streams are paired with the original-language audio track and merged on-device with Android `MediaMuxer`.
- **BitTorrent transfers** — libtorrent4j-backed engine with file selection and transfer limits.
- **Durable state** — Room stores tasks and recovery data; DataStore stores preferences. Downloads are designed to survive process death and device restarts.
- **Foreground execution** — A dataSync foreground service keeps active transfers alive while respecting Android background limits.
- **Library** — Search, filter, sort, play, and delete completed media. Missing files are marked unavailable instead of silently disappearing.
- **Playback** — AndroidX Media3 player with a dedicated player screen.
- **Settings** — Light/dark/system theme, download folder (defaults to a `PullDock` folder in app storage), network policy, consent, and legal information.
- **Accessible adaptive UI** — Material 3, warm-paper design tokens, large-font support, TalkBack semantics, RTL, and adaptive layouts.
- **CI** — Debug build, unit tests, Android lint, 64-bit ABI packaging check, and secret scanning.

## Tech stack

| Area | Technology |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Architecture | Clean Architecture: presentation → domain ← data, plus platform and native engines |
| DI | Hilt |
| Persistence | Room, DataStore |
| Async | Kotlin Coroutines, Flow |
| Playback | AndroidX Media3 |
| Media extraction | yt-dlp + hlsfetch (HLS fallback) via Chaquopy; FFmpegKit remux |
| Torrents | libtorrent4j 2.1.0-39 |
| Build | Gradle 9.6, Android Gradle Plugin 9.4, version catalog |
| Spec workflow | Spec Kit |

## Project layout

```text
app/                 # Application, navigation shell, manifest, Hilt root, execution layer
core/                # Shared models, dispatchers, design system, Room, DataStore, storage, network
domain/              # Pure Kotlin contracts, models, use cases, state machine
data/                # Repository implementations, queue coordinator, engine adapters
engines/             # Torrent, extractor, and media engine integration
feature/             # Onboarding, Home, Add, Queue, Download Detail, Library, Player, Settings, Legal
specs/               # Spec Kit feature specifications, plans, tasks, ADRs
docs/                # Security, limitations, extractor lifecycle, licenses
scripts/ci/          # ABI and secret-scan helpers
```

See [AGENTS.md](AGENTS.md) for the full module map and engineering rules, and [docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md) for current constraints.

## Requirements

- Android Studio with a recent Android SDK Platform
- JDK 17 or newer
- Android SDK Build Tools and Platform Tools
- A device or emulator running Android 8.0+ (API 26+)

Create a `local.properties` file if Android Studio does not create one:

```properties
sdk.dir=/path/to/Android/sdk
```

## Build and run

```bash
# Debug build
./gradlew :app:assembleDebug

# Install the arm64 debug APK on a connected device
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk

# Release build (signed when keystore.properties is present, otherwise unsigned)
./gradlew :app:assembleRelease
```

## Test and lint

```bash
# Unit tests
./gradlew :app:testDebugUnitTest

# Android lint
./gradlew :app:lintDebug

# Module tests
./gradlew :core:common:test :core:model:test
./gradlew :domain:download:test :domain:torrent:test :domain:extractor:test
./gradlew :data:download:testDebugUnitTest :data:torrent:testDebugUnitTest
./gradlew :data:extractor:testDebugUnitTest
```

Instrumentation tests require a connected device or emulator:

```bash
./gradlew :app:connectedDebugAndroidTest
```

## Architecture rules

- The domain layer is pure Kotlin: no Android, Room, Compose, or engine types.
- Features never access Room, native engines, or storage directly.
- State flows down; events flow up.
- Every schema change needs a tested migration.
- Native engines are isolated behind typed interfaces.
- No secrets, credentials, or signing keys are committed.
- All external input is untrusted: validate URLs, filenames, and paths.

## Current status

PullDock is an active open-source project. The app scaffold, media/torrent cores, library, player, and completion flow are implemented. Some device-verification and hardening work remains, including background-execution behavior and connected tests. See the issue tracker and `docs/KNOWN_LIMITATIONS.md` for details.

## Contributing

Contributions are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.

## Security

Please do not open a public issue for a security vulnerability. Use GitHub’s private vulnerability reporting for the repository, or contact the maintainer directly.

## License

Released under the [MIT License](LICENSE).

Third-party dependencies retain their own licenses. See [docs/THIRD_PARTY_LICENSES.md](docs/THIRD_PARTY_LICENSES.md) and the dependency assessment in `docs/`.

## Maintainer

Mohamed Hazeem — [@Mohamedhazeem](https://github.com/Mohamedhazeem)