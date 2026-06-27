#!/usr/bin/env bash
# Show FeatureLevel gates, excluding permanent infrastructure.
# If a level is given, show only gates at that level.
#
# Usage:
#   ./scripts/feature-gates.sh          # all FeatureLevel gates
#   ./scripts/feature-gates.sh beta     # only BETA gates
#   ./scripts/feature-gates.sh ga       # only GA gates

set -euo pipefail

# Permanent infrastructure — these are NOT gates to remove when promoting β → GA.
PERMANENT=(
    "ModernFixMixinPlugin\.java.*!= FeatureLevel\.GA"
    "ModernFixEarlyConfig\.java.*return FeatureLevel\.GA"
    "ModernFixEarlyConfig\.java.*FeatureLevel requiredLevel = FeatureLevel\.GA"
)

join() { local IFS='|'; echo "$*"; }

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$REPO_ROOT"

if [ $# -ge 1 ]; then
    LEVEL="${1^^}"
    PATTERN="FeatureLevel\.${LEVEL}"
else
    PATTERN="FeatureLevel\.[A-Z]+"
fi

git grep -rE "$PATTERN" src/ | grep -v -E "$(join "${PERMANENT[@]}")" || true
