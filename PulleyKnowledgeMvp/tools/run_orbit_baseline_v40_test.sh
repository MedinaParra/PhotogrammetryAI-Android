#!/bin/sh
set -eu
R=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
O="$R/build/orbit-baseline-v40"
rm -rf "$O"
mkdir -p "$O"
javac -d "$O" "$R/app/src/main/java/cl/skm/pulleyai/OrbitBaselinePriorCore.java" "$R/tools/OrbitBaselinePriorV40Test.java"
java -cp "$O" OrbitBaselinePriorV40Test
