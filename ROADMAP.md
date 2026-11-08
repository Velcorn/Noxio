# Noxio roadmap

## Next
- [ ] Sleep timer: built and unit tested (timer state, fade curve, shake detection), needs on-device testing (centered moon with countdown, 30 / custom / 60 min, end of track, fade-out, shake to reset)
- [x] Equalizer moved from the now playing toolbar to Settings > Audio
- [x] Icon: Crescent and Sparkle (adaptive icon on pure black, themed icon layer, splash, notification glyph)
- [x] Minimal launcher icon (white, no background), switchable in Settings > Personalize; needs a check on a device
- [ ] Rebrand: fastlane metadata, about screen
- [ ] Bottom navigation: home screen done (search row and tabs at the bottom), playlist screen (bottom bar, one sort button with custom order, search, M3U export), About page, ReplayGain scan under Settings > Audio still to do (see docs/mockups/navigation.html)
- [x] Unit tests for the stats and sleep timer logic, coverage badge, CI (format, Android lint, tests), tagged dev releases

## Later
- [ ] Stats redesign, image sharing, CSV export and weekly backup: implemented, needs on-device testing (see docs/mockups/stats.html)
- [ ] CSV: Musicolet-compatible columns, history export with timestamps (backup), most-played export, import of Noxio and Musicolet files (needs on-device testing)
- [ ] Listening stats: implemented, needs a build and on-device testing (Room database, week / month / year, top songs, artists, albums, total time; open it from the home overflow menu)
- [ ] Shareable "Wrapped" cards

## Ideas
- Bottom bar on the now playing screen for jumping to Songs, Albums, Artists and Playlists (deferred)
- Show the remaining time next to the toolbar icon (currently the icon is only tinted)
