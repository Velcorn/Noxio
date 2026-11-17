# Releasing Noxio (for coding agents)

Moved from `AGENTS.md`; read this before proposing a version, tagging or editing `CHANGELOG.md`.

- Before every release, review all changes since the latest final release and choose the version bump from their impact; do not automatically increment the patch number. State the proposed version and a brief reason before tagging. An explicit version requested by the user takes precedence.
- Use patch releases (`1.4.0` → `1.4.1`) for bug fixes, regressions, maintenance and internal changes without an intentional change to features or defaults.
- Use minor releases (`1.3.0` → `1.4.0`) for new features, removed settings or features, substantial UI/workflow changes, or intentional changes to behavior or defaults. The appearance and library settings simplification is a minor-release example, even though it mostly removes code.
- Use major releases (`1.x` → `2.0.0`) for deliberate compatibility breaks, such as backup formats or integrations that existing users cannot continue using through a safe migration. Choose the highest applicable bump across the whole release and reset lower version components.
- Every release gets its entry at the top of `CHANGELOG.md` (Auxio's layout: `## vX.Y.Z` with What's New, What's Improved, What's Fixed, What's Changed; short, user-visible points only), in the commit that is tagged.
- Published release tags are immutable. If a published version needs correcting, publish a new version with a higher Android version code rather than moving an existing tag.
- Pushing a tag `v*` or a plain version number like `1.0.0` runs `.github/workflows/release.yml`, which builds a signed release APK and publishes it as a GitHub Release (a hyphen in the tag, like `v1.0.0-dev.5`, makes it a pre-release). Obtainium follows these.
- Final releases (tags without a hyphen, like `1.0.0`; final releases use plain tags without the `v`) are signed with the real key from the repository secrets `NOXIO_KEYSTORE_BASE64`, `NOXIO_KEYSTORE_PASSWORD`, `NOXIO_KEY_ALIAS` and `NOXIO_KEY_PASSWORD`; the key file is never committed. Do not tag a final release before those secrets exist.
- Dev builds are signed with a throwaway key in `keystore/dev.jks`. Replace it with a secret-backed key before real releases.
