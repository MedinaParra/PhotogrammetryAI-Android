# SKM Polea AI — Android offline alpha24

Aplicación Android local para capturar evidencia fotográfica de poleas, ejecutar reconstrucción geométrica experimental, comparar cotas revisionadas y trabajar con componentes STEP sin depender de servidores.

## Estado comprobado en código

### Captura y trazabilidad

- Camera2 con preview horizontal y captura JPEG.
- Dos anillos guiados de 12 sectores.
- Registro de orientación, movimiento, exposición, enfoque, cámara y SHA-256 por fotografía.
- Evaluación local de nitidez, iluminación y estabilidad.
- Sesiones SQLite recuperables y exportación ZIP.
- Selección balanceada de hasta 48 fotogramas con presupuesto de memoria y almacenamiento.

### Conocimiento dimensional revisionado

- Modelo `MaterialFamily -> WorkOrder -> Drawing -> DrawingRevision -> DimensionEvidence`.
- Evidencia separada por OT, plano, revisión, dimensión y superficie.
- Radios `BARE_SHELL` y `OUTER_LAGGING` diferenciados.
- Conservación de unidad original y valor normalizado en milímetros.
- Puerta dura de radio:

```text
error_radio_mm = abs(radio_observado - radio_referencia)
MATCH solo cuando error_radio_mm <= 30 y no existe contradicción crítica
```

- Identificación multivariable con residuos `PASS`, `WARNING`, `CONTRADICTION` y `MISSING_REFERENCE`.
- Auditoría local append-only con puntuación, fuentes y huella reproducible.
- Familias documentales conflictivas permanecen en `BLOCKED`.

### Reconstrucción experimental

- Detector Harris y descriptor binario local.
- Ratio test, correspondencia simétrica, cobertura y coherencia espacial.
- Fundamental, esencial, pose, triangulación DLT y tracks multivista.
- Grafo de poses con propagación y auditoría de ciclos.
- Nube dispersa, ajuste cilíndrico y escala por referencia conocida.
- Safety gate fail-closed `READY`, `REVIEW` y `BLOCKED` para cobertura, paralaje, planaridad, grafo, ciclos, reproyección y degradación.
- Métricas ausentes producen `REVIEW`, no aprobación silenciosa.

### Optimización alpha24

- Bundle adjustment **local acotado**, no global.
- Máximo de 8 cámaras, 120 puntos y 1500 observaciones.
- Cámara 0 fija para eliminar el gauge.
- Refinamiento de puntos y traslaciones con pérdida Huber y priors.
- Admisión únicamente cuando el safety gate está en `READY`.
- RMS, mediana, P90, profundidad positiva e iteraciones aceptadas.
- Perfiles Brown-Conrady versionados para intrínsecos y distorsión.
- Los perfiles no validados no pueden utilizarse automáticamente.

### STEP y ensamblaje CAD

- AAR `cadcore-step-v2` con OCCT 7.9.2 para `arm64-v8a`.
- Importación mediante `STEPControl_Reader` por puente JNI.
- BRep, teselación, bounding box y normales.
- Transformaciones rígidas sin escalado no uniforme.
- Autoprueba del kernel y gates de empaquetado.
- Comparación cuantitativa STEP–reconstrucción.
- Exportación sin destruir el STEP original.

## Límites actuales

- El safety gate y el BA local todavía no gobiernan el pipeline runtime completo.
- No se optimizan rotaciones ni existe bundle adjustment global.
- No hay calibración física de Samsung A15 ni Honor X5C.
- Homografía, reflejos y ambigüedad repetitiva no se calculan todavía automáticamente por sesión.
- La interfaz de conocimiento heredada sigue disponible durante la migración.
- `armeabi-v7a` permanece pendiente.
- Las pruebas sintéticas y una APK compilada no constituyen validación metrológica.

## No validado para metrología industrial

La aplicación es una alpha de ingeniería. No debe utilizarse para liberar dimensionalmente una polea hasta completar campañas físicas de repetibilidad, calibración por dispositivo, comparación contra instrumentos trazables y análisis de incertidumbre.

## Compatibilidad

- `compileSdk 35`
- `targetSdk 35`
- `minSdk 24`
- ABI actual: `arm64-v8a`
- Versión: `0.18.0-alpha24`

## Compilar

```bash
gradle -p PulleyKnowledgeMvp :app:assembleDebug
```

APK esperada:

```text
PulleyKnowledgeMvp/app/build/outputs/apk/debug/app-debug.apk
```

## Validación automática

El workflow `.github/workflows/build-pulley-mvp-apk.yml` ejecuta los gates Java, verifica el AAR, compila la APK y audita el cierre OCCT. Alpha24 incorpora 23 gates previos a la publicación del artefacto.

## Roadmap e historial

- Roadmap: `docs/ROADMAP_ENGINEERING.md`.
- Estado y avance: `docs/iterations/CURRENT.md`.
- Historial: `docs/iterations/history/`.
- Última cerrada: `ITER-006_2026-07-21_local-bundle-adjustment-camera-profiles.md`.
- Siguiente: `ITER-007 — Integración runtime del safety gate y BA local`.

Antes de cerrar una iteración:

```bash
python3 PulleyKnowledgeMvp/tools/validate_iteration_history.py
```

GitHub Actions también aplica esta validación. Una iteración no se considera cerrada si no conserva resultados, riesgos, deuda y la siguiente iteración obligatoria.
