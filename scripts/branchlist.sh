#!/bin/bash
git ls-remote --heads origin | awk '{print $2}' | grep -E '^refs/heads/1\.' | sed 's:.*/::' | sort -V | grep -E '^1\.[0-9]*(\.[0-9]*)?$'
