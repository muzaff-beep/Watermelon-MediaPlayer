# Premium Media Tools — UI Manifest

Companion to `PREMIUM_MEDIA_TOOLS_BLUEPRINT.md` and the built `media-tools` module.
Describes what UI needs to exist, where it lives, and how it wires to what's already built.
No code yet — this is the spec to build from when UI work starts.

---

## 1. File/module placement

All new UI lives in `ui-presentation`, following the existing naming convention
(`XScreen.kt` for full screens, `XDialog.kt` for dialogs, phone/TV split where the app
already splits — e.g. `PhonePlayerScreen.kt` / `TvPlayerScreen.kt`).

```
ui-presentation/src/main/kotlin/com/watermelon/ui/
  screens/
    TrimScreen.kt                  // new, Phase 3
    CompressScreen.kt              // new, Phase 4
  components/
    MediaJobProgressSheet.kt       // new — shared progress/cancel UI (Phase 1)
    KeepOrDeleteOriginalDialog.kt  // new — post-completion prompt (trim/compress only)
    PremiumUpsellDialog.kt         // new, Phase 5
  viewmodel/
    TrimViewModel.kt               // new
    CompressViewModel.kt           // new
    MediaJobsViewModel.kt          // new — wraps MediaJobManager.jobs for any screen to observe
```

`VideoListScreen.kt` and `PhonePlayerScreen.kt` (+ TV equivalents) get new overflow-menu
entries; no new screens needed there, just `DropdownMenuItem` additions to their existing
`DropdownMenu` (confirmed present in `VideoListScreen.kt` — reuse, don't reinvent).

---

## 2. Entry points

### 2.1 Video list item overflow menu
`VideoListScreen.kt` / `TvVideoListScreen.kt` — existing `DropdownMenu` per item gets three
new `DropdownMenuItem`s, gated by the premium flag (see §6):

- **Extract Audio** → fires `MediaJobManager.extractAudio(...)` directly (no config screen —
  it's fire-and-forget, per blueprint §3).
- **Trim** → navigates to `TrimScreen`.
- **Compress** → navigates to `CompressScreen`.

### 2.2 Player screen overflow menu
`PhonePlayerScreen.kt` / `TvPlayerScreen.kt` — same three actions, same gating, operating on
the currently-playing video. Same engine calls as §2.1, just a different entry point.

### 2.3 Settings screen
`SettingsScreen.kt` gets a new section: **Media Tools** — two path fields (see §7).

---

## 3. TrimScreen

**Purpose:** let the user pick start/end points and confirm a trim job.

**Layout:**
- Video preview area (reuse `PlaybackController`/`MediaController` the same way
  `PhonePlayerScreen` already does — no new playback primitive).
- Dual-handle scrubber bound to `startMs`/`endMs`.
- Live preview: seeking to `startMs`, playing, auto-pausing at `endMs` on handle release —
  same mechanism `PhonePlayerScreen` already has for seek/play/pause.
- Thumbnail strip under the scrubber: **not in v1** (blueprint explicitly scopes this as a
  nice-to-have; ship without it).
- A small, persistent note near the scrubber: *"Trim is instant — no re-encoding. Cut points
  snap to the nearest keyframe, so the exact start/end may shift slightly."* This sets the
  right expectation given `VideoTrimmer`'s hard `setStartsAtKeyFrame(true)` guarantee — the
  UI should not imply frame-accuracy.
- "Trim" button (primary, bottom).

**On "Trim" tap:**
1. Check premium flag (§6) — if locked, show `PremiumUpsellDialog` instead of proceeding.
2. Call `VideoTrimmer.trim(jobManager, inputUri, originalDisplayName, startMs, endMs)`.
3. Show `MediaJobProgressSheet` (§4) for the returned job id.
4. Screen can be dismissed immediately after starting the job — the job runs via
   `MediaJobService`, not tied to this screen's lifecycle.

**States to handle:**
- Video shorter than 2 seconds, or `endMs - startMs` below some minimum (e.g. 500ms) — disable
  "Trim" button rather than let a degenerate job start.
- No audio track / corrupt file — `MediaJobState.Failed` surfaces via the progress sheet;
  this screen doesn't need its own error state beyond that.

---

## 4. CompressScreen

**Purpose:** let the user pick a quality preset and confirm a compress job.

**Layout:**
- Three preset cards: **Small / Medium / Original Quality** (`VideoCompressor.Preset` enum —
  labels only, don't expose raw bitrate/resolution numbers per blueprint's "quick" framing).
- Each card: preset name + a rough size estimate if feasible. Per blueprint, Transformer has
  no pre-flight size API — if shown at all, label it clearly as an estimate (e.g. "~120 MB
  estimated"), computed heuristically from input duration × target bitrate. This is a
  nice-to-have; shipping without an estimate at all is an acceptable v1.
- "Compress" button (primary, bottom), disabled until a preset is selected.

**On "Compress" tap:**
1. Check premium flag (§6).
2. Call `VideoCompressor.compress(jobManager, inputUri, preset, originalDisplayName)`.
3. Show `MediaJobProgressSheet` (§4).
4. Same dismiss-independent-of-job-lifecycle behavior as TrimScreen.

---

## 5. MediaJobProgressSheet (shared component)

**Purpose:** one component, reused by Extract Audio / Trim / Compress — mirrors the
blueprint's Phase 1 "shared progress + notification UI" requirement, and reflects the real
`MediaJobService` notification that's already running the same job in parallel.

**Behavior:**
- A bottom sheet or persistent bar, observing a single `MediaJob` from
  `MediaJobManager.jobs` (via `MediaJobsViewModel`) by job id.
- Shows: job type label, progress bar (`job.progressPercent`), Cancel button
  (→ `MediaJobManager.cancel(id)`).
- On `MediaJobState.Completed`:
  - If `awaitingOriginalFileDecision == true` (trim/compress only — confirmed in
    `MediaJobState`, not audio extraction): show `KeepOrDeleteOriginalDialog` (§5.1).
  - Otherwise (audio extraction): show a simple success state with the new file's name,
    then auto-dismiss or let the user dismiss manually.
- On `MediaJobState.Failed`: show the failure reason, offer "Dismiss" (no auto-retry — not
  specified, don't invent one).
- On `MediaJobState.Cancelled`: dismiss immediately, no lingering UI.

This component does **not** need to poll anything itself — `MediaJobManager.jobs` is already
a live `StateFlow`; the sheet just collects it.

### 5.1 KeepOrDeleteOriginalDialog

**Purpose:** the post-completion "what happens to the original file?" prompt — a hard product
requirement, applies only to Trim/Compress.

**Copy (draft, adjust to house style):**
> *"[original_filename] has been [trimmed/compressed] and saved as [output_filename].
> Keep the original video too, or delete it?"*
> Buttons: **Keep Original** / **Delete Original**

**On "Delete Original" tap:**
- API < 29: call `MediaJobManager.resolveOriginalFileDecision(id, deleteOriginal = true, resolver)`
  directly — works via plain `ContentResolver.delete()` on these OS versions (per
  `OriginalFileDeleter`'s doc).
- API 29+: call `OriginalFileDeleter.requestDelete(jobId, originalUri, resolver)` instead —
  this launches the system consent dialog. **`OriginalFileDeleter` must be constructed in the
  hosting Activity's `onCreate`** (its own constraint, not a UI-layer choice) — so
  `MainActivity` owns one instance; this dialog's ViewModel/composable needs a way to reach
  it (e.g. passed down, or exposed via a CompositionLocal — not decided here, pick whichever
  matches how the app already threads Activity-scoped things to Compose, if it does any).
- The actual deletion only completes when `OriginalFileDeleter`'s result callback fires
  (`resolveOriginalFileDecision` is invoked there, not before) — so on API 29+ this dialog's
  "Delete Original" tap should show a brief loading/pending state until that callback lands,
  not assume deletion succeeded instantly.

**On "Keep Original" tap:**
- Call `MediaJobManager.resolveOriginalFileDecision(id, deleteOriginal = false, resolver)`
  directly (no consent dialog needed for "do nothing").

**Known gap this dialog must surface, not hide:** if the API 29+ consent dialog is dismissed/
cancelled by the user (system back button, etc.), `OriginalFileDeleter`'s callback fires with
`deleted = false` — this dialog should treat that the same as "Keep Original" was chosen, not
error out.

---

## 6. Premium gating (Phase 5)

Per blueprint §6 — a pure UI-layer check, no engine/`MediaJobManager` changes:

- Read `isPremiumUnlocked` from `settingsStore` (same `SharedPreferences` pattern as
  `pureDarkTheme`, confirmed in `MainActivity`).
- Each of the three entry points (§2.1, §2.2 list items; §3/§4 screens' primary buttons)
  checks this flag before proceeding.
- If locked: show `PremiumUpsellDialog` — "Upgrade to unlock," dismissible, **no purchase
  flow** (Phase 6 is separate, out of scope here).
- Gate at the point of action (button tap / menu item tap), not by hiding the entry points
  entirely — létting users discover the features (greyed out or with a small lock icon) is
  generally better for conversion than hiding them, but this is a product call, not a
  technical constraint; flagging as a decision point rather than assuming.

---

## 7. Settings screen additions

New section in `SettingsScreen.kt`: **Media Tools**

- Two editable path fields:
  - **Compressed video folder** — bound to
    `FolderVisibilityStoreImpl.getCompressedOutputPath()` / `setCompressedOutputPath()`.
  - **Trimmed video folder** — bound to
    `getTrimmedOutputPath()` / `setTrimmedOutputPath()`.
- Default values shown as placeholder/current text: `Movies/Watermelon/compressed` and
  `Movies/Watermelon/trimmed`.
- **Must surface a real constraint, not hide it:** on API < 29, custom `RELATIVE_PATH`
  subfolders are silently ignored by `MediaStore.insert()` (confirmed limitation, documented
  in `OutputFileStore`). On those OS versions, this settings section should either:
  - show a note ("Custom folders require Android 10 or later; files will save to the default
    Movies/Music location on this device"), or
  - hide the fields entirely and show that note instead.
  Pick one — don't silently accept the input and fail to honor it later.
- No validation beyond "non-empty" is specified; don't invent path-format validation rules
  not asked for.

---

## 8. MediaJobsViewModel (cross-cutting)

**Purpose:** thin wrapper so any screen/component can observe `MediaJobManager.jobs` without
each one holding its own reference to the manager directly.

- Exposes `jobs: StateFlow<List<MediaJob>>` — pass-through of `MediaJobManager.jobs`.
- Exposes `cancel(id)`, `resolveOriginalFileDecision(id, deleteOriginal)` — thin delegating
  calls.
- **Depends on the same unresolved wiring gap `MediaJobService` has:** there's no DI framework
  in this repo, so how this ViewModel actually gets a `MediaJobManager` instance (constructor
  param from wherever `MainActivity` constructs it, most likely) needs to be decided as part
  of actual implementation — not assumed here.

---

## 9. Open items this manifest deliberately does not resolve

Carried over / new, flagged rather than guessed:

1. **`MediaJobManager` instance ownership** — no DI framework exists in this codebase
   (confirmed by audit). Needs a real decision (singleton in `Application`, constructed in
   `MainActivity` and threaded down, etc.) before `MediaJobsViewModel` or `MediaJobService`
   can be wired for real.
2. **Activity-scoped `OriginalFileDeleter` access from Compose** — same category of problem;
   depends on how (or whether) this app already bridges Activity-scoped objects into Compose.
3. **Premium gating UX** — greyed-out-but-visible vs. fully-hidden entry points (§6) is a
   product decision, not resolved here.
4. **Compression size estimate** — nice-to-have per blueprint; build or skip for v1, not
   mandatory.
5. **Multi-job notification layout** — `MediaJobService` currently shows one job at a time in
   its notification; if a user starts trim + compress simultaneously, only the first shows.
   Not addressed here or in the service.
