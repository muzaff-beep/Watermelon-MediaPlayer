
![Kotlin](https://img.shields.io/badge/kotlin-2.0.0-purple)
![Status](https://img.shields.io/badge/status-active-brightgreen)
![License](https://img.shields.io/badge/license-MIT-blue)
![Build](https://img.shields.io/github/actions/workflow/status/muzaff-beep/Watermelon-MediaPlayer/build-judge.yml)
![Android SDK](https://img.shields.io/badge/minSdk-23-brightgreen)
![Target SDK](https://img.shields.io/badge/targetSdk-35-blue)
![Status](https://img.shields.io/badge/status-active-brightgreen)
![Build](https://img.shields.io/github/actions/workflow/status/muzaff-beep/Watermelon-MediaPlayer/build-judge.yml)·
![Status](https://img.shields.io/badge/status-active-brightgreen)
![Migrations](https://img.shields.io/badge/migrations-zero--data--loss-blue)
![FPS](https://img.shields.io/badge/scroll_pps-60-brightgreen)

# Watermelon MediaPlayer

Offline-first, privacy-respecting local video player for Android (mobile + TV), engineered
RTL-native for Persian/Arabic with a signature **VHS Visual Seeker**. Zero telemetry, minimum
permission footprint, folder-centric navigation. 

- **minSdk 23** (Android 6.0) · **targetSdk 35** (Android 15) · Mobile + Android TV
- Offline-first · Folder-centric · RTL-native · Privacy by default

## Architecture

Strict unidirectional MVI. Every layer talks through plain Kotlin interfaces defined in
`common-interfaces` — the single source of truth. Implementation modules depend **only** on
`common-interfaces`; `app` is the only module that wires concrete implementations together.

```
                 app  (shell: MainActivity, NavHost, DI wiring)
        ┌──────────┼───────────┬───────────────┐
   ui-presentation playback-engine library-storage subtitle-engine
        └──────────┴───────────┴──── common-interfaces ────┘
                                          ▲
                                     benchmarks
```

| Module | Responsibility |
|--------|----------------|
| `common-interfaces` | Pure-Kotlin contracts: repositories, `PlaybackController`, models, `VhsTier`, `UserIntent`. |
| `playback-engine` | Media3/ExoPlayer service + MediaSession, `PlaybackControllerImpl`, sleep timer, VHS renderers (Tier A/B AGSL, Tier C PNG). |
| `library-storage` | Two-phase MediaStore indexer, hand-written `SQLiteOpenHelper` + idempotent migration ladder, repository impls. |
| `subtitle-engine` | OpenSubtitles hasher, Ktor client with mirror rotation, Linear-Drift sync. |
| `ui-presentation` | Compose screens, components, ViewModels, TV layouts, RTL theme. |
| `app` | Single `MainActivity`, NavHost, manifest, string resources (en/ar/fa). |
| `benchmarks` | Macrobenchmarks (scroll, startup). Migration-ladder gate lives in `library-storage`'s androidTest — see Quality gates below. |

## Build

```bash
./gradlew build            # all modules + unit tests
./gradlew :app:assembleDebug
python3 scripts/verify_interfaces.py   # every interface has an *Impl
```

> **Note on environments without Google Maven access:** the Android modules require the
> Android Gradle Plugin and AndroidX/Compose/Media3 artifacts from `dl.google.com`. The pure
> `common-interfaces` module builds anywhere (Maven Central only). CI (`.github/workflows/
> build-judge.yml`) performs the full Android build, unit tests, benchmarks, and the
> migration-ladder gate.

## Quality gates (CI)

- 60 fps p95 scroll on a 1,000-item folder
- Folder index visible before Phase-2 metadata extraction completes
- Zero-data-loss SQLite migrations (PlaybackPositions / SubtitleOffsets never dropped)
- Interface-consistency check (every contract has an implementation)
