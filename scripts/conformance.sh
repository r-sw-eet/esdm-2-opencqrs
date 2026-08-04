#!/usr/bin/env bash
# C4 conformance: this repo's target against the golden answers in ../esdm-extensions/conformance.
# Boots the emitted compose stack with only the api service published, on host port 1814x
# (opencqrs' slice of the family's port budget).
#
#   scripts/conformance.sh todo
#   scripts/conformance.sh todo orders manufacturing
#   scripts/conformance.sh --keep todo        # leave the stack up for inspection
set -euo pipefail
cd "$(dirname "$0")/.."

exec ./gradlew --quiet --console=plain run --args="conformance $*"
