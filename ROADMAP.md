# Noxio roadmap

## Next
- [ ] Sleep timer: built and unit tested (timer state, fade curve, shake detection), needs on-device testing (small moon next to ⋯ with countdown, own notification with live countdown, +10 min and Stop, 30 / custom / 60 min, finish last song, fade-out, shake to restart)
- [x] Equalizer moved from the now playing toolbar to Settings > Playback
- [x] Icon: Crescent and Sparkle (adaptive icon on pure black, themed icon layer, splash, notification glyph)
- [x] Minimal launcher icon (white, no background), switchable in Settings > Appearance; needs a check on a device
- [ ] Rebrand: fastlane metadata, about screen
- [x] Bottom navigation: home screen (search row, tabs and floating shuffle button at the bottom), album, genre, playlist and artist pages with the same bottom row (filter field on artists and playlists, sort, more, floating shuffle button; Cancel and Save replace it while a playlist order is edited), artist tabs you can swipe between, search with filter chips at the bottom that skip hidden tabs; needs a check on a device
- [x] Queue: search row under the playing strip (filters the queue, tap jumps to the song), sort of the whole queue in the usual sort dialog (name, artist, album, date, duration, random; ascending or descending), the current song keeps playing; now playing title centered without the down arrow; needs a check on a device
- [x] Genre tab removed (saved tab orders are read without it); listening stats bottom-oriented with swipe between artists, albums and songs; needs a check on a device
- [x] No three-dot buttons on list rows, long press opens the item menu (selecting several songs by long press is gone for now); all bottom sheets pure black
- [x] Themed-icon launchers (Kvaesitso) no longer clip the crescent; stats backup has a Back up now button and says that one file is replaced; search chips stay above the playback bar
- [x] Default tab order Artists, Albums, Songs, Playlists; an order still at the old Auxio default is reset once, one set by hand is kept
- [x] README screenshots retaken: ten shots of what differs from Auxio
- [x] Playback bar always has previous, play and next; the custom bar action setting is removed
- [x] Musicolet weekly and monthly "Most played songs" files import into their week or month, spread over its days (weekly preferred, re-importing replaces); imported plays are marked as estimated: they count for weeks listened (year and all time), month and year bars and first/last play (with ≈), not for days listened or day bars; needs a check on a device with real Musicolet files (the file naming of monthly files is a guess)
- [x] Full backup (settings, listening history, playlists) in its own Settings > Backup screen, as noxio_YYYY-MM-DD.zip every day, week or month at a chosen day and hour, the last five kept, with a safeguard against writing an empty or much smaller backup over a good one, and Restore from backup; needs a check on a device (restoring playlists especially)
- [x] German translation of everything Noxio added, and of the Auxio texts whose meaning Noxio changed
- [x] No back buttons left (search, settings, about, listening stats, detail pages), gestures only; needs a check on a device
- [x] Listening stats: tap an artist, album or song for a closer look (plays, time, share of all listening, days listened, top songs or plays over time, first play and total) that can be shared or saved as an image; needs a check on a device
- [x] Default and minimal icon without a background layer, so Kvaesitso no longer cuts them to its icon shape (seen with the pentagon and triangle shapes on an emulator); needs a check on a device
- [x] Black theme: menus, dialogs, sheets, app bar and status bar pure black; grey shuffle and repeat in the notification; wavy seek bar on by default, can be turned off
- [x] Settings in four screens (Appearance with the library tabs, Playback, Library, Backup); week start, what plays from the library or a detail page, headset autoplay, pause on repeat, remember pause, always exit on close and intelligent sorting removed
- [x] About page: Noxio links, Auxio credit and fork notice, upstream donate and supporters removed; needs a check on a device
- [x] Unit tests for the stats and sleep timer logic, coverage badge, CI (format, Android lint, tests), tagged dev releases

## Later
- [ ] Stats redesign, image sharing, CSV export and weekly backup: implemented, needs on-device testing
- [ ] CSV: Musicolet-compatible columns, history export with timestamps (backup), most-played export, import of Noxio and Musicolet files (needs on-device testing)
- [ ] Listening stats: implemented, needs a build and on-device testing (Room database, week / month / year, top songs, artists, albums, total time; open it from the home overflow menu)
- [ ] Shareable "Wrapped" cards

## Not planned
- Editing or writing tags (including ReplayGain) in audio files: the player only reads tags

## Ideas
- More screenshots (settings, playlists, the share cards) and ones from a real device
- Bottom bar on the now playing screen for jumping to Songs, Albums, Artists and Playlists (deferred)
- Show the remaining time next to the toolbar icon (currently the icon is only tinted)
