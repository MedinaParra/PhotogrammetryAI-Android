#!/bin/sh
set -eu
R=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
O="$R/build/maturity-v39"
rm -rf "$O"
mkdir -p "$O"
javac -d "$O" "$R/app/src/main/java/cl/skm/pulleyai/CaptureReadiness.java" "$R/app/src/main/java/cl/skm/pulleyai/ReconstructionFrameSelectorCore.java" "$R/app/src/main/java/cl/skm/pulleyai/TrackPointRefinementCore.java" "$R/app/src/main/java/cl/skm/pulleyai/IndustrialAnalysisBudgetCore.java" "$R/tools/ReconstructionMaturityV38V39Test.java"
java -cp "$O" ReconstructionMaturityV38V39Test
