# Consolidation roadmap

## Canonical role

This repository is the canonical product for Android pulley capture, photogrammetry, dimensional review, local knowledge and CAD-assisted inspection.

Older experiments may be used as reference, but new photogrammetry product work should land here rather than in `PhotoAI`, `FotoIA` or `PhotoGraph-IA`.

## Current product layers

1. **Field capture** — guided horizontal capture, image quality gates, traceable sessions and ZIP export.
2. **Geometry replay** — matching, robust estimation, tracks, bounded triangulation and point-cloud review.
3. **Pulley interpretation** — cylindrical prior, inlier/outlier classification and dimensional confidence states.
4. **CAD integration** — native STEP runtime supplied by `MedinaParra/FreeCAD-Native`.
5. **Industrial knowledge** — material-code and work-order history, dimensional review and confirmed operator experience.
6. **Vision AI** — runtime and model contracts for custom pulley segmentation and scene-quality classification.

## Branch consolidation order

The active line is stacked and must be consolidated without claiming unvalidated capability:

1. `product/single-device-photogrammetry-v1`
2. `agent/photogrammetry-validation-alpha20`
3. `agent/alpha58-canvas-viewer-validation`
4. `agent/alpha59-prior-cylinder-alignment`
5. `agent/alpha60-yolo11n-mobilenet-scene-awareness`

Create one release-candidate branch only after comparing these heads, preserving all gates and documenting intentionally dropped files.

## Definition of the next release candidate

A release candidate is ready only when all of the following are true:

- one canonical Android application ID and signing strategy are selected;
- the complete CI suite is green from a clean checkout;
- the Canvas point-cloud and cylindrical viewers are tested on the target Samsung device;
- background ZIP replay survives rotation, screen lock and application switching;
- STEP import and native library loading pass on physical hardware;
- a real custom model package is either validated or the AI acceptance path remains explicitly disabled;
- at least three known pulley specimens are captured repeatedly;
- dimensional error, repeatability, thermal behaviour and memory usage are recorded;
- no seed cloud, prior-only cylinder or unscaled reconstruction is presented as a metrological result.

## Repository migration policy

- `PhotoAI`: legacy precursor; migrate only unique, tested components.
- `FotoIA`: security quarantine until exposed credentials are rotated and removed from history.
- `PhotoGraph-IA`: do not use for new photogrammetry work while unrelated game branches remain there.
- `FreeCAD-Native`: shared CAD platform; consume versioned AAR releases rather than copying native binaries manually.

## Immediate work queue

1. Compare the stacked branch heads and create a single release-candidate branch.
2. Train and package real pulley models, or keep the AI decision path disabled.
3. Run a repeatability campaign on known workshop parts.
4. Publish a versioned test report with device, build SHA, specimen dimensions and measured errors.
5. Close or label historical PRs once their commits are represented in the release candidate.
