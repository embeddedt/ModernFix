#!/bin/bash
git ls-remote --heads origin | awk '{print $2}' | grep -E '^refs/heads/[0-9]+\.' | sed 's:.*/::' | sort -V | grep -E '^[0-9]+\.[0-9]*(\.[0-9]*)?$'
