# Noxio roadmap

## Open
Nothing planned right now.

## Needs a check on a phone
Built and unit tested where there is logic, and looked at on an emulator, but not yet confirmed on a real device:
- Sleep timer: hold play/pause for the timer sheet, countdown inside play/pause, the timer pauses with playback, custom duration up to 599 minutes, its own notification with a live countdown, +10 min and Stop, finish last song, fade-out, shake to restart
- Backup: an automatic backup at its chosen hour, and Restore from backup (playlists especially)
- Minimal and default launcher icon in Kvaesitso (no background layer, so it is not cut to the icon shape)
- Library settings and Music sources: simplified options (no automated screen or dialog tests; not tested on a device)
- Appearance: simplified settings, rounded UI and cover proportions (no automated screen tests; not tested on a device)
- Item menus: shorter options and playlist group dividers (no automated screen tests; visual review pending)
- Year in review cards on a phone screen
- Musicolet monthly files: their file naming is a guess (weekly files are confirmed)
- No back buttons anywhere, the back gesture only

## Done
- Library settings: Automatic reloading and Multi-value separators removed. Background watching is always off, with Refresh and Rescan still available; native multi-value tags stay supported, without extra punctuation splitting, even if old preferences are restored. The unused separator dialog is removed; Hide collaborators remains configurable.
- Music sources: Include hidden files removed; hidden files are always excluded in File picker mode, even with an old saved preference or restored backup. Other source and folder options are kept.
- Appearance: round mode and the wavy seek bar are always enabled; the Round mode, Wavy seek bar and Force square album covers switches and the Album covers selector are removed. Cover proportions are preserved, including for old installs or restored settings. Artwork always uses Balanced quality (embedded artwork cached as JPEG quality 85 with a 750-pixel downsampling target); old quality choices are discarded, and the empty Images section is removed.
- Shorter item menus: duplicate Play, Shuffle and View entries removed; queue actions first, playlist management grouped with Delete last; audio sharing kept for songs
- Year in review ("Wrapped"): five cards to swipe through, share or save (the year with its months, top artist, top songs, top album, and a summary), from the menu of the listening stats
- Listening stats: top artists, albums and songs with plays and time per week, month, year or all time, swiping between artists, albums and songs, a closer look at a single artist, album or song, and colorful cards to share or save as an image
- Musicolet weekly and monthly "Most played songs" files import into their week or month, spread over its days (weekly preferred, a re-import replaces); imported plays count as estimated: for weeks listened, month and year bars and the first and last play (with ≈), not for days listened or day bars
- CSV export and import of the full listening history, with Musicolet-compatible columns
- Full backup of settings, listening history and playlists as noxio_YYYY-MM-DD.zip every day, week or month at a chosen day and hour, the last five kept, with a safeguard against an empty or much smaller backup replacing a good one, and Restore from backup
- Sleep timer with fade-out, finishing the last song and shake to restart
- Settings in four screens (Appearance with the library tabs, Playback, Library, Backup); week start, what plays from the library or a detail page, headset autoplay, pause on repeat, remember pause, always exit on close, intelligent sorting and the custom playback bar action removed
- Bottom navigation: home screen with the search row, tabs and floating shuffle button at the bottom; album, genre, playlist and artist pages with the same bottom row; search with filter chips at the bottom; no back buttons, gestures only
- Queue: search row under the playing strip, sort of the whole queue (name, artist, album, date, duration, random); now playing title centered
- Playback bar with previous, play and next; wavy seek bar while playing
- Default tab order Artists, Albums, Songs, Playlists, no Genres tab; no three-dot buttons on list rows (long press opens the item menu); black theme for menus, dialogs, sheets and bars
- Icon: Crescent and Sparkle, and a minimal white outline icon switchable in Settings > Appearance
- About page with Noxio links, the Auxio credit and fork notice; store listing (fastlane) with a Noxio description in English and German, new screenshots and feature graphic, Auxio's changelogs and other languages removed
- README screenshots of what differs from Auxio, retaken for the backup, the stats with imported plays and the sleep timer in the play controls
- German translation of everything Noxio added
- Unit tests for the Noxio logic, coverage badge, CI (format, Android lint, tests), build caching, signed releases with an ARM64 APK

## Not planned
- Editing or writing tags (including ReplayGain) in audio files: the player only reads tags

## Ideas
- Bottom bar on the now playing screen for jumping to Songs, Albums, Artists and Playlists (deferred)
