ModernFix is a standard Minecraft-style Gradle project powered by Architectury Loom. To build the mod for all platforms,
run the `build` task (e.g. via `./gradlew build`). You can also use `./gradlew forge:build` or `./gradlew fabric:build`
to build for just one loader (e.g. when debugging and wanting to rebuild quickly).

You must use Java 21 to develop ModernFix as the toolchain requires it. Nonetheless, the built 1.20.1 JAR is still
compatible with Java 17.

## Submitting pull requests

Code or documentation contributions are welcome. Please keep the following points in mind:

* This project supports many Minecraft versions. Ideally, contributions should be made to the oldest relevant MC version (currently 1.20)
  and then they will be ported forward.

  This somewhat unconventional policy ensures that all supported versions are treated equal when it comes to development,
rather than the onus being on other modders and players to backport changes that are needed. Changes to older versions are
quickly ported up to the latest one as part of the regular development cycle. You are still welcome to open PRs against
a newer branch if desired - but the change will likely be applied manually and not merged as a regular PR.

* Please ensure your code is reasonably neat and sufficiently documented. Remember that self-documenting code is always
better.
