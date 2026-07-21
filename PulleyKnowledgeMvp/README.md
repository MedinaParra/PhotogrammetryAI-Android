# SKM Polea AI — Android offline alpha20

Aplicación Android local para capturar evidencia fotográfica de poleas, ejecutar una reconstrucción geométrica experimental y comparar el resultado con componentes STEP sin depender de un servidor.

## Estado comprobado en código

### Captura y trazabilidad

- Camera2 con preview horizontal y captura JPEG.
- Dos anillos guiados de 12 sectores.
- Registro por fotografía de orientación, movimiento, exposición, ISO, enfoque, cámara y orientación JPEG.
- Evaluación local de nitidez, iluminación y estabilidad.
- Sesiones SQLite recuperables.
- SHA-256 por fotografía y exportación ZIP mediante Storage Access Framework.
- Selección balanceada de hasta 48 fotogramas con presupuesto preventivo de memoria y almacenamiento.

### Reconstrucción experimental

- Detector Harris y descriptor binario local de 64 bits.
- Ratio test, correspondencia simétrica y control de distribución espacial.
- Matriz fundamental por ocho puntos normalizados, RANSAC determinista y error de Sampson.
- Matriz esencial calibrada, cuatro soluciones de pose, cheirality y paralaje.
- Triangulación DLT con filtros de profundidad y error de reproyección.
- Tracks de tres o más imágenes.
- Grafo global de poses con propagación por aristas confiables y auditoría de cierres de ciclo.
- Fusión robusta de nube dispersa y ajuste experimental de cilindro.
- Escala por referencia conocida y detección de referencias contradictorias.

### STEP y ensamblaje CAD

- AAR `cadcore-step-v2` con OCCT 7.9.2 para `arm64-v8a`.
- Importación mediante `STEPControl_Reader` a través del puente JNI.
- BRep, teselación, bounding box y normales.
- Transformaciones rígidas de componentes, sin escalado no uniforme.
- Autoprueba del kernel y bloqueo del procesamiento cuando la puerta no está aprobada.
- Comparación cuantitativa STEP–reconstrucción y estados `MATCH`, `REVIEW` y `BLOCKED`.
- Exportación auditable del ensamblaje y conservación del STEP original.

## Clasificación de madurez

### Implementado y cubierto por pruebas sintéticas

La matemática fundamental, pose esencial, triangulación, tracks, cierres de ciclo, ajuste cilíndrico, escala, restricciones CAD y políticas STEP se ejecutan en pruebas Java deterministas dentro de GitHub Actions.

### Compilado, pero todavía no validado suficientemente en teléfono

La captura Camera2, persistencia Android, importación STEP/JNI, teselación nativa, recuperación tras interrupciones y procesamiento prolongado requieren campañas reales en Samsung A15 y Honor X5C. Una compilación exitosa no demuestra estabilidad térmica, ausencia de fallos JNI ni compatibilidad de todos los STEP de taller.

### Implementación todavía simplificada

- El descriptor visual es experimental y mucho más pequeño que ORB/SIFT.
- El grafo global propaga poses y verifica ciclos, pero todavía no ejecuta optimización no lineal de pose graph ni bundle adjustment local/global.
- La IMU y el recorrido por sectores se usan como prior; no deben considerarse verdad geométrica.
- La incertidumbre reportada no constituye una cadena metrológica calibrada.
- El ajuste cilíndrico no reemplaza una medición física ni una inspección dimensional.

### No validado para metrología industrial

La aplicación es una alpha de ingeniería. No debe usarse para liberar dimensionalmente una polea hasta completar ensayos físicos de repetibilidad, comparación contra patrones trazables, análisis de incertidumbre y validación en los teléfonos objetivo.

## Compatibilidad de compilación

- `compileSdk 35`
- `targetSdk 35`
- `minSdk 24`
- ABI actual: `arm64-v8a`
- `armeabi-v7a` permanece pendiente porque el AAR OCCT actual no contiene esa ABI.

## Compilar

```bash
gradle -p PulleyKnowledgeMvp :app:assembleDebug
```

APK esperada:

```text
PulleyKnowledgeMvp/app/build/outputs/apk/debug/app-debug.apk
```

## Validación

El workflow `.github/workflows/build-pulley-mvp-apk.yml` ejecuta las pruebas puras, verifica el hash del AAR, compila la APK y audita el cierre nativo incluido. Los detalles y límites de cada iteración se registran en `TECHNICAL_VALIDATION_0.18.0-alpha20.md`.
