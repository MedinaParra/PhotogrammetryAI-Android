# SKM Polea AI — Android offline alpha34

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
- Checkpoints de cancelación durante detección Harris y matching.
- Pose graph con auditoría de ciclos.
- Nube dispersa, ajuste cilíndrico y escala condicionada por referencia.
- Competencia homografía/fundamental, desenfoque, reflejos y repetición calculados por sesión.
- Safety gate fail-closed `READY`, `REVIEW` y `BLOCKED` conectado al flujo runtime.

### Optimización local alpha34

- Máximo 8 cámaras, 120 puntos y 1500 observaciones.
- Cámara global 0 fija en rotación y traslación.
- Primera etapa: puntos y traslaciones con pérdida Huber y priors.
- Segunda etapa: pequeñas rotaciones de cámaras secundarias.
- Paso rotacional máximo predeterminado de 0,35° y límite acumulado de 3°.
- Damping adaptativo y prior rotacional.
- La etapa rotacional se acepta solo cuando reduce costo y RMS, conserva profundidad positiva y respeta el gauge.
- Cuando no existe mejora suficiente, se conserva exactamente el resultado base.
- Evidencia `runtime_rotational_ba.json` dentro de la generación comprometida.

### Estadística de residuos

- Mediana, MAD, P90 e intervalo empírico 2,5–97,5 % en píxeles.
- Etiqueta obligatoria `EMPIRICAL_RESIDUAL_INTERVAL_NOT_METROLOGICAL`.
- Los percentiles describen la muestra de reproyección; no constituyen incertidumbre dimensional, calibración ni trazabilidad.

### Recuperación y evidencia

- Preparación compartida para métricas y ventana BA.
- Deadline monotónico de 180 segundos.
- Cancelación visible con fallback sin optimizar.
- Ejecuciones en directorios `.pending` y `.committed`.
- Puntero activo promovido solo después de completar hashes y manifiestos.
- Rollback conserva la generación válida anterior.
- Una cancelación publica una generación `ABORTED` sin ventana BA parcial.
- El exportador incluye exclusivamente la generación activa comprometida.
- Manifiesto reproducible con versión, APK instalado, dispositivo, sesión, frames y runtime.

Una huella SHA-256 demuestra identidad del contenido. No equivale a firma digital, autoría corporativa, sellado de tiempo ni aprobación de calidad.

### Diagnóstico de dispositivo

- Temperatura de batería mediante Android cuando está disponible.
- Estado térmico abstracto mediante `PowerManager` en API 29+.
- PSS, heap y RAM disponible.
- Historial `REASON_CRASH_NATIVE` en API 30+.
- Conteo opcional de eventos locales `native-events.log`.

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

- Los intrínsecos permanecen fijos y no han sido calibrados físicamente en Samsung A15 u Honor X5C.
- Fundamental, recuperación de pose, triangulación y `BitmapFactory` no tienen checkpoints internos.
- La promoción transaccional no abarca simultáneamente SQLite y filesystem.
- La huella reproducible no está firmada por una identidad corporativa.
- No existe BA global ni Schur complement.
- Los intervalos estadísticos no se propagan a dimensiones físicas.
- El historial nativo es parcial en Android antiguos y no cubre todos los fallos JNI.
- `armeabi-v7a` permanece pendiente.
- Las pruebas sintéticas y una APK compilada no constituyen validación metrológica.

## No validado para metrología industrial

La aplicación es una alpha de ingeniería. No debe utilizarse para liberar dimensionalmente una polea hasta completar campañas físicas de repetibilidad, calibración por dispositivo, comparación contra instrumentos trazables y análisis formal de incertidumbre.

## Compatibilidad

- `compileSdk 35`
- `targetSdk 35`
- `minSdk 24`
- ABI actual: `arm64-v8a`
- Versión: `0.18.0-alpha34`

## Compilar

```bash
gradle -p PulleyKnowledgeMvp :app:assembleDebug
```

APK esperada:

```text
PulleyKnowledgeMvp/app/build/outputs/apk/debug/app-debug.apk
```

## Validación automática

El workflow `.github/workflows/build-pulley-mvp-apk.yml` ejecuta 33 gates Java, verifica el AAR, compila la APK y audita el cierre OCCT. Alpha34 fue validada en GitHub Actions run `#688`.

## Roadmap e historial

- Roadmap: `docs/ROADMAP_ENGINEERING.md`.
- Estado y avance: `docs/iterations/CURRENT.md`.
- Historial: `docs/iterations/history/`.
- Última cerrada: `ITER-016_2026-07-21_bounded-rotational-ba-statistical-residuals.md`.
- Siguiente: `ITER-017 — Intrínsecos focales condicionados y observabilidad`.

Antes de cerrar una iteración:

```bash
python3 PulleyKnowledgeMvp/tools/validate_iteration_history.py
```

Una iteración no se considera cerrada sin resultados, riesgos, deuda, siguiente paso y CI exitoso.