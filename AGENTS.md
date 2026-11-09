# Working on Noxio (for coding agents)

Noxio is a fork of Auxio (Android music player, Kotlin, Media3). Added features: sleep timer, listening stats (Room), switchable minimal launcher icon. See `README.md`, `ROADMAP.md` and `musikr/ARCHITECTURE.md`.

## Building
- A Windows build is not supported. Use Linux, macOS or WSL with JDK 21, NDK `28.2.13676358`, cmake, ninja and the git submodules (`git submodule update --init --recursive`).
- On Windows, build from a Linux-native clone inside WSL (for example `~/noxio`), not from the Windows checkout: its CRLF line endings break `gradlew` and the Media3 scripts, and `/mnt/d` is slow. Set `core.autocrlf false` in WSL.
- `./gradlew assembleDebug` must stay green. `./gradlew installDebug` installs on a connected device.
- `./gradlew spotlessApply` before every commit; CI checks formatting.

## Tests
- Unit tests live in `app/src/test` (the app) and `musikr/src/test` and run on the JVM (Robolectric where Android classes are needed): `./gradlew coverageReport` runs both and writes one coverage report (`build/reports/jacoco/coverageReport/`).
- Write or extend tests for every change to code that has logic: the stats (CSV, import, ranking, ranges, play counting, backup) and the sleep timer have the most. Fix failures before committing. The user does not write Android code, so tests are how mistakes get caught. Screens and dialogs have no tests: say clearly that they are untested.
- CI (`.github/workflows/ci.yml`) runs `spotlessCheck`, `app:lintDebug` and the tests, and publishes the coverage badge from `dev` to the `badges` branch. The coverage number counts all the code except generated code (see `coverageReport` in the root `build.gradle`), so it only goes up when tests are added.
- `app/lint-baseline.xml` holds the lint problems that came with Auxio. Do not add new ones to it to make lint pass; fix them.

## Releases
- Pushing a tag `v*` runs `.github/workflows/release.yml`, which builds a signed release APK and publishes it as a GitHub Release (a hyphen in the tag, like `v1.0.0-dev.5`, makes it a pre-release). Obtainium follows these.
- Dev builds are signed with a throwaway key in `keystore/dev.jks`. Replace it with a secret-backed key before real releases.
- Nothing is tested on a device by the agent, and screens have no automated tests. Say so instead of claiming a feature works.

## Rules
- The code stays in the `org.oxycblt.auxio` package on purpose, so upstream Auxio changes merge with few conflicts. Only the application ID (`de.devfin.noxio`) and branding differ. Keep Noxio changes small and self-contained (new files such as `stats/`, `ui/LauncherIcon.kt`) rather than rewriting upstream code.
- Noxio only reads tags and audio files. Do not add anything that edits tags or writes to the user's music files (no tag editor, no writing ReplayGain), and do not propose it. Things Noxio works out are kept in its own database or in exported files.
- `musikr/` and the vendored Media3/taglib trees are upstream code; do not edit them unless unavoidable.
- New UI strings go in `values/strings.xml` (English). Do not hand-write the other `values-*` translations; they come from upstream.
- Settings keys and defaults live in `values/settings.xml`, UI in `xml/preferences_*.xml`.
- Keep `README.md` (the "What's different" table) and `ROADMAP.md` up to date with every change that users see, in the same commit as the change. Check both before each commit.
- Describe a UI change before big restructurings and offer options when the direction is open. There are no mockups any more; icon sources are in `docs/icon/`, and screenshots from a device will go into the README.
- Nothing that changes how the app looks is committed before the user has reviewed it. Describe the result (or show a screenshot if there is one) and wait for approval, then commit. Tests, lint and the build can run in the meantime, but the commit and any release tag wait. This also holds for small tweaks and for fixes of a visual bug.
- Branches: `dev` is day-to-day work, `main` is releasable. Do not push to `main`.
