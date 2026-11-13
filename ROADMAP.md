# Noxio roadmap

## Open
Nothing planned right now.

## Needs a check on a phone
Built and unit tested where there is logic, and looked at on an emulator, but not yet confirmed on a real device:
- Playback state saved while playing: that the last song and position are there after the app was killed (the saving is tested, the killing is not)
- Sleep timer: hold play/pause for the timer sheet, countdown inside play/pause, the timer pauses with playback, custom duration up to 599 minutes, its own notification with a live countdown, +10 min, Restart and Stop, finish last song, fade-out, shake to restart
- Backup: an automatic backup at its chosen hour, and Restore from backup (playlists especially)
- Minimal and default launcher icon in Kvaesitso (no background layer, so it is not cut to the icon shape)
- Settings alignment: short lists at the bottom; captured and visually checked on an emulator (no automated screen tests; user review and phone testing pending)
- Library settings and Music sources: simplified options (no automated screen or dialog tests; not tested on a device)
- Appearance: simplified settings, rounded UI and cover proportions (no automated screen tests; not tested on a device)
- Item menus: shorter options and playlist group dividers (no automated screen tests; visual review pending)
- Year in review cards on a phone screen
- Short and square screens on the Clicks Communicator itself
- Keyboard phone mode on the Communicator: whether Automatic detects its keyboard, and whether Ctrl, Alt+number and Ctrl with comma or period arrive as expected
- Musicolet monthly files: their file naming is a guess (weekly files are confirmed)
- No back buttons anywhere, the back gesture only

## Done
- Playlists and queue (from the open issues of Auxio): a song's menu on a playlist page has Remove from playlist (the first copy, if it is there twice); the menu of a playlist has Remove duplicates (greyed out when there are none); adding songs to a playlist skips those that are in it already and says so; the queue has a More button (Ctrl+M with the queue open) with Save as playlist, which opens the playlist picker with a new playlist at its end, and Clear queue, which empties the queue and stops playing; opening the queue always starts it at the song that plays.
- Playback state: the position is saved every 15 seconds while playing and whenever playback starts or stops, so the last song and its position survive the app being stopped without notice (Auxio issues about the last song not being remembered).
- Search: the best matches come first (exact, starts with, a word starts with, contains, all the words in any order, file name last), the same matches as before otherwise, equally good ones by name.
- Keyboard phone mode: the Search and Menu keys of phones that have them work like Ctrl+F and Ctrl+M.
- Keyboard phone mode (Settings > Appearance > Keyboard phone: automatic when a hardware keyboard is found, on or off), made for the Clicks Communicator, which has no arrow keys: no search rows (home, pages, queue; they show while typing), no tabs (swipe, or Ctrl+comma and Ctrl+period for the previous and next tab), no floating buttons (shuffle is Ctrl+S, new or imported playlists are in the Ctrl+M menu of the Playlists tab) and no buttons on the play strip. Switched on without a hardware keyboard, the search row with sort and more stays, so the settings can always be reached by touch. Letters or digits (also those printed on letter keys, with Alt) search, or filter an artist or playlist page or the queue; Space plays or pauses; Backspace goes back without ever leaving the app; Ctrl+N/B next/previous, Ctrl+J/L 10 seconds back/ahead, Ctrl+P player, Ctrl+Q queue, Ctrl+S shuffle, Ctrl+R repeat, Ctrl+T sleep timer, Ctrl+F search, Ctrl+O sort, Ctrl+M menu, Ctrl+H or ? the overview of the keys, which also shows once by itself and in the settings. In the search Enter opens the first result. Checked on an emulator with its keyboard; on the Communicator itself pending, as is customizing the keys.
- Playing strip under the search row: on the home screen between the search row and the tabs, on album, artist and playlist pages and in the queue at the bottom, so it is nearer the thumb; the search row follows it and stays above the keyboard. The search field of a playlist page now shows the playlist name on first open.
- Short and square screens (under 480 dp of height, like the 1080 × 1200 Clicks Communicator): home tabs show icons only (labels kept for screen readers), the stats totals move into the bottom title row so the top list shows more rows, album, artist, genre and playlist headers lose the empty toolbar gap, and the player text is centered. Checked on an emulator at 1080 × 1200, 420 dpi; normal phones unchanged.
- Compact player: centered square artwork above centered song details and a shorter full-width control row, with the seek bar retained on screens down to 300 dp available height. The redundant top heading is hidden on these short screens to leave room for the cover. Checked at a Communicator-like 1080 × 1200 resolution at 400 and 480 dpi; real-phone testing pending.
- Sleep timer duration tiles: all three edit on the first tap, remember their values, and sort shortest to longest after confirmation; readable input and labels, with the active field kept above the keyboard. Defaults are 30 / 45 / 60 minutes, and edits remain capped at 1–599 minutes.
- Settings: the main settings menu and Appearance, Playback, Library and Backup place short preference lists at the bottom above the navigation inset. A small fixed title bar sits below the options, matching About; the top toolbar is removed. Long lists start at the top and scroll above the title bar.
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
- Queue ready before the library has loaded (Auxio #1467, deferred until a phone with a big library is there to test it): save each queued song's file record with the queue (Room schema 38 to 39 with a migration), add a small public entry point in `musikr/` that builds a temporary library from cached files, restore the queue from it at start, and let the existing tight restore swap in the real songs when the full library arrives.
