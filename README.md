<h1 align="center"><b>Noxio</b></h1>
<h4 align="center">A simple, rational music player for Android, built for listening through the night.</h4>
<p align="center">
    <img src="https://img.shields.io/badge/license-GPL%20v3-2B6DBE.svg?style=flat" alt="GPL v3">
    <img alt="Minimum SDK Version" src="https://img.shields.io/badge/API-24%2B-1450A8?style=flat">
</p>

## About

Noxio is a fork of [Auxio](https://github.com/OxygenCobalt/Auxio) by Alexander Capehart. Auxio is a great local music
player with a fast, reliable UI, and Noxio keeps that foundation. The name merges **Auxio** with **Nocturne**: the fork
started because a music player that is good to fall asleep to needs a sleep timer.

Noxio is a personal project and will keep diverging from upstream. It is **not** affiliated with the Auxio project, and
upstream issues should not be reported against it.

## What's different

Planned and in progress, tracked in [ROADMAP.md](/ROADMAP.md):

- [ ] Sleep timer, reachable from the now playing screen
- [ ] Listening stats: weekly, monthly and yearly top songs, artists and albums, a barebones "Wrapped"
- [ ] Assorted smaller tweaks

Everything else is inherited from Auxio: Media3 playback, gapless playback, ReplayGain, rich tag support (multiple
artists, release types, disc numbers, sort tags), playlists, playback persistence, Android Auto, widgets, and being
completely private and offline.

Noxio installs alongside Auxio (`de.devfin.noxio`), so you can try both.

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
