#!/bin/sh
set -eu
R=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
O="$R/build/cad-v40"
rm -rf "$O"
mkdir -p "$O"
javac -d "$O" \
  "$R/app/src/main/java/cl/skm/pulleyai/FreeCadNativeBridge.java" \
  "$R/tools/CadAssemblyV40Test.java"
java -cp "$O" CadAssemblyV40Test
