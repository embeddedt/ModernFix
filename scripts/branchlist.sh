#!/bin/bash
git ls-remote --heads origin | awk '{print $2}' | sed 's:.*/::' | sort -V | grep -E '^1\.[0-9]*(\.[0-9]*)?$'
