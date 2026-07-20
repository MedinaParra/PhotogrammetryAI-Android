#!/bin/sh
set -eu
R=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
O="$R/build/step-placement-v42"
rm -rf "$O"
mkdir -p "$O"
javac -d "$O" \
  "$R/app/src/main/java/cl/skm/pulleyai/StepMeshPlacementCore.java" \
  "$R/tools/StepMeshPlacementV42Test.java"
java -cp "$O" StepMeshPlacementV42Test
