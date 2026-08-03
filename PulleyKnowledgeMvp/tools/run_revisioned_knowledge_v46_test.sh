#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/skm-revisioned-v46"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/RevisionedPulleyKnowledgeCore.java" \
  "$ROOT/tools/RevisionedPulleyKnowledgeV46Test.java"
java -cp "$OUT" RevisionedPulleyKnowledgeV46Test
