This is the canonical source repository for the ModernFix project. `gradle.properties` contains info on the target MC version, modloader, etc.

Releases are not tagged: every commit pushed to a main version branch (e.g. `1.20`) is a release.

## Patterns

- Mixins are autodiscovered with a custom annotation processor; there is no mixin json file that must be edited to register them.
