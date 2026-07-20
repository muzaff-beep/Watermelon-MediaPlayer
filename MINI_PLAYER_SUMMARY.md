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

---

## Addendum — Mini-Player (in-app persistent overlay)

Verified against the codebase first: items 1 (Videos tab routing) and 2 (folder sort
options) from the separate feature spec were already fully implemented before this
session — routing bug fixed, tracked-folder data source, list-view parent-folder line,
and the 4-option Name/Size/Modified/Video-Count sort with correct "max video
`dateModified`" semantics were all present. Item 3, the mini-player, did not exist.

Per your answers: progress bar is read-only/display-only, bar is a compact ~64dp strip
with slide+fade animation.

**New file:**
- `ui-presentation/.../components/MiniPlayerBar.kt` — presentational only. Live video
  surface (reuses the same `PlayerView`/TextureView layout as the full player),
  title, Play/Pause, Next, Previous, Mute, Close, and a thin read-only
  `LinearProgressIndicator`. Tap anywhere outside the buttons restores the full
  player; each button consumes its own click so none of them also triggers restore.

**`app/.../MainActivity.kt` changes:**
- Two new Activity-level fields: `miniPlayerUri` (which URI is "in session",
  independent of the current screen) and `isMuted`.
- `WatermelonNavHost` gained an `onPlayerUriChanged` callback, fired from the existing
  `player/{uri}` route's `LaunchedEffect` right where it already calls
  `UserIntent.Play(mediaUri)` — this is what populates `miniPlayerUri`.
- `Scaffold`'s content slot now wraps `MiniPlayerBar` + `WatermelonNavHost` in a
  `Column`, bar on top, nav host given `Modifier.weight(1f)` below it — matching the
  spec's "docked bar at the top, rest of the screen's content renders beneath it".
- Visibility (`showMiniPlayer`) is `miniPlayerUri != null && not on the player route
  && not mid-PiP` — deliberately independent of back-stack state, since the whole
  point is that it survives navigating away from the player.
- Next/Previous reuse the existing `PlaybackQueue` singleton (already built for the
  full player's own Next/Previous) — no new queue logic.
- Mute drives `MediaController.volume` directly (0f/1f), not the system media stream —
  keeps it scoped to this app's playback rather than the device-wide volume the phone
  player's existing mute button uses.
- Close pauses playback and clears `miniPlayerUri`. Natural end-of-video only clears it
  when `PlaybackQueue.nextOf(uri) == null` (nothing left to auto-advance to) — matches
  "natural playback end with empty queue" from the spec.
- No change to `PlaybackController`/`PlaybackControllerImpl` (playback-engine module)
  or to `PlaybackConnection` — the existing Activity-hoisted `MediaController` already
  keeps the Media3 session/service running across navigation on its own; the mini-player
  only had to add UI on top of it, not change how playback survives navigation.

**Deliberately not built (unspecified in the spec, flagged rather than guessed):**
- Scrubbing — confirmed read-only per your answer.
- Any thumbnail/poster fallback — spec calls for a live surface, so none was added.

**Not yet manually verified:**
- Multiple `PlayerView`s attaching to the same `Player`/`MediaController` (mini-player's
  surface vs. the full player's surface) relies on Media3's documented behavior that the
  most-recently-attached `PlayerView` gets live video output and previous ones are
  cleanly detached. This is standard Media3 behavior and the mini-player is never
  composed at the same time as the full player screen (mutually exclusive by route), so
  there should be no contention — but it hasn't been run on-device to confirm no visual
  flash/glitch during the hand-off in either direction.
