# Alpha60 custom vision models

The Android app intentionally does not bundle generic model weights. The production package must be trained and calibrated with real pulley images.

## Models

1. `pulley_yolo11n_seg_int8.tflite`
   - architecture: YOLO11n-seg
   - input: 512 x 512
   - quantization: full INT8 weights and activations
   - classes in this exact order:
     1. pulley_shell
     2. pulley_end_disc
     3. pulley_shaft
     4. bearing_housing
     5. other_pulley
     6. person
     7. obstruction

2. `capture_quality_mobilenetv3_small_int8.tflite`
   - architecture: MobileNetV3-Small
   - input: 224 x 224
   - quantization: full INT8 weights and activations
   - classes in this exact order:
     1. good_capture
     2. wrong_target
     3. multiple_pulleys
     4. partial_shell
     5. person_obstruction
     6. tool_obstruction
     7. motion_blur
     8. strong_reflection
     9. too_dark
     10. too_far
     11. too_close

## YOLO export

Use a representative calibration set from the same phones, lighting and pulley surfaces used in the field.

```python
from ultralytics import YOLO

model = YOLO("runs/segment/pulley-yolo11n/weights/best.pt")
model.export(
    format="litert",
    imgsz=512,
    quantize=8,
    data="pulley_seg.yaml",
    fraction=1.0,
    device="cpu",
)
```

Do not calibrate INT8 with COCO when the production domain is workshop pulley imagery.

## Package manifest

Create `vision_manifest.json` using `vision_manifest.example.json` as the template, insert the SHA-256 of both `.tflite` files and ZIP only these three files at the archive root.

The Android app rejects:

- unknown files or nested paths;
- mismatched hashes;
- wrong class order;
- non-INT8 declarations;
- incompatible input or output tensor shapes;
- packages larger than the bounded import limits.

## Validation status

Passing the model package and LiteRT tensor gate only proves that the models can be loaded. Accuracy must still be evaluated on a held-out physical dataset and during a Samsung A26 device campaign before the AI may accept or reject production captures automatically.
