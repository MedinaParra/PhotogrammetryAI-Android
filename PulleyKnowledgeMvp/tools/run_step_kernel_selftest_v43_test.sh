#!/bin/sh
set -eu
R=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
O="$R/build/step-kernel-selftest-v43"
rm -rf "$O"
mkdir -p "$O"
javac -d "$O" \
  "$R/app/src/main/java/cl/skm/pulleyai/StepSelfTestModel.java" \
  "$R/tools/StepSelfTestModelV43Test.java"
java -cp "$O" StepSelfTestModelV43Test
