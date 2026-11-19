# Noxio changelog

Noxio versions follow major.minor.patch: a minor version has new features or changes how the app looks or
behaves, a patch version only fixes things. Auxio's own history is in its repository.

## v1.27.1

## What's Changed
- A tap on the song, artist or album in the player, or on the artist of an album page, no longer opens the album or artist; their long press menus have Go to artist and Go to album

## v1.27.0

## What's New
- Settings tab: the search button searches every setting; tap a match to open its screen
- A guide to all taps, long presses and swipes (docs/GESTURES.md)

## What's Improved
- Home: a long press on the search button shows a Shuffle all button above it
- An empty search field goes away with the keyboard; the search button brings it back
- Choose picture lists the albums from A to Z and is only in the Artists tab's menu
- About and the rest of the Settings tab sit right above the playing strip

## What's Fixed
- The wide layout's floating button showed the shuffle icon instead of search

## v1.26.0

## What's New
- Settings is the last home tab: swipe past Playlists or tap the gear
- Choose picture: an artist can show one of their album covers instead of the collage

## What's Improved
- The floating button searches: the library on home and album pages, the field above the playing strip on artist and playlist pages and in the queue; a long press offers Shuffle and the page's menu
- A long press on the header of an album, artist or playlist page opens its menu, now with Shuffle and Sort; page headers are centered
- Albums with several discs keep their discs in order, also when sorted by name
- Buttons no longer show a hint on a long press

## What's Changed
- The search rows, the queue's sort and ⋮, and the sort and ⋮ buttons in page headers are gone
- Fewer sort choices: an artist's albums by name or date, their songs by name, an album by track or name, a playlist by name; the queue can only be shuffled

## v1.25.0

## What's Improved
- Long press instead of three dots: a song in the queue, the song in the player and the playing strip open the song's menu; the queue menu can remove the song
- Now playing: song, artist and album are centered
- Home: the bottom row is only the search field; the tabs show bigger icons without labels, with Settings at their end
- An artist's albums start newest first (unless you picked an order)

## What's Changed
- Listening stats and About moved into Settings, which now lists Appearance, Library, Playback, Listening stats, Backup and About
- The home lists are always sorted by name; album, artist and playlist pages keep their sort
- Share is gone from all menus, and swiping no longer removes a song from the queue

## v1.24.1

## What's Improved
- Sleep timer: tapping a duration only selects it; tap the selected one again to edit it

## v1.24.0

## What's Improved
- Stats images no longer carry the Noxio label; titles, lists and tiles use the freed space

## What's Changed
- All saved files are named `Noxio_<What>_YYYY-MM-DD_HH-MM-SS`: `Noxio_Backup_….zip`, `Noxio_Stats_….csv` and `Noxio_<name>_….png` for images
- A second backup on the same day gets its own file instead of replacing the first; the last five files are kept

## v1.23.1

## What's Fixed
- Imported Musicolet weeks and months are spread evenly over their days instead of piling up on the first day; import the files again to fix earlier imports
- Year in review: the hours above the bars are no longer hidden by the tallest bars

## v1.23.0

## What's New
- Year in review: add your name to its title, and see the year as a heatmap of your listening days
- The week picker in the listening stats lists week numbers, like the month picker

## What's Improved
- Year in review shows listening time per month, with the hours on each bar
- The period pickers leave out months and weeks before your first play; years run oldest to newest

## v1.22.0

## What's New
- Listening stats: tap the shown period to pick a week, month or year from a calendar shaded by listening time

## What's Improved
- The back arrow in the listening stats stops at your first play

## v1.21.0

## What's Changed
- Backups are named Noxio_YYYY-MM-DD.zip; older noxio_ files still count as backups

## v1.20.1

## What's Fixed
- The wavy seek bar switch applies right away, also while the player is on screen

## v1.20.0

## What's New
- Wavy seek bar switch in Settings > Appearance, on by default; turn it off for less motion

## v1.19.0

## What's New
- Changelog in the app: About has a Changelog row that lists every Noxio release

## v1.18.0

## What's New
- A backup that is much smaller than your biggest one is no longer written silently: a manual backup asks first, a scheduled one is skipped with a notification, and Settings > Backup asks when you open it
- Date sort for songs with a date popup while scrolling (from Auxio)

## What's Improved
- Backups are kept as the five newest; old `_small` files count like any other backup

## What's Fixed
- The keyboard now really opens when editing a song's tags
- Restoring a backup applies the theme, black theme, accent and launcher icon right away
- The wavy seek bar follows playback again when animations are on (Auxio fix)

## v1.17.1

## What's Fixed
- The keyboard opens when editing a song's tags

## v1.17.0

## What's New
- Artist separator in Library settings: semicolon by default, with ampersand, slash, plus and comma available for artist and album artist tags

## What's Improved
- Song properties and Edit tags show the fields in the same order: title, artists, album, album artists, track and date
- Library settings put the separator before the two artist visibility options, with clearer names and a short explanation

## What's Changed
- Removed calculated ReplayGain and its scan, saved measurements and backup entry; ReplayGain tags in music files still work
- The selected artist separator also applies when editing tags

## v1.16.0

## What's New
- *First album artist only* in Library settings shows albums and songs under the first album artist and hides secondary album artists from the Artists tab

## What's Improved
- Album and artist pages start their artwork and short lists at the top; long titles scroll to show the full name

## v1.15.1

## What's Improved
- Album pages: the songs start right under the album header, also on short albums
- An album with only one song has no sort button and no shuffle button

## v1.15.0

## What's New
- Edit tags in Song properties: title, artists, album, album artists, date, track and disc. Playlists and measured ReplayGain follow the song
- *Write calculated ReplayGain to tags* (Settings > Playback) saves the measured values in the song files. Android asks before any file is changed
- Album ReplayGain: once every song of an album is measured, the album is measured as a whole, for *Prefer album*. Songs measured before are measured once more
- After a restart the song that played is ready right away, before the library has loaded; the rest of the queue follows

## What's Fixed
- On an album with only one song, the song was below the screen and could not be tapped

## What's Changed
- Song properties no longer show the genre
- Noxio now changes your music files when you ask it to (editing tags, writing ReplayGain); it never does on its own

## v1.14.0

## What's New
- An "i" in the header of the settings that need an explanation (ReplayGain strategy, Calculated ReplayGain, Keyboard, Music, Content, Backup) opens a short text about them

## What's Improved
- Settings summaries are shorter, the details are in the "i" texts
- Settings > Backup is split into Automatic backup (folder first, then schedule) and Manual; Launcher comes before Keyboard in Appearance
- *Calculate ReplayGain* and *Prefer ReplayGain tags* are in their own group, Calculated ReplayGain

## What's Changed
- The ReplayGain pre-amp is removed; songs always play at the level their ReplayGain gives (an old saved value is ignored)

## v1.13.1

## What's Improved
- Long album, artist and song titles in the stats lists, the share cards and the year in review wrap to a second line instead of being cut off, so titles that differ only at the end can be told apart
- Settings > Playback: *Prefer ReplayGain tags* sits above *Calculate ReplayGain*

## v1.13.0

## What's New
- The backup holds the calculated ReplayGain, and a restore puts it back for songs that have none yet
- *Delete calculated ReplayGain* in Settings > Playback removes the loudness Noxio measured (the tags in your files are not touched)
- A song's details show the ReplayGain Noxio measured
- The result of a calculation shows how long it took

## What's Improved
- Calculating ReplayGain measures up to 6 songs at once and its notification says "Calculating ReplayGain"

## What's Fixed
- The notification of the ReplayGain calculation no longer stays after it is done

## v1.12.0

## What's New
- ReplayGain calculation: *Calculate ReplayGain* in Settings > Playback measures the loudness of your songs in the background (with a notification), so quiet and loud songs play at the same level. It can be stopped and continued, and your files are not changed
- *Prefer ReplayGain tags* (on by default): a song's own tag is used when it has one and the measured loudness only for songs without; switched off, the measured loudness is used for every measured song

## v1.11.0

## What's New
- Swipe up on the open player to open the queue (swiping down still closes the player)

## What's Improved
- Loading the library is faster again, about 3 times faster than Auxio's on a Pixel 9 Pro (7.4 s to 2.5 s with 476 songs; the check for the transliterator is now done once as well)

## v1.10.2

## What's Changed
- Progress while the library loads is reported at most every 100 ms (it was every 50 songs), so it no longer slows the load on libraries of any size
- Internal: the name preparation keeps Auxio's own check and only creates the transliterator once, which keeps the code closer to Auxio

## v1.10.1

## What's Improved
- Loading the library is ~2.5 times faster (14.3 s to 5.8 s with 6,467 songs, measured on an emulator)

## v1.10.0

## What's New
- Queue: *Clear queue* in the queue's More menu, which empties the queue and stops playing

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
