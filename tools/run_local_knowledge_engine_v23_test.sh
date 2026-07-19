#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$ROOT_DIR/.build/local-knowledge-v23"
SRC="$ROOT_DIR/PhotogrammetryAI/app/src/main/java/cl/ingenieria/photogrammetryai/core/materialhistory"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

javac -encoding UTF-8 -d "$OUT_DIR" \
  "$SRC/MaterialKnowledgeCatalog.java" \
  "$SRC/MaterialKnowledgeStore.java" \
  "$SRC/MaterialPulleyKnowledgeBase.java" \
  "$SRC/IdentificationAudit.java" \
  "$SRC/MaterialFamilyMerge.java" \
  "$SRC/InMemoryMaterialKnowledgeStore.java" \
  "$SRC/MaterialCodeInput.java" \
  "$SRC/PulleyIdentificationRequest.java" \
  "$SRC/QualityReportTextParser.java" \
  "$SRC/QualityKnowledgeIngestionService.java" \
  "$SRC/DriveQualityKnowledgeSeed.java" \
  "$SRC/PulleyMaterialIdentificationEngine.java" \
  "$SRC/LocalPulleyKnowledgeEngine.java" \
  "$SRC/sqlite/SqlitePulleyKnowledgeSchema.java" \
  "$SRC/sqlite/TextListCodec.java" \
  "$ROOT_DIR/tools/LocalKnowledgeEngineV23Test.java"

java -cp "$OUT_DIR" LocalKnowledgeEngineV23Test
