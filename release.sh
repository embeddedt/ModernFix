#!/bin/bash
scope="$1"
if [ -z "$scope" ]; then
echo Scope not provided
exit 1
fi
./gradlew pushSemverTag -Psemver.scope=$scope
./gradlew publishToModSites
