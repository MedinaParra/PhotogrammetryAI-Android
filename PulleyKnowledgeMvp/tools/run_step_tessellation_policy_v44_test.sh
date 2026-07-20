#!/bin/sh
set -eu
R=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
O="$R/build/step-tessellation-policy-v44"
rm -rf "$O"
mkdir -p "$O"
javac -d "$O" \
  "$R/app/src/main/java/cl/skm/pulleyai/StepTessellationPolicyCore.java" \
  "$R/tools/StepTessellationPolicyV44Test.java"
java -cp "$O" StepTessellationPolicyV44Test
