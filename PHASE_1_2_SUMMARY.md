# Phase 1 & 2 — Navigation Shell + Missing TV Screens — Summary

Decisions taken (per your earlier answers): root-level rows extending
`TvFolderBrowserScreen`'s existing pattern (no side rail), All Videos as a pinned row
on that same screen (not nested inside Folders), default on-screen keyboard for
playlist naming (flagged for manual verification, not a custom input flow).

## Phase 1 — Navigation shell

1. **`app/.../MainActivity.kt` — `shouldShowBottomBar`**
   Now returns `false` unconditionally on TV (`PlayerDeviceRouting.isTelevision`).
   TV has no bottom bar at all; `TvFolderBrowserScreen` is the TV root/home surface.

2. **`ui-presentation/.../tv/TvFolderBrowserScreen.kt`**
   Added two new pinned rows — "All Videos" and "Playlists" — directly below the
   existing pinned "Settings" row, using the exact same inline `Surface` + focus-ring
   pattern already established there. New params: `onAllVideosClick`, `onPlaylistsClick`.
   Existing folder/playlist rows below them are unchanged.

## Phase 2 — Missing TV screens

3. **`ui-presentation/.../tv/TvPlaylistsScreen.kt`** (new)
   Create/rename/delete, built on the existing headless `PlaylistViewModel` — no new
   view-model logic. Row-list-with-focus-ring, matching `TvFolderBrowserScreen`'s visual
   language. User-playlist rows carry two extra always-visible focusable buttons
   (Rename/Delete) instead of a long-press/overflow menu, since D-pad has no long-press.
   Create/rename dialogs are standard `AlertDialog` + `TextField` (default Android
   on-screen keyboard) — **not yet manually verified on real TV hardware/emulator**.

4. **`ui-presentation/.../tv/TvVideoListScreen.kt`** (new)
   Serves both All Videos and any folder/playlist's contents, same as the plan called
   for. Plain D-pad row list, no sort/layout toolbar, no multi-select (no long-press
   concept on a D-pad) — video name + duration only.

5. **`app/.../MainActivity.kt` — route wiring**
   - `Routes.FOLDERS`: `TvFolderBrowserScreen` call updated with the two new callbacks,
     navigating to `Routes.ALL_VIDEOS` / `Routes.PLAYLISTS`.
   - `Routes.ALL_VIDEOS`: branches to `TvVideoListScreen` on TV.
   - `Routes.PLAYLISTS`: branches to `TvPlaylistsScreen` on TV.
   - `videos/{folderPath}?isPlaylist={isPlaylist}` (shared folder/playlist contents
     route): branches to `TvVideoListScreen` on TV.
   - `Routes.FAVORITES` needed no TV branch — it's a redirect-only composable, and
     Favourites already appears as a system-playlist row inside `TvPlaylistsScreen`
     via `PlaylistRepository.observeAll()`, same as on phone.

## Not changed

- Phase 3 (player completeness) — left to you, per your instruction. The zip you
  uploaded (with `subtitleStyle`/`onExit` wiring) was used as the base for this work,
  untouched beyond what's listed above.
- `TvVideoListScreen` / `TvPlaylistsScreen` have no visible Back button — unnecessary;
  the system Back key already pops the `NavHost` back stack by default (nothing in this
  codebase intercepts it outside the player screen), so this was left as-is.

## Verification still needed (manual, on real hardware — flagged, not assumed)

- On-screen-keyboard D-pad text entry for playlist create/rename (open question you
  deferred to "verify only" rather than building a custom flow).
- General D-pad focus traversal across the new pinned rows and playlist action buttons
  (3 focus stops per user-playlist row: open / rename / delete).
