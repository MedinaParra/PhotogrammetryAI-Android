# STEP native integration — alpha17

## Build

- App version: `0.16.0-alpha17`
- ABI: `arm64-v8a`
- CAD runtime: OCCT `7.9.2`
- cadcore AAR compile SDK: `35`
- minimum Android SDK: `24`
- workflow run: `29745493656`
- commit: `bd0490d93ff8d2dea1c1b8da7261c9db134670c1`

## Verified APK payload

The product workflow fails unless the APK contains:

- `libfreecad_android_bridge.so`
- `libTKDESTEP.so`
- `libTKXSBase.so`
- `libTKBRep.so`
- `libTKMesh.so`
- `libc++_shared.so`
- `assets/occt/XSTEPResource/`
- `assets/occt/SHMessage/`

## Functions

- STEP/STP document selection and ISO-10303-21 header validation.
- SHA-256 traceability.
- Component classification: support, shaft, shell, locking sleeve, bearing, hub, coupling or other.
- Native `STEPControl_Reader` import.
- BRep tessellation with `BRepMesh_IncrementalMesh`.
- Mesh cache and overlay with the photogrammetric shell.
- Rigid translation/rotation only; no scale deformation.
- Mechanical constraints, auto-alignment and ZIP export with original STEP and cached mesh.

## Checksums

- APK SHA-256: `bf3d4d930e34779396fd834cfc5e28fc8b06edce350c21dc3db99b0669dfc558`
- cadcore AAR SHA-256: `c8de1869b1a777a89f3d924130c4567bfe16ba218504ca3d83a3467bbd3bd795`

## Remaining qualification

The APK has been statically built and inspected, but still requires installation and prolonged testing on a physical ARM64 Android device with representative industrial STEP files before workshop production use.
