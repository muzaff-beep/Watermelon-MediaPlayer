package com.watermelon.ui

/**
 * Centralized icon resource map for Watermelon MediaPlayer.
 *
 * All entries resolve to the app's own custom-themed vector drawables
 * (`res/drawable/ic_*.xml`) rather than generic Material Icons — the repo already
 * ships a complete matching icon set; the previous version of this file bypassed it
 * and substituted stock Material glyphs, which produced two real bugs:
 *
 *  - [VolumeMuteOff] pointed at the *unmuted* speaker glyph while being named/used as
 *    the "muted" state's toggle target — it now correctly resolves to a distinct icon.
 *  - [VolumeLow] and [VolumeMedium] both resolved to the same Material glyph
 *    (`Icons.Filled.VolumeDown`), so the two volume levels were visually identical —
 *    they now point at the two distinct custom drawables that already existed unused
 *    in `res/drawable/`.
 *
 * Consumers (`LabeledIconButton`, `IconStub` in `PlayerControlPanel`) already accept
 * `Any` and branch on `is Int` / `is ImageVector`, so switching these values from
 * ImageVector to drawable-resource Int is source-compatible with every existing call
 * site — no call site needed to change.
 */
object WatermelonIcons {
    // Playback
    val Play: Int = R.drawable.ic_play
    val Pause: Int = R.drawable.ic_pause
    val SkipNext: Int = R.drawable.ic_skip_next
    val SkipPrevious: Int = R.drawable.ic_skip_previous
    val FastForward: Int = R.drawable.ic_fast_forward
    val Rewind: Int = R.drawable.ic_rewind

    // Volume & Audio — four distinct levels, not two glyphs shared across four names.
    val VolumeHigh: Int = R.drawable.ic_volume_high
    val VolumeMedium: Int = R.drawable.ic_volume_medium
    val VolumeLow: Int = R.drawable.ic_volume_low
    val VolumeMute: Int = R.drawable.ic_volume_mute
    /** The "tap to unmute" affordance — deliberately the high-volume glyph, distinct from [VolumeMute]. */
    val VolumeMuteOff: Int = R.drawable.ic_volume_high

    // Repeat & Shuffle — repeat-all is now its own asset, not aliased to repeat-off.
    val RepeatOff: Int = R.drawable.ic_repeat_off
    val RepeatOne: Int = R.drawable.ic_repeat_one
    val RepeatAll: Int = R.drawable.ic_repeat_all
    val ShuffleOn: Int = R.drawable.ic_shuffle_on
    val ShuffleOff: Int = R.drawable.ic_shuffle_off

    // Common actions
    val Share: Int = R.drawable.ic_share
    val Favorite: Int = R.drawable.ic_favorite
    val FavoriteBorder: Int = R.drawable.ic_favorite_off
    val Delete: Int = R.drawable.ic_delete
    val PlaylistAdd: Int = R.drawable.ic_playlist_add
    val Search: Int = R.drawable.ic_search
    val Settings: Int = R.drawable.ic_settings
    val Close: Int = R.drawable.ic_close
    val Check: Int = R.drawable.ic_confirm
    val Edit: Int = R.drawable.ic_edit
    val Refresh: Int = R.drawable.ic_refresh

    // Layout & View
    val ViewList: Int = R.drawable.ic_view_list
    val ViewGrid: Int = R.drawable.ic_view_grid
    val Sort: Int = R.drawable.ic_sort_ascending

    // Player specific
    val ArrowBack: Int = R.drawable.ic_arrow_back
    val Lock: Int = R.drawable.ic_lock
    val LockOpen: Int = R.drawable.ic_lock_open
    val MoreVert: Int = R.drawable.ic_more_vertical
    val MoreHoriz: Int = R.drawable.ic_more_horizontal

    // Note: highly specialized one-off icons (VHS effect, sleep timer, PiP, screenshot,
    // badge_new, size_*, sort variants, orientation, ratio) are still referenced directly
    // via painterResource(R.drawable.ic_*) at their call sites rather than aliased here,
    // since they're each used in exactly one place.
}
