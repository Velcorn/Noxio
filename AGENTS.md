# Working on Noxio (for coding agents)

Noxio is a fork of Auxio (Android music player, Kotlin, Media3). Added features: sleep timer, listening stats (Room), a full backup, tag editing, keyboard phone mode, faster library loading, a switchable minimal launcher icon and more (the list is in `README.md`). See `README.md`, `ROADMAP.md` and `musikr/ARCHITECTURE.md`.

## Building
- A Windows build is not supported. Use Linux, macOS or WSL with JDK 21, NDK `28.2.13676358`, cmake, ninja and the git submodules (`git submodule update --init --recursive`).
- On Windows, build from a Linux-native clone inside WSL (for example `~/noxio`), not from the Windows checkout: its CRLF line endings break `gradlew` and the Media3 scripts, and `/mnt/d` is slow. Set `core.autocrlf false` in WSL.
- `./gradlew assembleDebug` must stay green. `./gradlew installDebug` installs on a connected device.
- `./gradlew spotlessApply` before every commit; CI checks formatting.

## Tests
- Unit tests live in `app/src/test` (the app) and `musikr/src/test` and run on the JVM (Robolectric where Android classes are needed): `./gradlew coverageReport` runs both and writes one coverage report (`build/reports/jacoco/coverageReport/`).
- Write or extend tests for every change to code that has logic: the stats (CSV, import, ranking, ranges, play counting, backup) and the sleep timer have the most. Fix failures before committing. The user does not write Android code, so tests are how mistakes get caught. Screens and dialogs have no tests: say clearly that they are untested.
- CI (`.github/workflows/ci.yml`) runs `spotlessCheck`, `app:lintDebug` and the tests, and publishes the coverage badge from `dev` to the `badges` branch. For now the badge is "Noxio code" (only the testable files listed in `noxio-code.txt`, see `scripts/noxio_coverage.py`); the number for all code (everything except generated code, see `coverageReport` in the root `build.gradle`) is published next to it as `coverage-all.json`, and the badge moves to it once it is respectable. Add every new source file Noxio creates that holds logic to `noxio-code.txt`; screens and dialogs that only wire views together stay out of it, and they have no tests (say so). Keep the Noxio number high: new logic comes with tests.
- `app/lint-baseline.xml` holds the lint problems that came with Auxio. Do not add new ones to it to make lint pass; fix them.

## Releases
- **Before proposing a version, tagging, or editing `CHANGELOG.md`, read `docs/RELEASING.md`.** It has the version-bump rules, changelog layout, tag/signing rules and dev-key rules.
- Nothing is tested on a device by the agent, and screens have no automated tests. Say so instead of claiming a feature works.

## Rules
- The code stays in the `org.oxycblt.auxio` package on purpose, so upstream Auxio changes merge with few conflicts. Only the application ID (`de.devfin.noxio`) and branding differ. Keep Noxio changes small and self-contained (new files such as `stats/`, `ui/LauncherIcon.kt`) rather than rewriting upstream code.
- Noxio writes to the user's music files only when the user asks for it (Edit tags in Song properties), after Android's write permission is granted (`tags/TagEditor`). Never write on its own or in the background without that. Writing goes through `TagWriter` in `musikr/`; tag edits can change a song's UID, so `TagEditMigration` moves what points at songs by UID. Things Noxio works out are otherwise kept in its own database or in exported files.
- `musikr/` and the vendored Media3/taglib trees are upstream code; do not edit them unless unavoidable.
- New UI strings go in `values/strings.xml` (English) and `values-de/strings.xml` (German), in the same commit; when an English text of Auxio changes meaning, fix the German one too. Do not hand-write the other `values-*` translations; they come from upstream and show Noxio's own texts in English.
- Settings keys and defaults live in `values/settings.xml`, UI in `xml/preferences_*.xml`.
- Keep `README.md` (the "What's different" list) and `ROADMAP.md` up to date with every change that users see, in the same commit as the change. Check both before each commit. Keep the README short: one line or two per difference, the details belong in the ROADMAP.
- Keep the UI in line with the rest of the app: reuse the patterns, components and dialogs that are already there (the sort dialog, the bottom row with search, sort and more, the floating shuffle button, popup menus, spacing) and only design something new when it is a completely new kind of UI. Look at how the other screens do the same thing before building it.
- Describe a UI change before big restructurings and offer options when the direction is open. There are no mockups any more; icon sources are in `docs/icon/`, and screenshots from a device will go into the README.
- Nothing that changes how the app looks is committed before the user has reviewed it. Describe the result (or show a screenshot if there is one) and wait for approval, then commit. Tests, lint and the build can run in the meantime, but the commit and any release tag wait. This also holds for small tweaks and for fixes of a visual bug.
- Branches: `dev` is day-to-day work, `main` is releasable. The user allowed pushing to `main` (Nov 2026): after a final release tag, fast-forward `main` to that tag, and only then; day-to-day work stays on `dev`.

## Working style (all agents)
- If a task prompt or plan file exists, follow it step by step; don't widen scope.
- Report back briefly: files changed, test/lint/build result, what is untested. No full file dumps.
- Keep Gradle, lint and test output short: pipe through `tail -n 40` or `grep -E 'FAIL|error|warning:'`; open reports only for the failing part.
- Don't read `musikr/` internals, vendored Media3/taglib trees, `app/lint-baseline.xml` or `CHANGELOG.md` in full; search them with `grep -n`.
