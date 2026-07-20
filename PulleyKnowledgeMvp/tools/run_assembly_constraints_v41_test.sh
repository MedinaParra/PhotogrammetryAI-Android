#!/bin/sh
set -eu
R=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
O="$R/build/assembly-v41"
rm -rf "$O"
mkdir -p "$O"
javac -d "$O" \
  "$R/app/src/main/java/cl/skm/pulleyai/AssemblyConstraintCore.java" \
  "$R/tools/AssemblyConstraintsV41Test.java"
java -cp "$O" AssemblyConstraintsV41Test
