# Noxio roadmap

## Next
- [ ] Sleep timer: built and unit tested (timer state, fade curve, shake detection), needs on-device testing (centered moon with countdown, 30 / custom / 60 min, end of track, fade-out, shake to reset)
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
- [x] No back buttons left (search, settings, about, listening stats, detail pages), gestures only; needs a check on a device
- [x] Listening stats: tap an artist, album or song for a closer look (plays, time, share of all listening, days listened, top songs or plays over time, first play and total) that can be shared or saved as an image; needs a check on a device
- [x] Minimal icon without a background layer, so Kvaesitso no longer cuts it to its icon shape (seen with the pentagon and triangle shapes on an emulator); needs a check on a device
- [x] Black theme: menus, dialogs, sheets, app bar and status bar pure black; grey shuffle and repeat in the notification; optional wavy seek bar
- [x] Settings regrouped into five screens (Appearance, Home and player, Playback, Library, Listening stats)
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
