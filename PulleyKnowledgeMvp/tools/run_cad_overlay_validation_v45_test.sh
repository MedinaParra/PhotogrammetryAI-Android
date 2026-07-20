#!/bin/sh
set -eu
R=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
O="$R/build/cad-overlay-validation-v45"
rm -rf "$O"
mkdir -p "$O"
javac -d "$O" \
  "$R/app/src/main/java/cl/skm/pulleyai/CadOverlayValidationCore.java" \
  "$R/tools/CadOverlayValidationV45Test.java"
java -cp "$O" CadOverlayValidationV45Test
