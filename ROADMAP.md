# Noxio roadmap

## Next
- [ ] Sleep timer: built and unit tested (timer state, fade curve, shake detection), needs on-device testing (centered moon with countdown, 30 / custom / 60 min, end of track, fade-out, shake to reset)
- [x] Equalizer moved from the now playing toolbar to Settings > Playback
- [x] Icon: Crescent and Sparkle (adaptive icon on pure black, themed icon layer, splash, notification glyph)
- [x] Minimal launcher icon (white, no background), switchable in Settings > Appearance; needs a check on a device
- [ ] Rebrand: fastlane metadata, about screen
- [x] Bottom navigation: home screen (search row, tabs and floating shuffle button at the bottom), album, genre, playlist and artist pages with a bottom bar, artist tabs you can swipe between, search with filter chips at the bottom that skip hidden tabs; needs a check on a device
- [x] Genre tab removed (saved tab orders are read without it); listening stats bottom-oriented with swipe between artists, albums and songs; needs a check on a device
- [x] No three-dot buttons on list rows, long press opens the item menu (selecting several songs by long press is gone for now); all bottom sheets pure black
- [x] Black theme: menus, dialogs, sheets, app bar and status bar pure black; grey shuffle and repeat in the notification; optional wavy seek bar
- [x] Settings regrouped into five screens (Appearance, Home and player, Playback, Library, Listening stats)
- [x] About page: Noxio links, Auxio credit and fork notice, upstream donate and supporters removed; needs a check on a device
- [ ] ReplayGain scan (Settings > Playback > Calculate ReplayGain): loudness measurement is built and unit tested against the EBU reference signal; decoding, the background service and the notification need on-device testing, and measured values are only used for songs without a tag
- [x] Unit tests for the stats and sleep timer logic, coverage badge, CI (format, Android lint, tests), tagged dev releases

## Later
- [ ] Stats redesign, image sharing, CSV export and weekly backup: implemented, needs on-device testing
- [ ] CSV: Musicolet-compatible columns, history export with timestamps (backup), most-played export, import of Noxio and Musicolet files (needs on-device testing)
- [ ] Listening stats: implemented, needs a build and on-device testing (Room database, week / month / year, top songs, artists, albums, total time; open it from the home overflow menu)
- [ ] Shareable "Wrapped" cards

## Ideas
- More screenshots (settings, playlists, the share cards) and ones from a real device
- Bottom bar on the now playing screen for jumping to Songs, Albums, Artists and Playlists (deferred)
- Show the remaining time next to the toolbar icon (currently the icon is only tinted)
