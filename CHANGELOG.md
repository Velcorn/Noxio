# Noxio changelog

Noxio versions follow major.minor.patch: a minor version has new features or changes how the app looks or
behaves, a patch version only fixes things. Auxio's own history is in its repository.

## v1.9.0

## What's New
- Playlist pages: *Remove from playlist* in a song's menu (only the first copy if it is there twice)
- Playlists: *Remove duplicates* in the playlist menu
- Queue: *Save as playlist* in a new More menu, which opens the playlist picker with a new playlist at its end
- Search and Menu keys of phones that have them work like Ctrl+F and Ctrl+M in keyboard phone mode

## What's Improved
- Search puts the best matches first (exact, starts with, a word starts with, contains, all the words in any order)
- The queue always opens at the song that is playing
- The position in the song is saved every 15 seconds while playing, and when playback starts or stops, so the last song survives
  the app being stopped without notice

## What's Changed
- Adding songs to a playlist skips those that are in it already, and says so

## v1.8.0

## What's New
- Keyboard phone mode: Alt with a key that has a number printed on it starts a search with that digit

## What's Changed
- Tabs switch with Ctrl+comma and Ctrl+period instead of Alt+number
- The shortcut overview spells out Backspace and lists 0-9

## v1.7.1

## What's Fixed
- Keyboard phone mode switched on without a hardware keyboard locked the screen out of the settings: the search row stays
  then
- The floating button for new playlists is gone in keyboard phone mode, New and Import playlist are in the Ctrl+M menu

## v1.7.0

## What's New
- Keyboard phone mode goes all the way for phones like the Clicks Communicator: no search rows, no tabs, no floating
  shuffle button and no buttons on the playing strip
- Ctrl plus a letter for everything: N/B next/previous, J/L 10 seconds back/ahead, P player, Q queue, S shuffle, R repeat,
  T sleep timer, F search, O sort, M menu, H overview of the keys, which also shows once by itself and in the settings
- Typing searches or filters the page or the queue, Space plays or pauses, Backspace goes back without leaving the app,
  Enter opens the first search result

## v1.6.0

## What's New
- Keyboard phone mode (Settings > Appearance, automatic when a hardware keyboard is found, on or off): typing opens
  the search, Space plays and pauses

## v1.5.1

## What's Fixed
- Fixed the CI build failing when the ninja-build download failed (also in the tag 1.5.0, which was never released)

## What's Changed
- The playing strip sits under the search row on the home screen, album, artist and playlist pages and in the queue,
  nearer the thumb
- The search field of a playlist page shows the playlist name on first open

## v1.4.2

## What's New
- Sleep timer notification: Restart between +10 min and Stop

## What's Improved
- The sleep timer countdown is centered in the play button, the icon sits above it
- *Stop timer* is just *Stop*

## v1.4.1

## What's Improved
- Short and square screens (under 480 dp high, like the Clicks Communicator): icon-only home tabs, the stats totals in the
  title row, album, artist, genre and playlist headers without the empty gap at the top, centered text in the player

## v1.4.0

## What's New
- Compact player for short screens: centered square cover, shorter control row, seek bar kept down to 300 dp of height
- Sleep timer: all three duration tiles are editable on the first tap, remembered and sorted shortest first (30 / 45 / 60
  minutes at first)

## What's Improved
- Settings: short lists sit at the bottom above the navigation inset

## v1.3.1

## What's Changed
- Simpler settings: Automatic reloading, multi-value separators, Include hidden files, Round mode, Wavy seek bar, Force
  square album covers, Album covers and the image quality choice are gone and always use what is best (see the README)

## v1.3.0

## What's New
- Year in review: five cards to swipe through, share or save

## What's Improved
- Shorter item menus without the duplicate Play, Shuffle and View entries; playlist actions grouped, Delete last

## v1.2.1

## What's Improved
- Releases come as a universal and a smaller ARM64 APK

## v1.2.0

## What's Changed
- The sleep timer is in the play controls (hold play/pause) and its countdown pauses with playback

## v1.1.3

## What's New
- Sleep timer countdown inside the play button and in its own quiet notification

## What's Changed
- Settings in four screens: Appearance, Playback, Library, Backup

## v1.1.2

## What's New
- Imported plays count on the stats cards

## What's Improved
- The wavy seek bar is on by default, the playback bar is fixed

## v1.1.1

## What's Improved
- Importing weekly or monthly lists asks whether to remove plays without a date

## v1.1.0

## What's New
- Musicolet import: weekly and monthly "Most played songs" files land in their week or month
- Backup of settings, listening history and playlists as noxio_YYYY-MM-DD.zip, on a schedule, with Restore
- German translation of everything Noxio adds

## v1.0.0

First release, a fork of Auxio 4.1.5.

## What's New
- Sleep timer with fade-out, finishing the last song and shake to restart
- Listening stats: top artists, albums and songs per week, month, year or all time, a closer look at each, cards to share
  or save, CSV export and import
- One-handed layout: search, sort and tabs at the bottom, a floating shuffle button, no back buttons, no three-dot buttons
  on rows
- Queue with search and sort, playlists you can search and sort
- Pure black theme, rounded UI, the Crescent and Sparkle icon and a minimal white outline icon
- Package de.devfin.noxio, installs next to Auxio

## What's Changed
- Default tab order Artists, Albums, Songs, Playlists, no Genres tab
- Settings trimmed to what is used
