# SKM Polea AI — Android offline alpha32

Aplicación Android local para capturar evidencia fotográfica de poleas, ejecutar reconstrucción geométrica experimental, comparar cotas revisionadas y trabajar con componentes STEP sin depender de servidores.

## Estado comprobado en código

### Captura y trazabilidad

- Camera2 con preview horizontal y captura JPEG.
- Dos anillos guiados de 12 sectores.
- Orientación, movimiento, exposición, enfoque, cámara y SHA-256 por fotografía.
- Calidad local de nitidez, iluminación y estabilidad.
- Sesiones SQLite recuperables y exportación ZIP.
- Selección balanceada de hasta 48 fotogramas con presupuesto de memoria y almacenamiento.

### Conocimiento dimensional revisionado

- Modelo `MaterialFamily -> WorkOrder -> Drawing -> DrawingRevision -> DimensionEvidence`.
- Evidencia separada por OT, plano, revisión, dimensión y superficie.
- Radios `BARE_SHELL` y `OUTER_LAGGING` diferenciados.
- Unidad original y valor normalizado en milímetros.
- Puerta dura de radio:

```text
error_radio_mm = abs(radio_observado - radio_referencia)
MATCH solo cuando error_radio_mm <= 30 y no existe contradicción crítica
```

- Identificación multivariable con `PASS`, `WARNING`, `CONTRADICTION` y `MISSING_REFERENCE`.
- Auditoría append-only con puntuación, fuentes y huella reproducible.
- Familias documentales conflictivas permanecen en `BLOCKED`.

### Reconstrucción experimental

- Harris y descriptor binario local.
- Ratio test, correspondencia simétrica, cobertura y coherencia espacial.
- Fundamental, esencial, pose, triangulación DLT y tracks multivista.
- Pose graph con auditoría de ciclos.
- Nube dispersa, ajuste cilíndrico y escala condicionada por referencia.
- Competencia homografía/fundamental, desenfoque, reflejos y repetición calculados por sesión.
- Safety gate fail-closed `READY`, `REVIEW` y `BLOCKED` conectado al flujo runtime.
- Métricas ausentes nunca se reemplazan por valores favorables.

### Optimización runtime alpha32

- Bundle adjustment **local acotado**, no global.
- Máximo 8 cámaras, 120 puntos y 1500 observaciones.
- Cámara global 0 fija.
- Refinamiento de puntos y traslaciones con pérdida Huber y priors.
- BA ejecutado únicamente cuando safety gate, ventana y recursos están en `READY`.
- RMS, mediana, P90, profundidad positiva e iteraciones aceptadas.
- Una preparación compartida alimenta métricas suplementarias y ventana BA.
- Deadline monotónico de 180 segundos.
- Cancelación visible con fallback obligatorio sin optimizar.
- Caché, métricas, ventana, telemetría, diagnóstico, aborto y auditoría exportables.

### Diagnóstico de dispositivo

- Temperatura de batería mediante Android cuando está disponible.
- Estado térmico abstracto mediante `PowerManager` en API 29+.
- PSS, heap y RAM disponible.
- Historial `REASON_CRASH_NATIVE` en API 30+.
- Conteo opcional de eventos locales `native-events.log`.
- Campaña física `READY/REVIEW/BLOCKED` consume diagnóstico automático.

Estos valores son diagnósticos operacionales. No equivalen a temperatura de CPU/GPU, termografía ni metrología trazable.

### STEP y ensamblaje CAD

- AAR `cadcore-step-v2` con OCCT 7.9.2 para `arm64-v8a`.
- Importación mediante `STEPControl_Reader` por JNI.
- BRep, teselación, bounding box y normales.
- Transformaciones rígidas sin escalado no uniforme.
- Autoprueba del kernel y gates de empaquetado.
- Comparación cuantitativa STEP–reconstrucción.
- Exportación sin destruir el STEP original.

## Límites actuales

- `SessionOverlapAnalyzer` aún no consulta cancelación dentro de sus bucles internos.
- Los artefactos parciales todavía no se promueven mediante transacción atómica.
- No se optimizan rotaciones ni intrínsecos y no existe BA global.
- No hay calibración física ni campaña ejecutada en Samsung A15 u Honor X5C.
- El historial nativo es parcial en Android antiguos y no cubre todos los fallos JNI.
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
- Versión: `0.18.0-alpha32`

## Compilar

```bash
gradle -p PulleyKnowledgeMvp :app:assembleDebug
```

APK esperada:

```text
PulleyKnowledgeMvp/app/build/outputs/apk/debug/app-debug.apk
```

## Validación automática

El workflow `.github/workflows/build-pulley-mvp-apk.yml` ejecuta 31 gates Java, verifica el AAR, compila la APK y audita el cierre OCCT. Alpha32 fue validada en GitHub Actions run `#643`.

## Roadmap e historial

- Roadmap: `docs/ROADMAP_ENGINEERING.md`.
- Estado y avance: `docs/iterations/CURRENT.md`.
- Historial: `docs/iterations/history/`.
- Última cerrada: `ITER-014_2026-07-21_runtime-cache-cancellation-device-diagnostics.md`.
- Siguiente: `ITER-015 — Checkpoints profundos, recuperación transaccional y campaña reproducible`.

Antes de cerrar una iteración:

```bash
python3 PulleyKnowledgeMvp/tools/validate_iteration_history.py
```

Una iteración no se considera cerrada sin resultados, riesgos, deuda, siguiente paso y CI exitoso.