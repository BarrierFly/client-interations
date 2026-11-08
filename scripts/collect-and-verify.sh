#!/usr/bin/env bash
# Collects the finished jars into build/libs and prints a summary of what is inside each one.
#
# Usage: ./scripts/collect-and-verify.sh
#
# This is a convenience wrapper around the Gradle tasks plus a jar content check, so a release can be
# sanity checked with one command.

set -euo pipefail

cd "$(dirname "$0")/.."

./gradlew clean build collectJars

echo
echo "== jars in build/libs =="
ls -la build/libs

echo
echo "== contents summary =="
for jar in build/libs/*.jar; do
    case "$jar" in
        *-sources.jar|*-dev.jar) continue ;;
    esac

    classes=$(unzip -l "$jar" | grep -c '\.class$' || true)
    aw=$(unzip -l "$jar" | grep -c 'accesswidener' || true)
    lang=$(unzip -l "$jar" | grep -c 'assets/.*/lang/' || true)
    meta=$(unzip -l "$jar" | grep -c 'fabric.mod.json' || true)
    nested=$(unzip -l "$jar" | grep -c 'META-INF/jars' || true)

    printf '%-45s classes=%-3s accesswidener=%s lang=%s fabric.mod.json=%s nestedJars=%s\n' \
        "$(basename "$jar")" "$classes" "$aw" "$lang" "$meta" "$nested"
done
