# Estado actual de iteraciones

**Actualizado:** 2026-07-22  
**Rama:** `agent/photogrammetry-validation-alpha20`  
**PR activo:** `#8` — borrador  
**Versión Android:** `0.18.0-alpha40`

## Estado acumulado

- Alpha40 compila para `arm64-v8a` con STEP/OCCT.
- Evidencia dimensional, identificación, safety gate, pose graph y reconstrucción mantienen gates automáticos.
- La ventana BA permanece limitada a 8 cámaras, 120 puntos y 1500 observaciones con cámara global 0 fija.
- El BA base optimiza puntos y traslaciones con Huber y priors.
- Las etapas rotacional y focal permanecen acotadas, observables y con fallback exacto.
- La cámara 0, `cx`, `cy`, distorsión y razón `fx/fy` permanecen fijos.
- La sensibilidad focal se etiqueta `EMPIRICAL_FOCAL_SENSITIVITY_NOT_CALIBRATION`.
- Los residuos rotacionales se etiquetan `EMPIRICAL_RESIDUAL_INTERVAL_NOT_METROLOGICAL`.
- RANSAC fundamental, SVD esencial, cheirality y triangulación DLT poseen checkpoints internos cooperativos.
- Una cancelación o deadline dentro de geometría impide publicar resultados parciales.
- La publicación runtime se registra en SQLite mediante fases `PREPARED`, `FILES_COMMITTED`, `POINTER_PUBLISHED`, `COMPLETE` y `ROLLED_BACK`.
- Una carpeta comprometida puede recuperar su puntero después de una interrupción.
- Una generación incompleta se revierte y no reemplaza a la generación activa anterior.
- Antes de exportar se verifican rutas, tamaños, archivos listados y SHA-256.
- No existe todavía campaña física completada ni calificación industrial.

## Avance global estimado

- **Avance integral: 96 %.**
- **Madurez alpha: 99 %.**
- **Preparación industrial/metrológica: 25 %.**

El incremento corresponde a cancelación geométrica profunda y publicación recuperable. La preparación industrial no aumenta porque Samsung A15, Honor X5C, calibración física e instrumentos trazables no fueron ejecutados.

## Iteración actual o última cerrada

### ITER-019 — Checkpoints geométricos y journal coordinado

Registro: `history/ITER-019_2026-07-22_geometry-cancellation-publication-journal.md`

- checkpoints dentro de RANSAC, scoring, normalización y Jacobi fundamental;
- checkpoints dentro de SVD, candidatos de pose, cheirality y DLT esencial;
- checkpoints dentro de triangulación dispersa;
- journal SQLite independiente para publicación runtime;
- protocolo de fases recuperables;
- respaldo del puntero activo durante promoción;
- recuperación de carpeta `.committed` probada;
- rollback de generación incompleta probado;
- producto run `#754`: `success`;
- 36 gates Java;
- Gradle y cierre OCCT aprobados;
- APK alpha40 publicada;
- uso industrial continúa bloqueado.

## Bloqueos activos

1. `RUNTIME-004`: `Bitmap` decode y algunas llamadas nativas no admiten checkpoint interno;
2. `TX-002`: journal recuperable implementado, pero no existe una transacción ACID única SQLite/filesystem;
3. `INTR-002`: observabilidad focal validada sintéticamente, no con cámaras físicas;
4. `BA-003`: no existe BA global ni Schur complement;
5. `STAT-001`: sensibilidad e intervalos no representan incertidumbre física o dimensional;
6. `SIGN-001`: manifiestos sin firma corporativa ni atestación;
7. `DEVICE-001`: campaña Samsung A15 no ejecutada;
8. `DEVICE-002`: campaña Honor X5C no ejecutada;
9. `VALID-001`: metrología trazable ausente;
10. `DATA-007`: hashes reales de todos los PDF pendientes;
11. `ABI-001`: OCCT solo `arm64-v8a`.

## Siguiente iteración obligatoria

### ITER-020 — Firma local verificable y evidencia automática de ejecución

Firmar el manifiesto canónico de cada generación, verificar payload/firma/clave antes de exportar, declarar si la clave es hardware-backed o software sin atribuir identidad corporativa y persistir evidencia automática para campañas device-alpha.

## Criterios de entrada

- conservar los 36 gates acumulados;
- mantener journal recuperable e integridad SHA-256 fail-closed;
- mantener límites 8/120/1500 y cámara 0 fija;
- no declarar campañas físicas no ejecutadas;
- mantener `corporateIdentity=false` para claves locales;
- mantener PR en borrador.

## Criterios de salida

- firma local de manifiesto implementada;
- payload, firma y clave alterados bloqueados;
- hardware/software backing declarado sin suposiciones;
- evidencia automática de ejecución persistida;
- nueva alpha compilada;
- CI producto e historial exitosos;
- ITER-020 archivada.
