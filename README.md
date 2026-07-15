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

## Android TV support

TV runs as a fully separate composition layer, not a retrofit of the phone screens.
`PlayerDeviceRouting.isTelevision()` branches at every route in `MainActivity`'s
`NavHost`; the phone screen and its `Tv*` counterpart share the same ViewModel/data
layer but never share Compose UI code.

| Surface | Phone | TV |
|---|---|---|
| Root nav | Bottom `NavigationBar` | None — pinned D-pad rows (Settings / All Videos / Playlists) on `TvFolderBrowserScreen`, the TV root/home surface |
| Folder browsing | `FolderListScreen` | `TvFolderBrowserScreen` |
| Video list (All Videos + folder/playlist contents) | `VideoListScreen` (sort toolbar, long-press multi-select) | `TvVideoListScreen` (plain D-pad row list; no sort chrome or multi-select — no long-press concept on a D-pad) |
| Playlists | `PlaylistsScreen` | `TvPlaylistsScreen` (create/rename/delete via always-visible focusable buttons per row, not long-press/overflow) |
| Player | Touch controls | `TvPlayerScreen` + `TvPlayerControls` — D-pad seek/subtitle-offset, media-key and Back-key handling (see table below) |

**TV remote key map** (`TvPlayerControls.kt`):

| Key | Behavior |
|---|---|
| D-pad Left / Right | Seek ∓10s (hold repeats) |
| D-pad Up / Down | Subtitle offset ±100ms |
| OK / Center | Activates focused button |
| Media Play/Pause/Play/Pause | Toggle / resume / pause |
| Media Next / Previous | Skip track |
| Media Rewind / Fast Forward | Seek ∓10s, same step as D-pad |
| Back | Exit player |
| Volume Up/Down/Mute | System default (unhandled by app) |

**Known gap, flagged not silently assumed:** playlist create/rename on TV uses a
standard `AlertDialog` + `TextField`, relying on the default Android on-screen
keyboard for D-pad text entry. This has not been manually verified on real TV
hardware/emulator. Same caveat applies to media-key behavior above — confirm on
an actual remote before shipping.

See `PHASE_1_2_3_SUMMARY.md` for the full change log across all three build phases.



- 60 fps p95 scroll on a 1,000-item folder
- Folder index visible before Phase-2 metadata extraction completes
- Zero-data-loss SQLite migrations (PlaybackPositions / SubtitleOffsets never dropped)
- Interface-consistency check (every contract has an implementation)
