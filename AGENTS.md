# Working on Noxio (for coding agents)

Noxio is a fork of Auxio (Android music player, Kotlin, Media3). Added features: sleep timer, listening stats (Room), switchable minimal launcher icon. See `README.md`, `ROADMAP.md` and `musikr/ARCHITECTURE.md`.

## Commands (repo root)
- Building on Windows is not supported. Use Linux, macOS or WSL (`wsl -d archlinux --cd /mnt/d/Programming/Noxio -- <cmd>`). Needs JDK 21, NDK `28.2.13676358`, cmake and ninja, and the git submodules (`git submodule update --init --recursive`).
- `./gradlew assembleDebug` must stay green. `./gradlew installDebug` installs on a connected device.
- `./gradlew spotlessApply` before every commit; CI (`.github/workflows/lint.yml`) checks formatting.
- There are no unit tests in `app`. Nothing added so far has been tested on a device, so say so instead of claiming a feature works.

## Rules
- The code stays in the `org.oxycblt.auxio` package on purpose, so upstream Auxio changes merge with few conflicts. Only the application ID (`de.devfin.noxio`) and branding differ. Keep Noxio changes small and self-contained (new files such as `stats/`, `ui/LauncherIcon.kt`) rather than rewriting upstream code.
- `musikr/` and the vendored Media3/taglib trees are upstream code; do not edit them unless unavoidable.
- New UI strings go in `values/strings.xml` (English). Do not hand-write the other `values-*` translations; they come from upstream.
- Settings keys and defaults live in `values/settings.xml`, UI in `xml/preferences_*.xml`.
- Keep `README.md` (the "What's different" table) and `ROADMAP.md` in sync when a feature changes or is finished.
- Mockups for features live in `docs/mockups/*.html` (single files, same behavior as the app); icon sources in `docs/icon/`. Show UI options as mockups or screenshots before implementing them.
- Line endings: LF everywhere; only Windows scripts (`.bat`, `.cmd`, `.ps1`) use CRLF. Do not convert files with tools that add CRLF.
- Branches: `dev` is day-to-day work, `main` is releasable. Do not push to `main`.
