This is the canonical source repository for the ModernFix project. `gradle.properties` contains info on the target MC version, modloader, etc.

## Patterns

- Mixins are autodiscovered with a custom annotation processor; there is no mixin json file that must be edited to register them.
