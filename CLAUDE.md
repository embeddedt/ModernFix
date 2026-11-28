This is the canonical source repository for the ModernFix project. `gradle.properties` contains info on the target MC version, modloader, etc.

Releases are not tagged: every commit pushed to a main version branch (e.g. `1.20`) is a release.
The version is `<release_line.txt>.<first-parent commits since release_line.txt last changed>`. Leave `release_line.txt` alone unless a commit adds a non-bugfix change (e.g. a feature) that isn't gated behind a stability level; then bump its minor version, so that commit becomes `.0`.

The only supported loader is Forge on 1.20.1 and NeoForge on newer versions, so option descriptions don't need loader prefixes like "(Forge)".

## Patterns

- `en_us.json` option descriptions are shown to players in the config screen. Write them for players, not developers: say what the option improves and what to try if something breaks, without implementation details.
- Mixins are autodiscovered with a custom annotation processor; there is no mixin json file that must be edited to register them.
