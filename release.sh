#!/bin/bash
echo -n "Currently on: "
git describe
echo -n "New version: "
read newtag
git tag -a $newtag -m "$newtag"
./gradlew publishToModSites
