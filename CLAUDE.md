@AGENTS.md

# Claude only (noxio)

General delegation and token rules are in the global `~/.claude/CLAUDE.md`. Noxio specifics:

- **Codex:** hand it Kotlin implementation and the matching unit tests from a written plan (name the files, the tests to add, `noxio-code.txt` entries, "do not commit"). I review the diff, run `./gradlew coverageReport spotlessApply` myself (output via `tail`), and check README/ROADMAP updates.
- **Local LLM:** first drafts of `values-de/strings.xml` texts (I check them), CHANGELOG entries in Auxio's layout, commit messages, and summaries of failing Gradle/lint output.
- **Keep for myself:** UI decisions (they need the user's approval before commit), tag-writing code (`TagEditor`/`TagWriter`/`TagEditMigration`), release version choice, and anything touching upstream code.
