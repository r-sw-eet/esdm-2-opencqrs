#!/usr/bin/env bash
# Smoke gate: regenerate every examples/* app, compile it and run its emitted
# given-when-then tests (in-memory, no database). A scenario-emitter regression
# fails here. The generator's own unit tests run separately with `./gradlew test`.
#
#   scripts/examples.sh            generate + compile + run emitted tests
#   scripts/examples.sh --no-tests generate + compile only
set -euo pipefail
cd "$(dirname "$0")/.."

MVN="${MVN:-mvn}"
GRADLE="${GRADLE:-./gradlew}"
RUN_TESTS=1
[ "${1:-}" = "--no-tests" ] && RUN_TESTS=0

command -v "$MVN" >/dev/null 2>&1 || {
    echo "FAIL: no maven on PATH - set MVN=/path/to/mvn" >&2
    exit 1
}

fail=0
for app in examples/*/; do
    [ -f "${app}esdmgen.yaml" ] || continue
    name="$(basename "$app")"

    if ! "$GRADLE" --quiet run --args="generate $app --skip-lint" >/dev/null 2>&1; then
        echo "FAIL: $name did not generate"
        fail=1
        continue
    fi

    out="${app}generated/opencqrs"
    count="$(find "$out" -type f -not -path "*/target/*" 2>/dev/null | wc -l)"
    if [ "$count" -lt 10 ]; then
        echo "FAIL: $name generated only $count files"
        fail=1
        continue
    fi

    goal="test"
    [ "$RUN_TESTS" -eq 1 ] || goal="test-compile"
    if ! (cd "$out" && "$MVN" -B -q "$goal" >/dev/null 2>&1); then
        echo "FAIL: $name did not $goal"
        fail=1
        continue
    fi

    echo "ok: $name ($count files)"
done

[ "$fail" -eq 0 ] && echo "All examples generated and green." || echo "Some examples failed."
exit "$fail"
