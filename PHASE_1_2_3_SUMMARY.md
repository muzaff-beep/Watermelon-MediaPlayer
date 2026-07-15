# TV Support — Phases 1, 2 & 3 — Complete Summary

All three phases from the original plan are now complete. This supersedes
`PHASE_1_2_SUMMARY.md` (Phase 1/2) and `PHASE_3_SUMMARY.md` (Phase 3) with one
combined record.

## Phase 1 — Navigation shell

Decision taken: root-level pinned rows extending `TvFolderBrowserScreen`'s existing
pattern (no side rail); All Videos as a pinned row on that same screen, not nested
inside Folders.

1. **`app/.../MainActivity.kt` — `shouldShowBottomBar`**
   Returns `false` unconditionally on TV (`PlayerDeviceRouting.isTelevision`). TV has
   no bottom bar at all; `TvFolderBrowserScreen` is the TV root/home surface.

2. **`ui-presentation/.../tv/TvFolderBrowserScreen.kt`**
   Added two new pinned rows — "All Videos" and "Playlists" — directly below the
   existing pinned "Settings" row, using the same inline `Surface` + focus-ring
   pattern already established there. New params: `onAllVideosClick`, `onPlaylistsClick`.
   Existing folder/playlist rows below them are unchanged.

## Phase 2 — Missing TV screens

3. **`ui-presentation/.../tv/TvPlaylistsScreen.kt`** (new)
   Create/rename/delete, built on the existing headless `PlaylistViewModel` — no new
   view-model logic. Row-list-with-focus-ring, matching `TvFolderBrowserScreen`'s
   visual language. User-playlist rows carry two extra always-visible focusable
   buttons (Rename/Delete) instead of a long-press/overflow menu, since D-pad has no
   long-press. Create/rename dialogs are standard `AlertDialog` + `TextField`
   (default Android on-screen keyboard) — **not yet manually verified on real TV
   hardware/emulator**.

4. **`ui-presentation/.../tv/TvVideoListScreen.kt`** (new)
   Serves both All Videos and any folder/playlist's contents. Plain D-pad row list,
   no sort/layout toolbar, no multi-select (no long-press concept on a D-pad) —
   video name + duration only.

5. **`app/.../MainActivity.kt` — route wiring**
   - `Routes.FOLDERS`: `TvFolderBrowserScreen` call updated with the two new
     callbacks, navigating to `Routes.ALL_VIDEOS` / `Routes.PLAYLISTS`.
   - `Routes.ALL_VIDEOS`: branches to `TvVideoListScreen` on TV.
   - `Routes.PLAYLISTS`: branches to `TvPlaylistsScreen` on TV.
   - `videos/{folderPath}?isPlaylist={isPlaylist}` (shared folder/playlist contents
     route): branches to `TvVideoListScreen` on TV.
   - `Routes.FAVORITES` needed no TV branch — it's a redirect-only composable, and
     Favourites already appears as a system-playlist row inside `TvPlaylistsScreen`
     via `PlaylistRepository.observeAll()`, same as on phone.

## Phase 3 — Player completeness

Decision taken: media keys and Back wired to the same actions as their D-pad/focus
equivalents; Rewind/Fast-Forward reuse the same 10s step and hold-to-repeat as D-pad
Left/Right; volume keys left to Android system default.

6. **`app/.../MainActivity.kt`**
   - `TvPlayerScreen(...)` call now passes `subtitleStyle = settingsState.subtitleStyle`
     — TV player renders subtitles with the same settings-backed style as the phone
     player, instead of the previous hardcoded default.
   - Passes `onExit = { navController.popBackStack() }` for Back-key handling.

7. **`ui-presentation/.../tv/TvPlayerScreen.kt`**
   Added `onExit: () -> Unit` parameter, forwarded to `TvPlayerControls`.

8. **`ui-presentation/.../tv/TvPlayerControls.kt`**
   Extended `onKeyEvent` to cover media keys and Back, alongside the pre-existing
   D-pad handling.

### Full remote-control key map (final)

| Key | Behavior | Added in |
|---|---|---|
| D-pad Left | Seek −10s (hold repeats) | Pre-existing |
| D-pad Right | Seek +10s (hold repeats) | Pre-existing |
| D-pad Up | Subtitle offset +100ms | Pre-existing |
| D-pad Down | Subtitle offset −100ms | Pre-existing |
| OK / Center | Activates focused button (Play/Pause, Previous, Next) | Pre-existing |
| Media Play/Pause | Toggle playback | Phase 3 |
| Media Play | Resume | Phase 3 |
| Media Pause | Pause | Phase 3 |
| Media Next | Skip to next track (no-op if none) | Phase 3 |
| Media Previous | Skip to previous track (no-op if none) | Phase 3 |
| Media Rewind | Seek −10s (same step as D-pad Left) | Phase 3 |
| Media Fast Forward | Seek +10s (same step as D-pad Right) | Phase 3 |
| Back | Exit player (pop nav back stack) | Phase 3 |
| Volume Up / Down / Mute | System default | Untouched, by design |
| Number keys 0–9 | No action | Out of scope |
| Menu / Guide | No action | Out of scope |

## Not changed

- `TvVideoListScreen` / `TvPlaylistsScreen` have no visible Back button —
  unnecessary; the system Back key already pops the `NavHost` back stack by default
  (nothing in this codebase intercepts it outside the player screen).
- Subtitle live-offset nudge (D-pad Up/Down during playback) remains render-time-only,
  matching current phone-screen behavior — persisting it is a separate cross-platform
  ticket, not part of this scope.

## Verification still needed (manual, on real hardware — flagged, not assumed)

- On-screen-keyboard D-pad text entry for playlist create/rename.
- General D-pad focus traversal across the new pinned rows and playlist action
  buttons (3 focus stops per user-playlist row: open / rename / delete).
- Media-key behavior (Play/Pause/Next/Previous/Rewind/FF) on actual remote hardware
  — not all Android TV remotes send these; some send D-pad + OK only.
- Hardware volume-key behavior on a real TV remote (expected: system default,
  unaffected by app code).
