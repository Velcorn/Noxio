<p align="center"><img src="fastlane/metadata/android/en-US/images/icon.png" width="120" alt="Noxio icon"></p>
<h1 align="center"><b>Noxio</b></h1>
<h4 align="center">A simple, rational music player for Android, built for listening through the night.</h4>
<p align="center">
    <img src="https://img.shields.io/badge/license-GPL%20v3-2B6DBE.svg?style=flat" alt="GPL v3">
    <img alt="Minimum SDK Version" src="https://img.shields.io/badge/API-24%2B-1450A8?style=flat">
</p>

> [!WARNING]
> **I'm not an Android developer.** Noxio is a personal project, and most of its changes are vibe-coded with
> an AI assistant for my own use. Expect rough edges, and review anything you rely on.
>
> The main reason this fork exists is the **sleep timer**. **Listening stats** (a barebones, Spotify
> Wrapped-style summary) are a nice to have. Anything else is a small tweak I wanted for myself.
> It is shared as is, with no promise of support or stability.

## About

Noxio is a fork of [Auxio](https://github.com/OxygenCobalt/Auxio) by Alexander Capehart. Auxio is a great local music
player with a fast, reliable UI, and Noxio keeps that foundation. The name merges **Auxio** with **Nocturne**: the fork
started because a music player that is good to fall asleep to needs a sleep timer.

Noxio is a personal project and will keep diverging from upstream. It is **not** affiliated with the Auxio project, and
upstream issues should not be reported against it.

## What's different from Auxio

| | Auxio | Noxio |
| --- | --- | --- |
| Sleep timer | none | Moon icon above the seek bar. End of track, 30 min, custom minutes or 60 min, with fade-out. Remembers your last choice. Restart it from the notification or by shaking the device. |
| Listening stats | none | Home menu > Listening stats. Top artists, albums and songs with plays and time listened for each, plus the total, per week, month, year or all time. Colorful cards you can share as an image, CSV export and import with an optional weekly backup to a folder. Musicolet's "Most played songs" lists import as your history from before Noxio, counted in All time only. Stored on the device only. |
| Now playing layout | Controls in the middle of the screen | Content packed towards the bottom for one-handed use |
| App icon | One icon | Default, or a minimal white silhouette with no background, chosen in the settings |
| Equalizer shortcut | Button in the now playing toolbar | Moved to Settings > Audio |
| Package name | `org.oxycblt.auxio` | `de.devfin.noxio`, so it installs next to Auxio |

Both features are implemented but have only been build-tested so far. Progress is tracked in
[ROADMAP.md](/ROADMAP.md). Everything else is inherited from Auxio: Media3 playback, gapless playback, ReplayGain, rich
tag support (multiple artists, release types, disc numbers, sort tags), playlists, playback persistence, Android Auto,
widgets, and being completely private and offline.

## Mockups

Clickable mockups of the added features, with the same behavior as the app. They are single HTML files: download them
(or clone the repo) and open them in a browser.

- [Sleep timer](docs/mockups/sleep-timer.html): the timer button, the bottom sheet, the editable Custom tile, shake to
  reset, the notification action, a thumb-zone overlay, and a theme switcher with AMOLED black and Material You colors
- [Listening stats](docs/mockups/stats.html): week, month and year views with example data, the category pill, sharing
  a card as an image, the CSV export and import menu, and the same theme switcher
- [App icon](docs/mockups/icons.html): the icon with its adaptive layers, the themed (Material You) versions, the
  splash screen, the notification glyph and a home screen preview

Screenshots from a real device will be added here once the features have been tested on one.

## Minimal icon

Besides the default icon, Noxio has a minimal version: a flat white crescent, play button and star with no
background at all, so it sits straight on your wallpaper. Switch to it in Settings > Personalize > App icon. Any
launcher shows the choice, since it is the app's own launcher icon, and launchers that support themed icons (like
Kvaesitso) tint the single-color layer with your theme colors.

## Branches

- `main`: stable, releasable state
- `dev`: day-to-day development; merged into `main` when ready

## Permissions

- Storage (`READ_MEDIA_AUDIO`, `READ_EXTERNAL_STORAGE`) to read and play your music files
- Services (`FOREGROUND_SERVICE`, `WAKE_LOCK`) to keep the music playing in the background
- Notifications (`POST_NOTIFICATION`) to indicate ongoing playback and music loading

## Building

Noxio relies on a patched Media3 and taglib, which adds some caveats:

1. `cmake` and `ninja-build` must be installed.
2. The project uses submodules: clone with `git clone --recurse-submodules`, or run
   `git submodule update --init --recursive` afterwards.
3. Building on Windows is not supported (the Media3 build runs unix shell scripts). Use Linux, macOS or WSL.
4. You need JDK 21 and NDK `28.2.13676358` (installable through Android Studio's SDK manager).

```bash
./gradlew assembleDebug   # build
./gradlew installDebug    # install on a connected device or emulator
```

The code still lives under the `org.oxycblt.auxio` package on purpose, so changes from upstream can be merged with
minimal conflicts. Only the application ID and branding differ.

## Credits & License

Noxio is built on [Auxio](https://github.com/OxygenCobalt/Auxio) © Auxio Project, and on its dependencies, including
Media3, taglib and Material Components. Thanks to Alexander Capehart and all Auxio contributors.

Noxio is Free Software, licensed under the
[GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.html) or later, the same as Auxio. See
[LICENSE](/LICENSE).
